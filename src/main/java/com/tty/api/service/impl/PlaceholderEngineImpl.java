package com.tty.api.service.impl;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.ComponentTool;
import com.tty.api.service.placeholder.PlaceholderEngine;
import com.tty.api.service.placeholder.PlaceholderRegistry;
import com.tty.api.utils.ColorConverterLegacy;
import lombok.Getter;
import lombok.Setter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderEngineImpl implements PlaceholderEngine {

    private static final Pattern PATTERN = Pattern.compile("<([a-z0-9_]+)>");

    private final AbstractJavaPlugin plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    @Setter
    @Getter
    private PlaceholderRegistry registry;

    public PlaceholderEngineImpl(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Component> render(String template, OfflinePlayer context) {

        CompletableFuture<String> future = this.processPlaceholder(template, context).thenApply(ColorConverterLegacy::convert);

        return future.thenCompose(string -> {

            Matcher matcher = PATTERN.matcher(string);
            Map<String, CompletableFuture<Component>> futures = new HashMap<>();
            while (matcher.find()) {
                String key = matcher.group(1);
                if (futures.containsKey(key)) continue;
                registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
            }

            if (futures.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> ComponentTool.text(string, Collections.emptyMap()), this.executor);
            }

            CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
            return CompletableFuture.allOf(all).thenApplyAsync(v -> {
                Map<String, Component> resolved = new HashMap<>(futures.size());
                futures.forEach((k, f) -> resolved.put(k, f.join()));
                return ComponentTool.text(string, resolved);
            }, this.executor);

        });
    }

    @Override
    public CompletableFuture<Component> renderList(List<String> list, OfflinePlayer context) {

        List<CompletableFuture<String>> futureList = list.stream().map(line -> this.processPlaceholder(line, context).thenApply(ColorConverterLegacy::convert)).toList();

        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).thenCompose(v -> {

            List<String> lines = futureList.stream().map(CompletableFuture::join).toList();

            Set<String> keys = new HashSet<>();

            for (String line : lines) {
                Matcher matcher = PATTERN.matcher(line);
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }

            Map<String, CompletableFuture<Component>> futures = new HashMap<>(keys.size());
            for (String key : keys) {
                registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
            }

            if (futures.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> {
                    List<Component> components = lines.stream().map(line -> ComponentTool.text(line, Collections.emptyMap())).toList();
                    return Component.join(JoinConfiguration.separator(Component.newline()), components);
                }, this.executor);
            }

            CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
            return CompletableFuture.allOf(all).thenApplyAsync(t -> {
                Map<String, Component> resolved = new HashMap<>(futures.size());

                futures.forEach((k, f) -> resolved.put(k, f.join()));
                List<Component> components = lines.stream().map(line -> ComponentTool.text(line, resolved)).toList();
                return Component.join(JoinConfiguration.separator(Component.newline()), components);
            }, this.executor);
        });
    }

    @Override
    public CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context) {

        List<CompletableFuture<String>> futureList = list.stream().map(line -> processPlaceholder(line, context).thenApply(ColorConverterLegacy::convert)).toList();

        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).thenCompose(v -> {

                    List<String> lines = futureList.stream().map(CompletableFuture::join).toList();

                    Set<String> keys = new HashSet<>();
                    for (String line : lines) {
                        Matcher matcher = PATTERN.matcher(line);
                        while (matcher.find()) {
                            keys.add(matcher.group(1));
                        }
                    }

                    Map<String, CompletableFuture<Component>> futures = new HashMap<>(keys.size());
                    for (String key : keys) {
                        registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
                    }

                    if (futures.isEmpty()) {
                        return CompletableFuture.supplyAsync(() -> lines.stream().map(line -> ComponentTool.text(line, Collections.emptyMap())).toList(), this.executor);
                    }

                    CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
                    return CompletableFuture.allOf(all).thenApplyAsync(t -> {

                        Map<String, Component> resolved = new HashMap<>(futures.size());
                        futures.forEach((k, f) -> resolved.put(k, f.join()));
                        return lines.stream().map(line -> ComponentTool.text(line, resolved)).toList();

                    }, this.executor);

                });
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
    }

    private CompletableFuture<String> processPlaceholder(@Nullable String content, OfflinePlayer offlinePlayer) {
        if (content == null || content.isEmpty()) return CompletableFuture.completedFuture(content);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (Bukkit.getServer().isPrimaryThread()) {
                return CompletableFuture.completedFuture(PlaceholderAPI.setPlaceholders(offlinePlayer, content));
            } else {
                CompletableFuture<String> future = new CompletableFuture<>();
                this.plugin.getScheduler().run(i -> {
                    try {
                        future.complete(PlaceholderAPI.setPlaceholders(offlinePlayer, content));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                return future;
            }
        }
        return CompletableFuture.completedFuture(content);
    }
}
