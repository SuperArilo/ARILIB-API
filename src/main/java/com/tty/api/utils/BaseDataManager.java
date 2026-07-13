package com.tty.api.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tty.api.dto.PageResult;
import lombok.Getter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class BaseDataManager<T> {

    @Getter
    private volatile boolean isAsync;

    private final ExecutorService executor;

    private final Supplier<SqlSessionFactory> factorySupplier;

    public BaseDataManager(@NotNull Supplier<SqlSessionFactory> factorySupplier, boolean isAsync) {
        this.factorySupplier = factorySupplier;
        this.isAsync = isAsync;
        this.executor = new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread thread = new Thread(r, "tty-db-thread");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    /**
     * 设置执行模式
     * @param async true: 异步模式, false: 同步模式
     */
    public void setExecutionMode(boolean async) {
        this.isAsync = async;
    }

    protected <R> CompletableFuture<R> executeTask(Function<SqlSession, R> task) {
        SqlSession session = this.factorySupplier.get().openSession(true);
        if (!this.isAsync) {
            try {
                R result = task.apply(session);
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            } finally {
                session.close();
            }
        } else {
            return CompletableFuture.supplyAsync(() -> task.apply(session), this.executor)
                    .whenComplete((result, ex) -> session.close());
        }
    }

    protected <R> CompletableFuture<R> executeTransaction(Function<SqlSession, R> task) {
        if (!this.isAsync) {
            SqlSession session = this.factorySupplier.get().openSession(false);
            try {
                R result = task.apply(session);
                session.commit();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                session.rollback();
                return CompletableFuture.failedFuture(e);
            } finally {
                session.close();
            }
        } else {
            return CompletableFuture.supplyAsync(() -> {
                SqlSession session = this.factorySupplier.get().openSession(false);
                try {
                    R result = task.apply(session);
                    session.commit();
                    return result;
                } catch (Exception e) {
                    session.rollback();
                    throw e;
                } finally {
                    session.close();
                }
            }, this.executor);
        }
    }

    public abstract CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize, LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> get(LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> create(T instance);

    public abstract CompletableFuture<Boolean> delete(T entity);

    public abstract CompletableFuture<Integer> delete(LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<Boolean> update(T instance, LambdaQueryWrapper<T> key);

    public void shutdown() {
        this.executor.shutdownNow();
        try {
            if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}