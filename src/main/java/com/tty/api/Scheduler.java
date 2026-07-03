package com.tty.api;

import com.tty.api.scheduler.BukkitScheduler;
import com.tty.api.scheduler.FoliaScheduler;
import com.tty.api.task.CancellableTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

public interface Scheduler {

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    CancellableTask run(Consumer<CancellableTask> task);
    CancellableTask runAtEntity(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback);
    CancellableTask runAtEntityLater(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay);
    CancellableTask runAtEntityFixedRate(Entity entity, Consumer<CancellableTask> task, Runnable errorCallback, long delay, long rate);
    CancellableTask runAtFixedRate(Consumer<CancellableTask> task, long delay, long rate);
    CancellableTask runAsync(Consumer<CancellableTask> task);
    CancellableTask runAsyncAtFixedRate(Consumer<CancellableTask> task, long c, long rate);
    CancellableTask runAtRegion(Location loc, Consumer<CancellableTask> task);
    CancellableTask runAtRegionLater(Location loc, Consumer<CancellableTask> task, long later);
    CancellableTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<CancellableTask> task);
    CancellableTask runAsyncDelayed(Consumer<CancellableTask> task, long delay);
    CancellableTask runLater(Consumer<CancellableTask> task, long delayTicks);

    static Scheduler create(AbstractJavaPlugin plugin) {
        return isFolia() ? new FoliaScheduler(plugin) : new BukkitScheduler(plugin);
    }

}
