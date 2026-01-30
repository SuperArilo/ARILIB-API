package com.tty.api.command;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

public abstract class AbstractSubCommand {

    protected static final String[] PLUGIN_NAMES;

    static {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(plugins, (a, b) -> Integer.compare(b.getName().length(), a.getName().length()));
        PLUGIN_NAMES = Arrays.stream(plugins)
                .map(Plugin::getName)
                .toArray(String[]::new);
    }

    protected abstract int preExecute(CommandContext<CommandSourceStack> ctx);

}
