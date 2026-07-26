package com.tty.api.service;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface AttackService extends BaseOtherPluginService {

    boolean isInPvp(Player player);

    boolean canAttackPlayer(Player damager, Player victim);

    void changePvpStatus(Player player, boolean pvpStatus);

    void cancelPvpTag(@NotNull Player player);

}
