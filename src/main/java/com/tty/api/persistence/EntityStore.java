package com.tty.api.persistence;

import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.query.Patch;
import com.tty.api.persistence.query.Query;
import com.tty.api.persistence.result.ChangeSet;
import com.tty.api.dto.PageResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface EntityStore<T, ID> {
    EntityModel<T, ID> model();

    CompletableFuture<Optional<T>> findOne(Query<T> query);

    CompletableFuture<List<T>> findAll(Query<T> query);

    CompletableFuture<PageResult<T>> findPage(Query<T> query, int page, int size);

    CompletableFuture<Long> count(Query<T> query);

    CompletableFuture<ChangeSet<T>> insert(T entity);

    CompletableFuture<ChangeSet<T>> save(T entity);

    CompletableFuture<ChangeSet<T>> update(Query<T> query, Patch<T> patch);

    CompletableFuture<ChangeSet<T>> delete(Query<T> query);

    default CompletableFuture<Void> closeAsync() {
        return CompletableFuture.completedFuture(null);
    }

}
