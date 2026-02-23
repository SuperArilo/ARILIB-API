package com.tty.api.event;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class CustomPluginReloadEvent extends Event implements Cancellable {
    @Getter
    private final static HandlerList handlerList = new HandlerList();
    private boolean isCancelled = false;
    @Getter
    private final CommandSender sender;
    @Getter
    private final JavaPlugin plugin;

    public CustomPluginReloadEvent(JavaPlugin plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.isCancelled = b;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}
