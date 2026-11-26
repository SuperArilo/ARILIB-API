package com.tty.api.persistence.cache;

import java.time.Duration;
import java.util.Objects;

public record CachePolicy(long singleEntries, long collectionEntries, Duration ttl, Duration missTtl) {

    public CachePolicy {
        Objects.requireNonNull(ttl);
        Objects.requireNonNull(missTtl);
        if (singleEntries < 1 || collectionEntries < 1 || ttl.isNegative() || ttl.isZero() || missTtl.isNegative() || missTtl.isZero()) throw new IllegalArgumentException("Cache capacities and durations must be positive");
        ttl.toNanos();
        missTtl.toNanos();
    }

    public static CachePolicy defaults() {
        return new CachePolicy(2000, 200, Duration.ofMinutes(300), Duration.ofSeconds(30));
    }

}
