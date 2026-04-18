package com.tty.api.annotations.function_type;

import com.tty.api.TriConsumer;
import com.tty.api.enumType.FunctionType;
import com.tty.api.gui.BaseInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public class FunctionHandler<T extends BaseInventory> {

    private final Map<FunctionType, TriConsumer<InventoryClickEvent, T, Player>> map = new HashMap<>();

    public void add(FunctionType type, TriConsumer<InventoryClickEvent, T, Player> t) {
        this.map.put(type, t);
    }

    public void dispatch(FunctionType type, InventoryClickEvent event, T holder, Player player) {
        TriConsumer<InventoryClickEvent, T, Player> consumer = this.map.get(type);
        if (consumer == null) {
            throw new NullPointerException("can not found function type " + type.getName() + "in function handler");
        }
        consumer.accept(event, holder, player);
    }

}
