package com.tty.api.scheduler;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.Scheduler;
import com.tty.api.task.CancellableTask;
import com.tty.api.task.WrapperScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

import java.util.concurrent.TimeUnit;


public record FoliaScheduler(AbstractJavaPlugin plugin) implements Scheduler {


    @Override
    public CancellableTask run(Consumer<CancellableTask> task) {
        return new WrapperScheduledTask<>(Bukkit.getGlobalRegionScheduler().run(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i))));
    }

    @Override
    public CancellableTask runAtEntity(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback) {
        return new WrapperScheduledTask<>(entity.getScheduler().run(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), errorCallback));
    }

    @Override
    public CancellableTask runAtEntityLater(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay) {
        return new WrapperScheduledTask<>(entity.getScheduler().runDelayed(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), errorCallback, delay));
    }

    @Override
    public CancellableTask runAtEntityFixedRate(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay, long rate) {
        return new WrapperScheduledTask<>(entity.getScheduler().runAtFixedRate(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), errorCallback, delay, rate));
    }

    @Override
    public CancellableTask runAtFixedRate(Consumer<CancellableTask> task, long delay, long rate) {
        return new WrapperScheduledTask<>(Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), delay, rate));
    }

    @Override
    public CancellableTask runAsync(Consumer<CancellableTask> task) {
        return new WrapperScheduledTask<>(Bukkit.getAsyncScheduler().runNow(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i))));
    }

    @Override
    public CancellableTask runAsyncAtFixedRate(Consumer<CancellableTask> task, long delay, long rate) {
        return new WrapperScheduledTask<>(Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), delay * 50, rate * 50, TimeUnit.MILLISECONDS));
    }

    @Override
    public CancellableTask runAtRegion(Location loc, Consumer<CancellableTask> task) {
        return new WrapperScheduledTask<>(Bukkit.getRegionScheduler().run(this.plugin, loc, i -> task.accept(new WrapperScheduledTask<>(i))));
    }

    @Override
    public CancellableTask runAtRegionLater(Location loc, Consumer<CancellableTask> task, long later) {
        return new WrapperScheduledTask<>(Bukkit.getRegionScheduler().runDelayed(this.plugin, loc, i -> task.accept(new WrapperScheduledTask<>(i)), later));
    }

    @Override
    public CancellableTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<CancellableTask> task) {
        return new WrapperScheduledTask<>(Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ, i -> task.accept(new WrapperScheduledTask<>(i))));
    }

    @Override
    public CancellableTask runAsyncDelayed(Consumer<CancellableTask> task, long delay) {
        return new WrapperScheduledTask<>(Bukkit.getAsyncScheduler().runDelayed(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), delay * 50, TimeUnit.MILLISECONDS));
    }


    @Override
    public CancellableTask runLater(Consumer<CancellableTask> task, long delayTicks) {
        return new WrapperScheduledTask<>(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, i -> task.accept(new WrapperScheduledTask<>(i)), delayTicks));
    }
}
