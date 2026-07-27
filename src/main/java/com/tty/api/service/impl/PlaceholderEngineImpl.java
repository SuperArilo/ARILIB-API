package com.tty.api.service.impl;

import com.tty.api.service.placeholder.PlaceholderEngine;
import com.tty.api.service.placeholder.PlaceholderRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderEngineImpl implements PlaceholderEngine {

    private static final Pattern PATTERN = Pattern.compile("<([a-z0-9_]+)>");

    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));

    private final PlaceholderRegistry registry;

    public PlaceholderEngineImpl(PlaceholderRegistry registry) {
        this.registry = Objects.requireNonNullElseGet(registry, PlaceholderRegistryImpl::new);
    }

    @Override
    public CompletableFuture<Component> render(String template, OfflinePlayer context) {
        String string = this.processPlaceholder(template, context);

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
                resolved.put(k, this.build(join == null ? "":join, null));
            });
            return this.build(string, resolved);
        }, this.executor);

    }

    @Override
    public CompletableFuture<Component> renderList(List<String> list, OfflinePlayer context) {

        List<String> lines = list.stream().map(line -> this.processPlaceholder(line, context)).toList();

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
                resolved.put(k, this.build(join == null ? "":join, null));
            });
            List<Component> components = lines.stream().map(line -> this.build(line, resolved)).toList();
            return Component.join(JoinConfiguration.separator(Component.newline()), components);
        }, this.executor);
    }

    @Override
    public CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context) {

        List<String> lines = list.stream().map(line -> this.processPlaceholder(line, context)).toList();

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
                resolved.put(k, this.build(join == null ? "":join, null));
            });
            return lines.stream().map(line -> this.build(line, resolved)).toList();

        }, this.executor);

    }

    @Override
    public Component directRender(String template) {
        return this.build(this.processPlaceholder(template, null), null);
    }

    @Override
    public Component directRender(String template, OfflinePlayer context) {
        return this.build(this.processPlaceholder(template, context), null);
    }

    @Override
    public Component directRender(String template, Map<String, Component> map) {
        return this.build(this.processPlaceholder(template, null), map);
    }

    @Override
    public Component directRender(String template, OfflinePlayer context, Map<String, Component> map) {
        return this.build(this.processPlaceholder(template, context), map);
    }

    @Override
    public Component directRenderList(List<String> templates, OfflinePlayer context) {
        List<Component> list = new ArrayList<>();
        for (String template : templates) {
            list.add(this.build(this.processPlaceholder(template, context), null));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), list);
    }

    @Override
    public List<Component> directRenderAsComponentList(List<String> list, OfflinePlayer context) {
        List<Component> componentList = new ArrayList<>();
        for (String s : list) {
            componentList.add(this.build(this.processPlaceholder(s, context), null));
        }
        return componentList;
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
    }

}
