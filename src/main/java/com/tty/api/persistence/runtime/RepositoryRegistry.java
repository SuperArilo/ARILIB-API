package com.tty.api.persistence.runtime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tty.api.Log;
import com.tty.api.enumType.SQLType;
import com.tty.api.persistence.CachedRepository;
import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.mybatis.MyBatisEntityStore;
import org.apache.ibatis.session.SqlSessionFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class RepositoryRegistry implements AutoCloseable {

    private final Log log;
    private final Duration closeTimeout;
    private final ExecutorService executor;
    private final Map<Class<?>, CachedRepository<?, ?>> repositories = new LinkedHashMap<>();

    public RepositoryRegistry(Log log, String threadName, int threads, Duration closeTimeout) {
        this.log = Objects.requireNonNull(log, "log");
        this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
        int size = Math.max(1, threads);
        this.executor = Executors.newFixedThreadPool(size, task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public ExecutorService executor() {
        return this.executor;
    }

    public static <T, ID> CachedRepository<T, ID> create(
            Log log,
            EntityModel<T, ID> model,
            Class<? extends BaseMapper<T>> mapper,
            Supplier<SqlSessionFactory> factory,
            Executor executor,
            SQLType sqlType) {
        var store = new MyBatisEntityStore<>(model, factory, mapper, executor, sqlType);
        return new CachedRepository<>(log, store);
    }

    public <T, ID> CachedRepository<T, ID> register(CachedRepository<T, ID> repository) {
        Objects.requireNonNull(repository, "repository");
        Class<T> type = repository.model().entityType();
        synchronized (this.repositories) {
            if (this.repositories.putIfAbsent(type, repository) != null)
                throw new IllegalStateException("Repository already registered for " + type.getName());
        }
        return repository;
    }

    @SuppressWarnings("unchecked")
    public <T> CachedRepository<T, ?> find(Class<T> type) {
        synchronized (this.repositories) {
            CachedRepository<?, ?> repository = this.repositories.get(type);
            if (repository == null)
                throw new IllegalArgumentException("No repository registered for " + type.getName());
            return (CachedRepository<T, ?>) repository;
        }
    }

    public Collection<CachedRepository<?, ?>> repositories() {
        synchronized (this.repositories) {
            return List.copyOf(this.repositories.values());
        }
    }

    public void clearAllCaches() {
        for (CachedRepository<?, ?> repository : this.repositories()) {
            repository.clearCache();
        }
    }

    @Override
    public void close() {
        for (CachedRepository<?, ?> repository : this.repositories()) {
            try {
                repository.close(this.closeTimeout);
            } catch (RuntimeException e) {
                this.log.error(e, "close repository did not finish in time");
            }
        }
        this.executor.shutdown();
    }

    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(this::close);
    }

}
