package com.tty.api.scheduler;

import com.tty.api.AbstractJavaPlugin;
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

    RunTask run(Consumer<RunTask> task);
    RunTask runAtEntity(Entity entity, Consumer<RunTask> task, Runnable errorCallback);
    RunTask runAtEntityLater(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay);
    RunTask runAtEntityFixedRate(Entity entity, Consumer<RunTask> task, Runnable errorCallback, long delay, long rate);
    RunTask runAtFixedRate(Consumer<RunTask> task, long delay, long rate);
    RunTask runAsync(Consumer<RunTask> task);
    RunTask runAsyncAtFixedRate(Consumer<RunTask> task, long c, long rate);
    RunTask runAtRegion(Location loc, Consumer<RunTask> task);
    RunTask runAtRegionLater(Location loc, Consumer<RunTask> task, long later);
    RunTask runAtRegion(World world, int chunkX, int chunkZ, Consumer<RunTask> task);
    RunTask runAsyncDelayed(Consumer<RunTask> task, long delay);
    RunTask runLater(Consumer<RunTask> task, long delayTicks);

    static Scheduler create(AbstractJavaPlugin plugin) {
        return isFolia() ? new FoliaScheduler(plugin) : new BukkitScheduler(plugin);
    }

}
