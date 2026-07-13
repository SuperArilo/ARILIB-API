package com.tty.api.annotations.function_type;

import com.tty.api.TriConsumer;
import com.tty.api.enumType.FunctionType;
import com.tty.api.gui.AsyncGuiAction;
import com.tty.api.gui.BaseInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionHandler<T extends BaseInventory> {

    private final Map<FunctionType, AsyncGuiAction<T>> actionMap = new ConcurrentHashMap<>();

    public void addAsync(FunctionType type, AsyncGuiAction<T> action) {
        this.actionMap.put(type, action);
    }

    public void addSync(FunctionType type, TriConsumer<InventoryClickEvent, T, Player> consumer) {
        addAsync(type, (event, holder, player) -> {
            consumer.accept(event, holder, player);
            return CompletableFuture.completedFuture(null);
        });
    }

    public void dispatch(FunctionType type, InventoryClickEvent event, T holder, Player player, Runnable pending, Runnable done, Runnable onError) {
        AsyncGuiAction<T> action = this.actionMap.get(type);
        if (action == null) return;

        CompletableFuture<Void> future = action.execute(event, holder, player);

        if (pending != null) {
            pending.run();
        }

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                if (done != null) done.run();
            } else {
                if (onError != null) onError.run();
            }
        });

    }

}