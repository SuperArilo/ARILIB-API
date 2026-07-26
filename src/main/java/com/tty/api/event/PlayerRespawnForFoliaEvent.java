package com.tty.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerRespawnForFoliaEvent extends PlayerEvent {
    @Getter
    private final static HandlerList handlerList = new HandlerList();
    @Getter
    @Setter
    private Location respawnLocation;
    @Setter
    @Getter
    private Location deathLocation;

    public PlayerRespawnForFoliaEvent(@NotNull Player who, @NotNull Location respawnLocation, @NotNull Location deathLocation) {
        super(who);
        this.respawnLocation = respawnLocation;
        this.deathLocation = deathLocation;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }
}
