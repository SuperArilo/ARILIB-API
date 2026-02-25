package com.tty.api.utils;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tty.api.dto.PageResult;
import lombok.Getter;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 基础sql接口类
 * @param <T> 实体类
 */
public abstract class BaseDataManager<T> {

    /**
     * 是否异步
     */
    @Getter
    private boolean isAsync;

    private final ExecutorService executor;

    public BaseDataManager(boolean isAsync) {
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

    protected <R> CompletableFuture<R> executeTask(Supplier<R> task) {
        if (!this.isAsync) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, this.executor);
    }

    public abstract CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize, LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> get(LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> create(T instance);

    public abstract CompletableFuture<Boolean> delete(T entity);

    public abstract CompletableFuture<Boolean> delete(LambdaQueryWrapper<T> key);

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
