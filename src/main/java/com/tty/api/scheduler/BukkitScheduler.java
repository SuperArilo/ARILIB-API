package com.tty.api.scheduler;

import com.tty.api.AbstractJavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Consumer;

public record BukkitScheduler(AbstractJavaPlugin plugin) implements Scheduler {

    @Override
    public RunTask run(Consumer<RunTask> task) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTask(this.plugin, () -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAtEntity(Entity entity, Consumer<RunTask> task, Runnable errorCallback) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                task.accept(wrapper);
            } catch (Exception e) {
                if (errorCallback != null) errorCallback.run();
            }
        });
        wrapper.setTask(bukkitTask);
        return wrapper;
    }

    @Override
    public RunTask runAtEntityLater(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            try {
                task.accept(wrapper);
            } catch (Exception e) {
                if (errorCallback != null) errorCallback.run();
            }
        }, delay);
        wrapper.setTask(bukkitTask);
        return wrapper;
    }

    @Override
    public RunTask runAtEntityFixedRate(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay, long rate) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            try {
                task.accept(wrapper);
            } catch (Exception e) {
                if (errorCallback != null) errorCallback.run();
            }
        }, delay, rate);
        wrapper.setTask(bukkitTask);
        return wrapper;
    }

    @Override
    public RunTask runAtFixedRate(Consumer<RunTask> task, long delay, long rate) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskTimer(this.plugin, () -> task.accept(wrapper), delay, rate));
        return wrapper;
    }

    @Override
    public RunTask runAsync(Consumer<RunTask> task) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAsyncAtFixedRate(Consumer<RunTask> task, long delay, long rate) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> task.accept(wrapper), delay, rate));
        return wrapper;
    }

    @Override
    public RunTask runAtRegion(Location loc, Consumer<RunTask> task) {
        return this.run(task);
    }

    @Override
    public RunTask runAtRegionLater(Location loc, Consumer<RunTask> task, long later) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskLater(this.plugin, () -> task.accept(wrapper), later));
        return wrapper;
    }

    @Override
    public RunTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<RunTask> task) {
        return this.run(task);
    }

    @Override
    public RunTask runAsyncDelayed(Consumer<RunTask> task, long delay) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> task.accept(wrapper), delay));
        return wrapper;
    }

    @Override
    public RunTask runLater(Consumer<RunTask> task, long delayTicks) {
        WrapperRunTask<BukkitTask> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getScheduler().runTaskLater(this.plugin, () -> task.accept(wrapper), delayTicks));
        return wrapper;
    }
}