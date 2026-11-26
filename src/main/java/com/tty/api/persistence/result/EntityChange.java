package com.tty.api.persistence.result;


public record EntityChange<T>(T before, T after) {
    public EntityChange {
        if (before == null && after == null) throw new IllegalArgumentException("Empty change");
    }
}
