package com.tty.api.enumType;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public enum Operator {

    CONSOLE("00000000-0000-0000-0000-000000000000"),
    RCON("11111111-0000-0000-0000-000000000000");

    @Getter
    private final String uuid;
    private static final Map<String, Operator> UUID_TO_OPERATOR = new HashMap<>();

    static {
        for (Operator op : values()) {
            UUID_TO_OPERATOR.put(op.uuid, op);
        }
    }

    Operator(String uuid) {
        this.uuid = uuid;
    }

    public static UUID getOperator(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getUniqueId();
        } else if (sender instanceof RemoteConsoleCommandSender) {
            return UUID.fromString(RCON.getUuid());
        } else {
            return UUID.fromString(CONSOLE.getUuid());
        }
    }

    public static Operator fromUuid(String uuidStr) {
        if (uuidStr == null) {
            return null;
        }
        return UUID_TO_OPERATOR.get(uuidStr);
    }

    public static Operator fromUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return fromUuid(uuid.toString());
    }

}
