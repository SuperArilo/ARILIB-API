package com.tty.api.persistence.cache;

import com.tty.api.persistence.query.Query;

public interface CacheTrace {

    CacheTrace NONE = new CacheTrace() {};

    default void lookup(String entity, String kind, Query<?> query, int page, int size, boolean hit) {}

    default void invalidate(String entity, int cachedKeys, int changes) {}

}
