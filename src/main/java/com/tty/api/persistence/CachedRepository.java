package com.tty.api.persistence;

import com.tty.api.persistence.cache.CachePolicy;
import com.tty.api.persistence.cache.RepositoryCache;
import com.tty.api.persistence.model.BeanAccess;
import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.query.Patch;
import com.tty.api.persistence.query.Query;
import com.tty.api.persistence.result.ChangeSet;
import com.tty.api.dto.PageResult;

import com.github.benmanes.caffeine.cache.Ticker;
import com.tty.api.persistence.cache.CacheTrace;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class CachedRepository<T, ID> implements AutoCloseable {

    private final EntityStore<T, ID> store;
    private final EntityModel<T, ID> model;
    private final RepositoryCache<T> cache;
    private final Executor writeExecutor;
    private final Object lifecycle = new Object();
    private CompletableFuture<Void> writeTail = CompletableFuture.completedFuture(null);
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private int active;
    private boolean closing, closingStore;

    public CachedRepository(EntityStore<T, ID> store) {
        this(store, CachePolicy.defaults(), ForkJoinPool.commonPool(), Ticker.systemTicker());
    }

    public CachedRepository(EntityStore<T, ID> store, CacheTrace trace) {
        this(store, CachePolicy.defaults(), ForkJoinPool.commonPool(), Ticker.systemTicker(), trace);
    }

    public CachedRepository(EntityStore<T, ID> store, CachePolicy policy, Executor writeExecutor, Ticker ticker) {
        this(store, policy, writeExecutor, ticker, CacheTrace.NONE);
    }

    public CachedRepository(EntityStore<T, ID> store, CachePolicy policy, Executor writeExecutor, Ticker ticker, CacheTrace trace) {
        this.store = Objects.requireNonNull(store);
        this.model = Objects.requireNonNull(store.model());
        this.cache = new RepositoryCache<>(this.model, policy, Objects.requireNonNull(ticker), Objects.requireNonNull(trace));
        this.writeExecutor = Objects.requireNonNull(writeExecutor);
    }

    public EntityModel<T, ID> model() {
        return this.model;
    }

    public OwnedRepository<T, ID> forOwner(Object owner) {
        this.model.validateOwner(owner);
        return new OwnedRepository<>(this, BeanAccess.scalar(owner));
    }

    private Query<T> checked(Query<T> query) {
        if (query.entityType() != this.model.entityType()) throw new IllegalArgumentException("Query entity mismatch");
        return query;
    }

    private RepositoryCache.Key<T> key(Query<T> query, RepositoryCache.Kind kind, int page, int size) {
        return new RepositoryCache.Key<>(this.checked(query), kind, page, size);
    }

    @SuppressWarnings("unchecked")
    private Optional<T> optionalCopy(Object value) {
        return ((Optional<T>) value).map(this.model::copy);
    }

    @SuppressWarnings("unchecked")
    private List<T> listCopy(Object value) {
        return ((List<T>) value).stream().map(this.model::copy).toList();
    }

    @SuppressWarnings("unchecked")
    private PageResult<T> pageCopy(Object value) {
        var p = (PageResult<T>) value;
        return PageResult.build(listCopy(p.records()), p.total(), p.totalPages(), p.currentPage());
    }

    public CompletableFuture<Optional<T>> findOne(Query<T> query) {
        var key = key(query.withoutOrder(), RepositoryCache.Kind.ONE, 0, 0);
        return this.perform(() ->
                this.cache.load(key, () ->
                        this.store.findOne(key.query()).thenApply(v -> v.map(this.model::copy))).thenApply(this::optionalCopy));
    }

    public CompletableFuture<List<T>> findAll(Query<T> query) {
        var key = key(query, RepositoryCache.Kind.ALL, 0, 0);
        return this.perform(() ->
                this.cache.load(key, () ->
                        this.store.findAll(query).thenApply(this::listCopy)).thenApply(this::listCopy));
    }

    public CompletableFuture<PageResult<T>> findPage(Query<T> query, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("Page and size must be positive");
        var key = key(query, RepositoryCache.Kind.PAGE, page, size);
        return this.perform(() ->
                this.cache.load(key, () ->
                        this.store.findPage(query, page, size).thenApply(this::pageCopy)).thenApply(this::pageCopy));
    }

    public CompletableFuture<Long> count(Query<T> query) {
        var key = key(query.withoutOrder(), RepositoryCache.Kind.COUNT, 0, 0);
        return this.perform(() -> this.cache.load(key, () -> this.store.count(key.query()).thenApply(v -> v)).thenApply(v -> (Long) v));
    }

    public Optional<T> peekOne(Query<T> query) {
        var value = this.cache.peek(key(query.withoutOrder(), RepositoryCache.Kind.ONE, 0, 0));
        return value == null ? Optional.empty() : this.optionalCopy(value);
    }

    public Optional<List<T>> peekAll(Query<T> query) {
        var value = this.cache.peek(key(query, RepositoryCache.Kind.ALL, 0, 0));
        return value == null ? Optional.empty() : Optional.of(this.listCopy(value));
    }

    public CompletableFuture<T> insert(T entity) {
        T snapshot = this.model.copy(Objects.requireNonNull(entity));
        return this.write(() -> this.store.insert(snapshot), changes -> {
            if (changes.affectedRows() != 1 || changes.changes().getFirst().after() == null) throw new IllegalStateException("Store did not insert one entity");
            return this.model.copy(changes.changes().getFirst().after());
        });
    }

    public CompletableFuture<Boolean> save(T entity) {
        T snapshot = this.model.copy(Objects.requireNonNull(entity));
        if (this.model.id(snapshot) == null) throw new IllegalArgumentException("save requires primary key; use insert");
        return this.write(() -> this.store.save(snapshot), changes -> changes.affectedRows() > 0);
    }

    public CompletableFuture<Integer> update(Query<T> query, Patch<T> patch) {
        this.checked(query);
        this.model.validatePatch(patch);
        return this.write(() -> this.store.update(query, patch), ChangeSet::affectedRows);
    }

    public CompletableFuture<Integer> delete(Query<T> query) {
        this.checked(query);
        return write(() -> this.store.delete(query), ChangeSet::affectedRows);
    }

    private <R> CompletableFuture<R> write(Supplier<CompletableFuture<ChangeSet<T>>> action, Function<ChangeSet<T>, R> result) {
        return this.perform(() -> {
            CompletableFuture<Void> previous, gate = new CompletableFuture<>();
            synchronized (this.lifecycle) {
                previous = this.writeTail;
                this.writeTail = gate;
            }
            CompletableFuture<R> completion = new CompletableFuture<>();
            previous.handle((v, e) -> null)
                .thenComposeAsync(ignored -> invoke(action), writeExecutor)
                .whenComplete((changes, error) -> {
                    R value = null;
                    Throwable failure = error;
                    if (failure == null)
                        try {
                            this.cache.invalidate(changes);
                            value = result.apply(changes);
                        } catch (Throwable ex) {
                            failure = ex;
                        }
                    gate.complete(null);
                    if (failure == null) completion.complete(value);
                    else completion.completeExceptionally(failure);
                });
            return completion;
        });
    }

    private static <R> CompletableFuture<R> invoke(Supplier<CompletableFuture<R>> action) {
        try {
            return Objects.requireNonNull(action.get());
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <R> CompletableFuture<R> perform(Supplier<CompletableFuture<R>> action) {
        synchronized (this.lifecycle) {
            if (this.closing)
                return CompletableFuture.failedFuture(new IllegalStateException("Repository is closing"));
            this.active++;
        }
        var operation = invoke(action);
        var visible = new CompletableFuture<R>();
        operation.whenComplete((value, error) -> {
            boolean finishClosing;
            synchronized (this.lifecycle) {
                this.active--;
                finishClosing = this.closing && this.active == 0;
            }
            if (finishClosing) {
                try {
                    this.writeExecutor.execute(this::tryCloseStore);
                } catch (RuntimeException rejected) {
                    this.tryCloseStore();
                }
            }
            if (error == null) visible.complete(value);
            else visible.completeExceptionally(error);
        });
        return visible;
    }

    public void clearCache() {
        this.cache.clear();
    }

    public int cachedQueryCount() {
        return this.cache.queryCount();
    }

    public CompletableFuture<Void> closeAsync() {
        synchronized (this.lifecycle) {
            this.closing = true;
        }
        tryCloseStore();
        return this.closed.thenApply(Function.identity());
    }

    private void tryCloseStore() {
        synchronized (this.lifecycle) {
            if (!this.closing || this.active != 0 || this.closingStore) return;
            closingStore = true;
        }
        this.cache.clear();
        invoke(this.store::closeAsync).whenComplete((value, error) -> {
            if (error == null) this.closed.complete(null);
            else this.closed.completeExceptionally(error);
        });
    }

    public void close(Duration timeout) {
        try {
            closeAsync().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted closing repository", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Repository close did not finish", e);
        }
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }

}
