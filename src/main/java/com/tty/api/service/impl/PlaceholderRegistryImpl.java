package com.tty.api.service.impl;

import com.tty.api.enumType.PlaceholderTypeEnum;
import com.tty.api.service.placeholder.PlaceholderResolve;
import com.tty.api.service.placeholder.PlaceholderDefinition;
import com.tty.api.service.placeholder.PlaceholderRegistry;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PlaceholderRegistryImpl implements PlaceholderRegistry {

    private final Map<String, PlaceholderResolve> placeholders = new HashMap<>();

    @Override
    public void register(PlaceholderDefinition<? extends PlaceholderTypeEnum> definition) {
        String key = definition.key().getType();
        if (this.placeholders.containsKey(key)) {
            throw new IllegalStateException("Duplicate placeholder: " + key);
        }
        this.placeholders.put(key, definition.resolver());
    }

    @Override
    public Optional<PlaceholderResolve> find(String key, OfflinePlayer context) {
        return Optional.ofNullable(this.placeholders.get(key));
    }

}
