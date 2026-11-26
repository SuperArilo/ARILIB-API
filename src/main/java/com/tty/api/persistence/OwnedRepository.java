package com.tty.api.persistence;

import com.tty.api.persistence.model.BeanAccess;
import com.tty.api.persistence.query.Patch;
import com.tty.api.persistence.query.Query;
import com.tty.api.dto.PageResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class OwnedRepository<T, ID> {

    private final CachedRepository<T, ID> repository;
    private final Object owner;

    OwnedRepository(CachedRepository<T, ID> repository, Object owner) {
        this.repository = repository;
        this.owner = owner;
    }

    public Query<T> query() {
        return Query.of(this.repository.model().entityType()).owner(this.repository.model().ownerProperty(), this.owner);
    }

    private Query<T> scoped(Query<T> query) {
        var owners = query.ownerValues(this.repository.model().ownerProperty());
        if (owners != null && !owners.contains(this.owner))
            throw new IllegalArgumentException("Query contradicts this repository's bound owner");
        return query.and(this.query());
    }

    public CompletableFuture<List<T>> findAll() {
        return findAll(Query.of(this.repository.model().entityType()));
    }

    public CompletableFuture<List<T>> findAll(Query<T> query) {
        return this.repository.findAll(this.scoped(query));
    }

    public CompletableFuture<Optional<T>> findOne(Query<T> query) {
        return this.repository.findOne(this.scoped(query));
    }

    public CompletableFuture<PageResult<T>> findPage(Query<T> query, int page, int size) {
        return this.repository.findPage(this.scoped(query), page, size);
    }

    public CompletableFuture<Long> count(Query<T> query) {
        return this.repository.count(this.scoped(query));
    }

    private T owned(T entity) {
        var model = this.repository.model();
        T copy = model.copy(entity);
        Object current = model.owner(copy);
        if (current != null && !current.equals(this.owner)) throw new IllegalArgumentException("Entity belongs to a different owner");
        BeanAccess.write(copy, model.ownerProperty(), this.owner);
        return copy;
    }

    public CompletableFuture<T> insert(T entity) {
        return this.repository.insert(this.owned(entity));
    }

    public CompletableFuture<Boolean> save(T entity) {
        T copy = this.owned(entity);
        var model = this.repository.model();
        Object id = model.id(copy);
        if (id == null) throw new IllegalArgumentException("save requires primary key");
        var values = new LinkedHashMap<String, Object>();
        for (String property : BeanAccess.fields(model.entityType()).keySet())
            if (!property.equals(model.idProperty())) values.put(property, BeanAccess.read(copy, property));

        return this.repository.update(query().owner(model.idProperty(), id), Patch.fromValues(model.entityType(), values)).thenApply(count -> count > 0);
    }

    public CompletableFuture<Integer> update(Query<T> query, Patch<T> patch) {
        var property = this.repository.model().ownerProperty();
        if (patch.values().containsKey(property) && !Objects.equals(patch.values().get(property), this.owner))
            throw new IllegalArgumentException("Use root repository to transfer ownership");

        return this.repository.update(this.scoped(query), patch);
    }

    public CompletableFuture<Integer> delete(Query<T> query) {
        return this.repository.delete(this.scoped(query));
    }

}
