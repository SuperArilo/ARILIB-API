package com.tty.api.persistence.mybatis;

import com.tty.api.persistence.EntityStore;
import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.query.Patch;
import com.tty.api.persistence.query.Query;
import com.tty.api.persistence.result.ChangeSet;
import com.tty.api.persistence.result.EntityChange;
import com.tty.api.dto.PageResult;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.*;
import com.tty.api.enumType.SQLType;

import org.apache.ibatis.session.*;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class MyBatisEntityStore<T, ID> implements EntityStore<T, ID> {

    private final EntityModel<T, ID> model;
    private final Supplier<SqlSessionFactory> factory;
    private final Class<? extends BaseMapper<T>> mapperClass;
    private final Executor executor;
    private final SQLType sqlType;
    private final QueryCompiler<T> compiler;
    private final Map<String, String> columns;
    private final Object lifecycle = new Object();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private boolean closing;
    private int active;

    public MyBatisEntityStore(EntityModel<T, ID> model, Supplier<SqlSessionFactory> factory, Class<? extends BaseMapper<T>> mapperClass, Executor executor, SQLType sqlType) {

        this.model = Objects.requireNonNull(model);
        this.factory = Objects.requireNonNull(factory);
        this.mapperClass = Objects.requireNonNull(mapperClass);
        this.executor = Objects.requireNonNull(executor);
        this.sqlType = Objects.requireNonNull(sqlType);
        var table = TableInfoHelper.getTableInfo(model.entityType());
        if (table == null)
            throw new IllegalArgumentException("Register the entity Mapper in MyBatis before creating its Store");
        if (!model.idProperty().equals(table.getKeyProperty()))
            throw new IllegalArgumentException("Model identity must match MyBatis primary key");
        if (table.isWithLogicDelete())
            throw new IllegalArgumentException("Logical-delete entities need a specialized Store");

        var mapping = new LinkedHashMap<String, String>();
        mapping.put(table.getKeyProperty(), table.getKeyColumn());
        for (var field : table.getFieldList()) {
            if (!field.isSelect() || field.getTypeHandler() != null)
                throw new IllegalArgumentException("Hidden/custom-converted fields need a specialized Store: " + field.getProperty());

            mapping.put(field.getProperty(), field.getColumn());
        }
        for (String property : model.values(newEntity()).keySet())
            if (!mapping.containsKey(property))
                throw new IllegalArgumentException("Snapshot field is not persisted: " + property);

        this.columns = Map.copyOf(mapping);
        this.compiler = new QueryCompiler<>(model, this.columns, sqlType);
        this.columns.keySet().forEach(this.compiler::column);
    }

    private T newEntity() {
        try {
            var constructor = this.model.entityType().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public EntityModel<T, ID> model() {
        return this.model;
    }

    private BaseMapper<T> mapper(SqlSession session) {
        return session.getMapper(this.mapperClass);
    }

    private List<T> selectRows(SqlSession session, QueryWrapper<T> wrapper) {
        List<String> selected = this.columns.keySet().stream().map(property -> this.compiler.column(property) + " AS " + SqlSyntax.column(this.sqlType, property)).toList();
        wrapper.select(selected);
        return mapper(session).selectMaps(wrapper).stream().map(this.model::fromDatabaseValues).toList();
    }

    private QueryWrapper<T> wrapper(Query<T> query, boolean order) {
        var sql = this.compiler.compile(query);
        var wrapper = new QueryWrapper<T>();
        wrapper.setEntityClass(this.model.entityType());
        wrapper.apply(sql.expression(), sql.parameters().toArray());
        if (order) {
            boolean includesId = false;
            for (var item : query.orders()) {
                String expression = this.compiler.orderColumn(item.property());
                if (item.ascending()) wrapper.orderByAsc(expression);
                else wrapper.orderByDesc(expression);
                includesId |= item.property().equals(this.model.idProperty());
            }
            if (!includesId) wrapper.orderByAsc(this.compiler.orderColumn(this.model.idProperty()));
        }
        return wrapper;
    }

    @Override
    public CompletableFuture<Optional<T>> findOne(Query<T> query) {
        return task(session -> {
            var rows = selectRows(session, wrapper(query, false).last("LIMIT 2"));
            if (rows.size() > 1)
                throw new IllegalStateException("Expected at most one entity");
            return rows.stream().findFirst().map(this.model::copy);
        });
    }

    @Override
    public CompletableFuture<List<T>> findAll(Query<T> query) {
        return task(session -> selectRows(session, wrapper(query, true)));
    }

    @Override
    public CompletableFuture<PageResult<T>> findPage(Query<T> query, int page, int size) {
        if (page < 1 || size < 1)
            throw new IllegalArgumentException("Page and size must be positive");
        long offset = Math.multiplyExact((long) page - 1, size);
        return task(session -> {
            long total = mapper(session).selectCount(wrapper(query, false));
            var rows = this.selectRows(session, wrapper(query, true).last("LIMIT " + size + " OFFSET " + offset));
            long totalPages = total / size + (total % size == 0 ? 0 : 1);
            return PageResult.build(rows.stream().map(this.model::copy).toList(), total, totalPages, page);
        });
    }

    @Override
    public CompletableFuture<Long> count(Query<T> query) {
        return this.task(session -> this.mapper(session).selectCount(this.wrapper(query, false)));
    }

    @Override
    public CompletableFuture<ChangeSet<T>> insert(T entity) {
        T input = this.model.copy(entity);
        return task(session -> {
            if (mapper(session).insert(input) != 1)
                throw new IllegalStateException("Insert did not affect one row");
            T stored = this.readBack(session, model.id(input));
            return new ChangeSet<>(1, List.of(new EntityChange<>(null, stored)));
        });
    }

    @Override
    public CompletableFuture<ChangeSet<T>> save(T entity) {
        T input = this.model.copy(entity);
        ID id = Objects.requireNonNull(this.model.id(input), "save requires primary key");
        return this.update(this.model.byId(id), Patch.replacing(input, this.model));
    }

    @Override
    public CompletableFuture<ChangeSet<T>> update(Query<T> query, Patch<T> patch) {
        this.model.validatePatch(patch);
        for (String property : patch.values().keySet()) compiler.column(property);
        if (patch.values().size() > 700)
            throw new IllegalArgumentException("Patch exceeds portable parameter budget");

        return task(session -> {
            if (patch.values().isEmpty()) return ChangeSet.empty();
            var before = lockedRows(session, query);
            if (before.isEmpty()) return ChangeSet.empty();
            var changes = new ArrayList<EntityChange<T>>();
            for (var batch : this.batches(before)) {
                var update = new UpdateWrapper<T>();
                update.setEntityClass(this.model.entityType());
                update.in(this.compiler.column(this.model.idProperty()), batch.stream().map(this.model::id).toList());
                patch.values().forEach((property, value) -> update.set(compiler.column(property), value));

                int processed = mapper(session).update(null, update);
                if (this.sqlType == SQLType.SQLITE && processed != batch.size())
                    throw new IllegalStateException("Update skipped one or more selected rows");

                var after = new HashMap<ID, T>();
                var readback = new QueryWrapper<T>();
                readback.setEntityClass(this.model.entityType());
                readback.in(this.compiler.column(this.model.idProperty()), batch.stream().map(this.model::id).toList());

                for (T stored : this.selectRows(session, readback))
                    after.put(this.model.id(stored), this.model.copy(stored));
                for (T old : batch) {
                    T stored = after.get(this.model.id(old));
                    if (stored == null)
                        throw new IllegalStateException("Written entity missing during readback");

                    changes.add(new EntityChange<>(this.model.copy(old), stored));
                }
            }

            return new ChangeSet<>(changes.size(), changes);
        });
    }

    @Override
    public CompletableFuture<ChangeSet<T>> delete(Query<T> query) {
        return task(session -> {
            var before = lockedRows(session, query);
            if (before.isEmpty()) return ChangeSet.empty();
            for (var batch : this.batches(before)) {
                var delete = new QueryWrapper<T>();
                delete.setEntityClass(this.model.entityType());
                delete.in(this.compiler.column(this.model.idProperty()), batch.stream().map(this.model::id).toList());
                if (mapper(session).delete(delete) != batch.size())
                    throw new IllegalStateException("Delete changed an unexpected row count");
            }
            return new ChangeSet<>(before.size(), before.stream().map(e -> new EntityChange<>(model.copy(e), null)).toList());
        });
    }

    private List<T> lockedRows(SqlSession session, Query<T> query) {
        var wrapper = wrapper(query, true);
        if (sqlType == SQLType.MYSQL) wrapper.last("FOR UPDATE");
        return selectRows(session, wrapper);
    }

    private T readBack(SqlSession session, ID id) {
        if (!(id instanceof Serializable))
            throw new IllegalArgumentException("Primary key must be non-null and serializable");
        var rows = selectRows(session, wrapper(this.model.byId(id), false));
        if (rows.size() != 1)
            throw new IllegalStateException("Written entity missing during readback");
        return rows.getFirst();
    }

    private List<List<T>> batches(List<T> rows) {
        var result = new ArrayList<List<T>>();
        for (int start = 0; start < rows.size(); start += 200)
            result.add(rows.subList(start, Math.min(rows.size(), start + 200)));
        return result;
    }

    private <R> CompletableFuture<R> task(Function<SqlSession, R> action) {
        synchronized (this.lifecycle) {
            if (this.closing)
                return CompletableFuture.failedFuture(new IllegalStateException("Store is closing"));

            this.active++;
        }
        var result = new CompletableFuture<R>();
        try {
            this.executor.execute(() -> {
                SqlSession session = null;
                R value = null;
                Throwable failure = null;

                try {
                    session = Objects.requireNonNull(this.factory.get(), "session factory").openSession(false);
                    value = action.apply(session);
                    session.commit();
                } catch (Throwable ex) {
                    failure = ex;
                    if (session != null)
                        try {
                            session.rollback();
                        } catch (Throwable rollback) {
                            ex.addSuppressed(rollback);
                        }
                } finally {
                    if (session != null)
                        try {
                            session.close();
                        } catch (Throwable cleanup) {
                            if (failure != null) failure.addSuppressed(cleanup);
                            else Bukkit.getLogger().warning("Session close failed after commit");
                        }
                }
                this.finished();
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        } catch (Throwable rejection) {
            finished();
            result.completeExceptionally(rejection);
        }
        return result;
    }

    private void finished() {
        boolean done;
        synchronized (this.lifecycle) {
            this.active--;
            done = this.closing && this.active == 0;
        }
        if (done) this.closed.complete(null);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        boolean done;
        synchronized (this.lifecycle) {
            this.closing = true;
            done = this.active == 0;
        }
        if (done) this.closed.complete(null);
        return this.closed.thenApply(Function.identity());
    }

}
