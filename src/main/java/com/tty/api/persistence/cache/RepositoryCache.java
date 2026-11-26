package com.tty.api.persistence.cache;

import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.query.Query;
import com.tty.api.persistence.result.ChangeSet;
import com.tty.api.dto.PageResult;

import com.github.benmanes.caffeine.cache.*;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;


public final class RepositoryCache<T> {
    public enum Kind {
        ONE,
        ALL,
        PAGE,
        COUNT
    }

    public record Key<T>(Query<T> query, Kind kind, int page, int size) {}

    private static final class Entry<T> {
        final Key<T> key;
        final CompletableFuture<Object> future = new CompletableFuture<>();
        boolean loading = true;
        Object value;

        Entry(Key<T> key) {
            this.key = key;
        }
    }

    private final Object lock = new Object();
    private final EntityModel<T, ?> model;
    private final CacheTrace trace;
    private final String entityName;
    private final Cache<Key<T>, Entry<T>> singles, collections;
    private final Map<Key<T>, Entry<T>> entries = new HashMap<>();
    private final Map<Object, Set<Key<T>>> byOwner = new HashMap<>();
    private final Set<Key<T>> unscoped = new HashSet<>();
    private final Queue<Entry<T>> removed = new ConcurrentLinkedQueue<>();


    private record Slot<E>(CompletableFuture<Object> hit, Entry<E> entry) {}

    public RepositoryCache(EntityModel<T, ?> model, CachePolicy policy) {
        this(model, policy, Ticker.systemTicker());
    }

    public RepositoryCache(EntityModel<T, ?> model, CachePolicy policy, Ticker ticker) {
        this(model, policy, ticker, CacheTrace.NONE);
    }

    public RepositoryCache(EntityModel<T, ?> model, CachePolicy policy, Ticker ticker, CacheTrace trace) {
        this.model = Objects.requireNonNull(model);
        this.trace = Objects.requireNonNull(trace);
        this.entityName = model.entityType().getSimpleName();
        this.singles = this.build(policy.singleEntries(), policy, ticker);
        this.collections = this.build(policy.collectionEntries(), policy, ticker);
    }

    private Cache<Key<T>, Entry<T>> build(long capacity, CachePolicy policy, Ticker ticker) {
        return Caffeine.newBuilder()
                .maximumSize(capacity)
                .ticker(ticker)
                .executor(Runnable::run)
                .expireAfter(new Expiry<Key<T>, Entry<T>>() {
                    public long expireAfterCreate(@NonNull Key<T> key, Entry<T> entry, long now) {
                        return negative(entry.value) ? policy.missTtl().toNanos() : policy.ttl().toNanos();
                    }

                    public long expireAfterUpdate(@NonNull Key<T> key, @NonNull Entry<T> entry, long now, long duration) {
                        return this.expireAfterCreate(key, entry, now);
                    }

                    public long expireAfterRead(@NonNull Key<T> key, @NonNull Entry<T> entry, long now, long duration) {
                        return duration;
                    }
                }).removalListener((key, entry, cause) -> {
                    if (entry != null) this.removed.add(entry);
                }).build();
    }

    private boolean negative(Object value) {
        return value instanceof Optional<?> o && o.isEmpty()
                || value instanceof List<?> l && l.isEmpty()
                || value instanceof PageResult<?> p && p.records().isEmpty()
                || value instanceof Long n && n == 0L;
    }

    private Cache<Key<T>, Entry<T>> cache(Key<T> key) {
        return key.kind() == Kind.ONE ? this.singles : this.collections;
    }

    private Set<Object> domain(Key<T> key) {
        return this.model.grouped() ? key.query().ownerValues(this.model.ownerProperty()) : null;
    }

    private void register(Entry<T> entry) {
        this.entries.put(entry.key, entry);
        Set<Object> owners = domain(entry.key);
        if (owners == null) this.unscoped.add(entry.key);
        else
            for (Object owner : owners)
                this.byOwner.computeIfAbsent(owner, k -> new HashSet<>()).add(entry.key);
    }

    private void unregister(Entry<T> entry) {
        if (!this.entries.remove(entry.key, entry)) return;
        this.unscoped.remove(entry.key);
        Set<Object> owners = domain(entry.key);
        if (owners != null)
            for (Object owner : owners) {
                var keys = this.byOwner.get(owner);
                if (keys != null) {
                    keys.remove(entry.key);
                    if (keys.isEmpty()) this.byOwner.remove(owner);
                }
            }
    }

    private void drain() {
        for (Entry<T> entry; (entry = this.removed.poll()) != null; )
            if (!entry.loading) this.unregister(entry);
    }

    private Slot<T> acquire(Key<T> key) {
        synchronized (this.lock) {
            this.drain();
            var cached = this.cache(key).getIfPresent(key);
            this.drain();
            if (cached != null) {
                return new Slot<>(cached.future, null);
            }
            var existing = this.entries.get(key);
            if (existing != null && existing.loading) {
                return new Slot<>(existing.future, null);
            }
            if (existing != null) this.unregister(existing);
            var entry = new Entry<>(key);
            this.register(entry);
            return new Slot<>(null, entry);
        }
    }

    public CompletableFuture<Object> load(Key<T> key, Supplier<CompletableFuture<Object>> loader) {
        var slot = this.acquire(key);
        this.trace.lookup(this.entityName, key.kind().name(), key.query(), key.page(), key.size(), slot.hit() != null);

        if (slot.hit() != null) {
            return slot.hit();
        }
        Entry<T> entry = slot.entry();
        CompletableFuture<Object> loading;
        try {
            loading = Objects.requireNonNull(loader.get(), "loader future");
        } catch (Throwable ex) {
            loading = CompletableFuture.failedFuture(ex);
        }
        loading.whenComplete((value, error) -> {
            Throwable failure = error;
            synchronized (lock) {
                entry.loading = false;
                if (this.entries.get(key) == entry) {
                    if (failure == null) {
                        try {
                            entry.value = Objects.requireNonNull(value, "cache result");
                            this.cache(key).put(key, entry);
                            this.drain();
                        } catch (Throwable ex) {
                            failure = ex;
                            this.unregister(entry);
                            this.cache(key).invalidate(key);
                        }
                    } else this.unregister(entry);
                }
            }
            if (failure == null) entry.future.complete(value);
            else entry.future.completeExceptionally(failure);
        });
        return entry.future;
    }

    public Object peek(Key<T> key) {
        Object value;
        synchronized (lock) {
            this.drain();
            var entry = this.cache(key).getIfPresent(key);
            this.drain();
            value = entry == null ? null : entry.value;
        }
        this.trace.lookup(this.entityName, key.kind().name(), key.query(), key.page(), key.size(), value != null);

        return value;
    }

    public void invalidate(ChangeSet<T> changes) {
        if (changes.changes().isEmpty()) return;
        int invalidated;
        synchronized (lock) {
            this.drain();
            Set<Key<T>> candidates = new HashSet<>(unscoped);
            if (model.grouped())
                for (var change : changes.changes()) {
                    if (change.before() != null)
                        candidates.addAll(byOwner.getOrDefault(model.owner(change.before()), Set.of()));
                    if (change.after() != null)
                        candidates.addAll(byOwner.getOrDefault(model.owner(change.after()), Set.of()));
                }

            invalidated = 0;

            for (Key<T> key : candidates) {
                var entry = this.entries.get(key);
                if (entry == null) continue;
                boolean affected = key.kind() != Kind.ONE || changes.changes().stream().anyMatch(c -> key.query().matches(c.before()) || key.query().matches(c.after()));

                if (affected) {
                    this.unregister(entry);
                    this.cache(key).invalidate(key);
                    invalidated++;
                }
            }
            this.drain();

        }
        this.trace.invalidate(this.entityName, invalidated, changes.changes().size());
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            byOwner.clear();
            unscoped.clear();
            singles.invalidateAll();
            collections.invalidateAll();
            this.drain();
        }
    }

    public int queryCount() {
        synchronized (lock) {
            singles.cleanUp();
            collections.cleanUp();
            this.drain();
            return entries.size();
        }
    }

}
