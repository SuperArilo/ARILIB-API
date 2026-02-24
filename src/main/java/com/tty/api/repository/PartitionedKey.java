package com.tty.api.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record PartitionedKey<K>(PartitionKey partition, K key) {

    public PartitionedKey(PartitionKey partition, K key) {
        this.partition = partition == null ? PartitionKey.global() : partition;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartitionedKey<?>(PartitionKey partition1, Object key1))) return false;
        return Objects.equals(partition, partition1) && Objects.equals(key, key1);
    }

    @Override
    public @NotNull String toString() {
        return "PartitionedKey[" + partition + " | " + key + "]";
    }

}
