package com.tty.api.enumType;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public enum IconKeyType {
    ID("id"),
    X("x"),
    Y("y"),
    Z("z"),
    WORLD_NAME("world_name"),
    PLAYER_NAME("player_name"),
    COST("cost"),
    TOP_SLOT("top_slot"),
    PERMISSION("permission"),
    PLAYER_LEVEL("player_level"),
    PLAYER_EXP("player_exp"),
    PLAYER_TOTAL_EXPERIENCE("player_total_experience"),;

    private static final Map<String, IconKeyType> MAP = Arrays.stream(values()).collect(Collectors.toMap(IconKeyType::getKey, e -> e));

    private final String key;

    IconKeyType(String key) {
        this.key = key;
    }

    public static IconKeyType fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        IconKeyType type = MAP.get(key);
        if (type == null) {
            throw new IllegalArgumentException("Unknown key: " + key);
        }
        return type;
    }

}
