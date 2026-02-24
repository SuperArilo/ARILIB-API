package com.tty.api.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 缓存作用域（请注意，必须填写准确，不然会导致脏缓存）
 * @param value
 */
public record PartitionKey(Object value) {

    private static final PartitionKey GLOBAL = new PartitionKey("__GLOBAL__");

    public static PartitionKey global() {
        return GLOBAL;
    }

    public static PartitionKey of(Object value) {
        if (value == null) return GLOBAL;
        // 如果 value 本身就是 PartitionKey，直接返回
        if (value instanceof PartitionKey) return (PartitionKey) value;
        return new PartitionKey(value);
    }

    public boolean isGlobal() {
        return this == GLOBAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartitionKey(Object value1))) return false;
        return Objects.equals(value, value1);
    }

    @Override
    public @NotNull String toString() {
        return isGlobal() ? "GLOBAL" : String.valueOf(value);
    }

}
