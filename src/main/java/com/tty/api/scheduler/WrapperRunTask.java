package com.tty.api.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.scheduler.BukkitTask;

public class WrapperRunTask<T> implements RunTask {

    @Getter
    @Setter
    private T task;

    public WrapperRunTask(T task) {
        this.task = task;
    }

    public WrapperRunTask() {}

    @Override
    public void cancel() {
        switch (this.task) {
            case null -> throw new IllegalStateException("task not initialized");
            case BukkitTask bukkitTask -> bukkitTask.cancel();
            case ScheduledTask scheduledTask -> scheduledTask.cancel();
            default -> throw new IllegalStateException("unknown task type.");
        }
    }

    @Override
    public boolean isCancelled() {
        if (this.task instanceof BukkitTask bukkitTask) {
            return bukkitTask.isCancelled();
        } else if (this.task instanceof ScheduledTask scheduledTask) {
            return scheduledTask.isCancelled();
        }
        return false;
    }

}

