package com.tty.api.service;

import org.bukkit.entity.Player;

public interface AttackService extends BaseOtherPluginService {

    boolean isInPvp(Player player);

    boolean canAttackPlayer(Player damager, Player victim);

    void changePlayerPvpStatus(Player player, boolean pvpStatus);

}
