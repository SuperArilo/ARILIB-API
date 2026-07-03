package com.tty.api.scheduler;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.Scheduler;
import com.tty.api.task.CancellableTask;
import com.tty.api.task.WrapperScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Consumer;

import java.util.concurrent.atomic.AtomicReference;

public record BukkitScheduler(AbstractJavaPlugin plugin) implements Scheduler {

    @Override
    public CancellableTask run(Consumer<CancellableTask> task) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTask(this.plugin, () -> task.accept(atomicReference.get()))));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtEntity(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                task.accept(new WrapperScheduledTask<>(atomicReference.get()));
            } catch (Exception e) {
                if (errorCallback == null) return;
                errorCallback.run();
            }
        });
        atomicReference.set(new WrapperScheduledTask<>(bukkitTask));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtEntityLater(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            try {
                task.accept(new WrapperScheduledTask<>(atomicReference.get()));
            } catch (Exception e) {
                if (errorCallback == null) return;
                errorCallback.run();
            }
        }, delay);
        atomicReference.set(new WrapperScheduledTask<>(bukkitTask));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtEntityFixedRate(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay, long rate) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            try {
                task.accept(atomicReference.get());
            } catch (Exception e) {
                if (errorCallback == null) return;
                errorCallback.run();
            }
        }, delay, rate);
        atomicReference.set(new WrapperScheduledTask<>(bukkitTask));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtFixedRate(Consumer<CancellableTask> task, long delay, long rate) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskTimer(this.plugin, () -> task.accept(atomicReference.get()), delay, rate)));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAsync(Consumer<CancellableTask> task) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> task.accept(atomicReference.get()))));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAsyncAtFixedRate(Consumer<CancellableTask> task, long delay, long rate) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> task.accept(atomicReference.get()), delay, rate)));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtRegion(Location loc, Consumer<CancellableTask> task) {
        return this.run(task);
    }

    @Override
    public CancellableTask runAtRegionLater(Location loc, Consumer<CancellableTask> task, long later) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskLater(this.plugin, () -> task.accept(atomicReference.get()), later)));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<CancellableTask> task) {
        return this.run(task);
    }

    @Override
    public CancellableTask runAsyncDelayed(Consumer<CancellableTask> task, long delay) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> task.accept(atomicReference.get()), delay)));
        return atomicReference.get();
    }

    @Override
    public CancellableTask runLater(Consumer<CancellableTask> task, long delayTicks) {
        AtomicReference<WrapperScheduledTask<BukkitTask>> atomicReference = new AtomicReference<>();
        atomicReference.set(new WrapperScheduledTask<>(Bukkit.getScheduler().runTaskLater(this.plugin, () -> task.accept(atomicReference.get()), delayTicks)));
        return atomicReference.get();
    }
}
