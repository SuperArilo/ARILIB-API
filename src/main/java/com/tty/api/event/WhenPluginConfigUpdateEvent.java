package com.tty.api.event;

import com.tty.api.AbstractJavaPlugin;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WhenPluginConfigUpdateEvent extends Event {

    @Getter
    private final static HandlerList handlerList = new HandlerList();

    @Getter
    private final AbstractJavaPlugin plugin;
    @Getter
    private final YamlConfiguration configuration;

    public WhenPluginConfigUpdateEvent(AbstractJavaPlugin plugin, YamlConfiguration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}
