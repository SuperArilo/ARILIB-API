package com.tty.api.persistence.result;

import java.util.List;

public record ChangeSet<T>(int affectedRows, List<EntityChange<T>> changes) {
    public ChangeSet {
        changes = List.copyOf(changes);
        if (affectedRows < 0 || affectedRows != changes.size()) throw new IllegalArgumentException("Rows and changes differ");
    }

    public static <T> ChangeSet<T> empty() {
        return new ChangeSet<>(0, List.of());
    }

}
