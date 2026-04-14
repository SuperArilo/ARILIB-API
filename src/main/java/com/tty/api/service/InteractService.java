package com.tty.api.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface InteractService {

    String pluginName();

    boolean canBuild(Location location);

    boolean canBuild(Location location, Player player);

    boolean canTeleport(Location location);

    boolean canTeleport(Location location, Player player);

    boolean canInteract(Location location);

    boolean canInteract(Location location, Player player);

}
