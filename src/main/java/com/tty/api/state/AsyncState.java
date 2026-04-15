package com.tty.api.state;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Entity;

public class AsyncState extends State {

    /**
     * 如果该状态存在异步等待，则需要设置
     */
    @Getter
    @Setter
    private volatile boolean isRunning;

    public AsyncState(Entity owner, int max_count) {
        super(owner, max_count);
    }
}
