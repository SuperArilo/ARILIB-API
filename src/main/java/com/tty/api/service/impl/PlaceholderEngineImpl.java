package com.tty.api.service.impl;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.service.placeholder.PlaceholderEngine;
import com.tty.api.service.placeholder.PlaceholderRegistry;
import com.tty.api.utils.ColorConverterLegacy;
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

    private final PlaceholderRegistry registry;

    private final boolean t;

    public PlaceholderEngineImpl(AbstractJavaPlugin plugin, PlaceholderRegistry registry) {
        this.plugin = plugin;
        this.registry = Objects.requireNonNullElseGet(registry, PlaceholderRegistryImpl::new);
        this.t = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public CompletableFuture<Component> render(String template, OfflinePlayer context) {

        CompletableFuture<String> future = this.processPlaceholder(template, context).thenApply(ColorConverterLegacy::convert);

        return future.thenCompose(string -> {

            Matcher matcher = PATTERN.matcher(string);
            Map<String, CompletableFuture<String>> futures = new HashMap<>();
            while (matcher.find()) {
                String key = matcher.group(1);
                if (futures.containsKey(key)) continue;
                registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
            }

            if (futures.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> this.build(string, null), this.executor);
            }

            CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
            return CompletableFuture.allOf(all).thenApplyAsync(v -> {
                Map<String, Component> resolved = new HashMap<>(futures.size());
                futures.forEach((k, f) -> {
                    String join = f.join();
                    resolved.put(k, Component.text(join == null ? "":join));
                });
                return this.build(string, resolved);
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

            Map<String, CompletableFuture<String>> futures = new HashMap<>(keys.size());
            for (String key : keys) {
                registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
            }

            if (futures.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> {
                    List<Component> components = lines.stream().map(line -> this.build(line, Collections.emptyMap())).toList();
                    return Component.join(JoinConfiguration.separator(Component.newline()), components);
                }, this.executor);
            }

            CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
            return CompletableFuture.allOf(all).thenApplyAsync(t -> {
                Map<String, Component> resolved = new HashMap<>(futures.size());

                futures.forEach((k, f) -> {
                    String join = f.join();
                    resolved.put(k, Component.text(join == null ? "":join));
                });
                List<Component> components = lines.stream().map(line -> this.build(line, resolved)).toList();
                return Component.join(JoinConfiguration.separator(Component.newline()), components);
            }, this.executor);
        });
    }

    @Override
    public CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context) {

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

            Map<String, CompletableFuture<String>> futures = new HashMap<>(keys.size());
            for (String key : keys) {
                registry.find(key, context).ifPresent(resolver -> futures.put(key, resolver.resolve(context)));
            }

            if (futures.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> lines.stream().map(line -> this.build(line, Collections.emptyMap())).toList(), this.executor);
            }

            CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture[0]);
            return CompletableFuture.allOf(all).thenApplyAsync(t -> {

                Map<String, Component> resolved = new HashMap<>(futures.size());
                futures.forEach((k, f) -> {
                    String join = f.join();
                    resolved.put(k, Component.text(join == null ? "":join));
                });
                return lines.stream().map(line -> this.build(line, resolved)).toList();

            }, this.executor);

        });
    }

    @Override
    public Component directRender(String template) {
        return this.build(this.processPlaceholder(template, null).join(), null);
    }

    @Override
    public Component directRender(String template, OfflinePlayer context) {
        return this.build(this.processPlaceholder(template, context).join(), null);
    }

    @Override
    public Component directRender(String template, Map<String, Component> map) {
        return this.build(this.processPlaceholder(template, null).join(), map);
    }

    @Override
    public Component directRender(String template, OfflinePlayer context, Map<String, Component> map) {
        return this.build(this.processPlaceholder(template, context).join(), map);
    }

    @Override
    public Component directRenderList(List<String> templates, OfflinePlayer context) {
        List<Component> list = new ArrayList<>();
        for (String template : templates) {
            list.add(this.build(this.processPlaceholder(template, context).join(), null));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), list);
    }

    @Override
    public List<Component> directRenderAsComponentList(List<String> list, OfflinePlayer context) {
        List<Component> componentList = new ArrayList<>();
        for (String s : list) {
            componentList.add(this.build(this.processPlaceholder(s, context).join(), null));
        }
        return componentList;
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
    }

    private CompletableFuture<String> processPlaceholder(@Nullable String content, OfflinePlayer offlinePlayer) {
        if (content == null || content.isEmpty()) return CompletableFuture.completedFuture(content);

        if (this.t) {
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
