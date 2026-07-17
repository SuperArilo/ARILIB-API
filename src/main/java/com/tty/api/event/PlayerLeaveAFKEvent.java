package com.tty.api.event;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerLeaveAFKEvent extends Event {

    @Getter
    private final Player player;

    @Getter
    private final static HandlerList handlerList = new HandlerList();

    public PlayerLeaveAFKEvent(Player player) {
        this.player = player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

}
