package com.tty.api.annotations.function_type;

import com.tty.api.enumType.FunctionType;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class FunctionHandlerRegistry {

    private final Map<FunctionType, MethodHandle> handlers = new HashMap<>();
    @Getter
    private final Object handlerInstance;

    public FunctionHandlerRegistry(Object handlerInstance) {
        this.handlerInstance = handlerInstance;
        if (handlerInstance == null) return;
        Class<?> handlerClass = handlerInstance.getClass();

        for (Method method : handlerClass.getDeclaredMethods()) {
            FunctionHandler ann = method.getAnnotation(FunctionHandler.class);
            if (ann == null) continue;
            method.setAccessible(true);
            try {
                this.handlers.put(ann.value(), MethodHandles.lookup().unreflect(method).bindTo(handlerInstance));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                Bukkit.getLogger().log(Level.SEVERE, "Failed to register handler for " + ann.value(), e);
            }
        }
    }

    public void dispatch(FunctionType type, InventoryClickEvent event, Object holder, Player player) {
        MethodHandle handle = this.handlers.get(type);
        if (handle != null) {
            try {
                handle.invoke(type, event, holder, player);
            } catch (Throwable e) {
                e.printStackTrace();
                Bukkit.getLogger().severe("Error dispatching FunctionType: " + type);
            }
        }
    }

}
