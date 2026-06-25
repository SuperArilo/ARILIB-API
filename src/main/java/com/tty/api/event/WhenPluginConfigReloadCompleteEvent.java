package com.tty.api.event;

import com.tty.api.AbstractJavaPlugin;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhenPluginConfigReloadCompleteEvent extends Event {

    @Getter
    private final static HandlerList handlerList = new HandlerList();

    @Getter
    private final AbstractJavaPlugin plugin;
    @Getter
    private final CommandSender sender;

    public WhenPluginConfigReloadCompleteEvent(AbstractJavaPlugin plugin, @Nullable CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}
