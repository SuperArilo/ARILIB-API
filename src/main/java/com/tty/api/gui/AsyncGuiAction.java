package com.tty.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AsyncGuiAction<T extends BaseInventory> {

    CompletableFuture<Void> execute(InventoryClickEvent event, T holder, Player player);

}
