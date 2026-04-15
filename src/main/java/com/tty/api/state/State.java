package com.tty.api.state;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Entity;

import java.util.concurrent.atomic.AtomicInteger;

public class State {

    @Getter
    private final Entity owner;

    /**
     * 基础计数
     */
    @Getter
    @Setter
    private AtomicInteger count = new AtomicInteger(0);

    /**
     * 最大检查计数
     */
    @Getter
    private final int max_count;

    /**
     * 是否提前结束
     */
    @Getter
    @Setter
    private volatile boolean isOver = false;

    /**
     * 当前的次数是否在进行中
     */
    @Getter
    @Setter
    private volatile boolean pending = false;

    public State(Entity owner, int max_count) {
        this.owner = owner;
        this.max_count = max_count;
    }

    public void increment() {
        this.count.updateAndGet(current -> current < this.max_count ? current + 1 : current);
    }

    public boolean isDone() {
        return this.count.get() >= this.max_count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State state)) return false;
        return this.owner != null && this.owner.equals(state.owner);
    }

    @Override
    public int hashCode() {
        return this.owner != null ? this.owner.hashCode() : 0;
    }

}
