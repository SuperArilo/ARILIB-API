package com.tty.api.service.placeholder;

import com.tty.api.enumType.PlaceholderTypeEnum;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public interface PlaceholderRegistry {

    void register(PlaceholderDefinition<? extends PlaceholderTypeEnum> definition);
    Optional<PlaceholderResolve> find(String key, OfflinePlayer context);

}

