package com.tty.api.utils;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tty.api.dto.PageResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 基础sql接口类
 * @param <T> 实体类
 */
public abstract class BaseDataManager<T> {

    /**
     * 是否异步
     */
    public boolean isAsync;

    public BaseDataManager(boolean isAsync) {
        this.isAsync = isAsync;
    }

    /**
     * 设置执行模式
     * @param async true: 异步模式, false: 同步模式
     */
    public void setExecutionMode(boolean async) {
        this.isAsync = async;
    }

    protected <R> CompletableFuture<R> executeTask(Supplier<R> task) {
        if (this.isAsync) {
            return CompletableFuture.supplyAsync(task);
        } else {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    public abstract CompletableFuture<PageResult<T>> getList(int pageNum, int pageSize, LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> get(LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<T> create(T instance);

    public abstract CompletableFuture<Boolean> delete(T entity);

    public abstract CompletableFuture<Boolean> delete(LambdaQueryWrapper<T> key);

    public abstract CompletableFuture<Boolean> update(T instance, LambdaQueryWrapper<T> key);

}
