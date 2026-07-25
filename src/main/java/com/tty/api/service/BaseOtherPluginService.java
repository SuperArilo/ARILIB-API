package com.tty.api.service;

import com.tty.api.AbstractJavaPlugin;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface BaseOtherPluginService{

    String pluginName();

    default <Q> void loadOtherPlugin(@NotNull AbstractJavaPlugin plugin, @NotNull String pluginName, @NotNull Class<Q> clazz, Consumer<Q> consumer) {
        if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            try {
                Q hook = clazz.getDeclaredConstructor().newInstance();
                if (consumer != null) {
                    consumer.accept(hook);
                }
            } catch (Throwable e) {
                plugin.getLog().warn(e, "failed to load hook: " + clazz.getName());
            }
        }
    }

}
