package com.tty.api.service.impl;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.ComponentTool;
import com.tty.api.service.placeholder.PlaceholderEngine;
import com.tty.api.service.placeholder.PlaceholderRegistry;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderEngineImpl implements PlaceholderEngine {

    private static final Pattern PATTERN = Pattern.compile("<([a-z0-9_]+)>");

    private final AbstractJavaPlugin plugin;

    @Setter
    @Getter
    private PlaceholderRegistry registry;

    public PlaceholderEngineImpl(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Component> render(String template, OfflinePlayer context) {

        Matcher matcher = PATTERN.matcher(template);
        Map<String, CompletableFuture<Component>> futures = new HashMap<>();

        while (matcher.find()) {
            String key = matcher.group(1);
            if (futures.containsKey(key)) continue;
            this.registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(ComponentTool.text(template, context, Collections.emptyMap()));
        }

        CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);

        return CompletableFuture.allOf(all).thenApplyAsync(v -> {
            Map<String, Component> resolved = new HashMap<>(futures.size());
            futures.forEach((k, f) -> resolved.put(k, f.join()));
            return ComponentTool.text(template, context, resolved);
        }, this.plugin.getExecutorAsync());
    }

    @Override
    public CompletableFuture<Component> renderList(List<String> list, OfflinePlayer context) {

        Set<String> keys = new HashSet<>();
        for (String line : list) {
            Matcher matcher = PATTERN.matcher(line);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }

        Map<String, CompletableFuture<Component>> futures = new HashMap<>(keys.size());
        for (String key : keys) {
            this.registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
        }

        if (futures.isEmpty()) {
            List<Component> components = new ArrayList<>(list.size());
            for (String line : list) {
                components.add(ComponentTool.text(line, context, Collections.emptyMap()));
            }
            return CompletableFuture.completedFuture(Component.join(JoinConfiguration.separator(Component.newline()), components));
        }

        CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);

        return CompletableFuture.allOf(all).thenApplyAsync(v -> {
            Map<String, Component> resolved = new HashMap<>(futures.size());
            futures.forEach((k, f) -> resolved.put(k, f.join()));
            List<Component> components = new ArrayList<>(list.size());
            for (String line : list) {
                components.add(ComponentTool.text(line, context, resolved));
            }
            return Component.join(JoinConfiguration.separator(Component.newline()), components);
        }, this.plugin.getExecutorAsync());
    }

    @Override
    public CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context) {
        Set<String> keys = new HashSet<>();
        for (String line : list) {
            Matcher matcher = PATTERN.matcher(line);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }

        Map<String, CompletableFuture<Component>> futures = new HashMap<>(keys.size());
        for (String key : keys) {
            this.registry.find(key, context)
                    .ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
        }

        if (futures.isEmpty()) {
            List<Component> components = new ArrayList<>(list.size());
            for (String line : list) {
                components.add(ComponentTool.text(line, context, Collections.emptyMap()));
            }
            return CompletableFuture.completedFuture(components);
        }

        CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(all).thenApplyAsync(v -> {
            Map<String, Component> resolved = new HashMap<>(futures.size());
            futures.forEach((k, f) -> resolved.put(k, f.join())); // 此时所有 future 均已完成，join 不会阻塞

            List<Component> components = new ArrayList<>(list.size());
            for (String line : list) {
                components.add(ComponentTool.text(line, context, resolved));
            }
            return components;
        }, this.plugin.getExecutorAsync());

    }

}
