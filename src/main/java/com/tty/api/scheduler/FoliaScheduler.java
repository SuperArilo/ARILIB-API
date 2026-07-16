package com.tty.api.scheduler;

import com.tty.api.AbstractJavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public record FoliaScheduler(AbstractJavaPlugin plugin) implements Scheduler {

    @Override
    public RunTask run(Consumer<RunTask> task) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getGlobalRegionScheduler().run(this.plugin, i -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAtEntity(Entity entity, Consumer<RunTask> task, Runnable errorCallback) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(entity.getScheduler().run(this.plugin, i -> task.accept(wrapper), errorCallback));
        return wrapper;
    }

    @Override
    public RunTask runAtEntityLater(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(entity.getScheduler().runDelayed(this.plugin, i -> task.accept(wrapper), errorCallback, delay));
        return wrapper;
    }

    @Override
    public RunTask runAtEntityFixedRate(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay, long rate) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(entity.getScheduler().runAtFixedRate(this.plugin, i -> task.accept(wrapper), errorCallback, delay, rate));
        return wrapper;
    }

    @Override
    public RunTask runAtFixedRate(Consumer<RunTask> task, long delay, long rate) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, i -> task.accept(wrapper), delay, rate));
        return wrapper;
    }

    @Override
    public RunTask runAsync(Consumer<RunTask> task) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getAsyncScheduler().runNow(this.plugin, i -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAsyncAtFixedRate(Consumer<RunTask> task, long delay, long rate) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, i -> task.accept(wrapper), delay * 50, rate * 50, TimeUnit.MILLISECONDS));
        return wrapper;
    }

    @Override
    public RunTask runAtRegion(Location loc, Consumer<RunTask> task) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getRegionScheduler().run(this.plugin, loc, i -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAtRegionLater(Location loc, Consumer<RunTask> task, long later) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getRegionScheduler().runDelayed(this.plugin, loc, i -> task.accept(wrapper), later));
        return wrapper;
    }

    @Override
    public RunTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<RunTask> task) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ, i -> task.accept(wrapper)));
        return wrapper;
    }

    @Override
    public RunTask runAsyncDelayed(Consumer<RunTask> task, long delay) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getAsyncScheduler().runDelayed(this.plugin, i -> task.accept(wrapper), delay * 50, TimeUnit.MILLISECONDS));
        return wrapper;
    }

    @Override
    public RunTask runLater(Consumer<RunTask> task, long delayTicks) {
        WrapperRunTask<Object> wrapper = new WrapperRunTask<>();
        wrapper.setTask(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, i -> task.accept(wrapper), delayTicks));
        return wrapper;
    }

}