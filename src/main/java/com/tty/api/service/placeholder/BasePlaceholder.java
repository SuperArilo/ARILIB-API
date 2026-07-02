package com.tty.api.service.placeholder;

import com.google.common.reflect.TypeToken;
import com.tty.api.AbstractJavaPlugin;
import com.tty.api.configuration.BaseConfiguration;
import com.tty.api.service.impl.PlaceholderEngineImpl;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BasePlaceholder {

    private final AbstractJavaPlugin plugin;
    private final PlaceholderEngineImpl engine;
    @Setter
    private BaseConfiguration instance;

    private final Type typeTokenList = new TypeToken<List<String>>() {}.getType();

    public BasePlaceholder(AbstractJavaPlugin plugin, BaseConfiguration instance) {
        this.instance = instance;
        this.plugin = plugin;
        this.engine = new PlaceholderEngineImpl(plugin);
    }

    protected CompletableFuture<Component> empty() {
        return CompletableFuture.completedFuture(Component.empty());
    }

    protected CompletableFuture<Component> set(String value) {
        return CompletableFuture.completedFuture(this.plugin.getComponentTool().text(value));
    }

    public CompletableFuture<Component> render(String path, Player player) {
        return this.engine.render(instance.getValue(path, String.class, "null"), player);
    }

    public CompletableFuture<Component> render(String path, OfflinePlayer offlinePlayer) {
        return this.engine.render(instance.getValue(path, String.class, "null"), offlinePlayer);
    }

    public CompletableFuture<Component> renderList(String path, Player player) {
        return this.engine.renderList(instance.getValue(path, typeTokenList, List.of()), player);
    }

    public CompletableFuture<Component> renderList(String path, OfflinePlayer offlinePlayer) {
        return this.engine.renderList(instance.getValue(path, typeTokenList, List.of()), offlinePlayer);
    }

    protected void addRegister(PlaceholderRegistry registry) {
        this.engine.setRegistry(registry);
    }

}
