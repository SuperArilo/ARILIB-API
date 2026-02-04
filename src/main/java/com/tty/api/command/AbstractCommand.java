package com.tty.api.command;

import com.mojang.brigadier.context.CommandContext;
import com.tty.api.annotations.command.CommandMeta;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractCommand implements SuperHandsomeCommand {

    protected static final String[] PLUGIN_NAMES;

    static {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(plugins, (a, b) -> Integer.compare(b.getName().length(), a.getName().length()));
        PLUGIN_NAMES = Arrays.stream(plugins)
                .map(Plugin::getName)
                .toArray(String[]::new);
    }

    public abstract void execute(CommandSender sender, String[] args);

    public abstract List<SuperHandsomeCommand> thenCommands();

    protected int preExecute(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (this.isDisabledInGame()) {
            sender.sendMessage(this.disableInGame());
            return 0;
        }

        CommandMeta meta = this.getClass().getAnnotation(CommandMeta.class);
        if (!meta.allowConsole() && !(sender instanceof Player)) {
            sender.sendMessage(this.onlyUseInGame());
            return 0;
        }

        String input = ctx.getInput().trim();

        for (String name : PLUGIN_NAMES) {
            if (input.startsWith(name + " ")) {
                input = input.substring(name.length()).trim();
                break;
            }
        }

        String[] args = input.isEmpty() ? new String[0] : input.split(" ");

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("\"") && arg.endsWith("\"") && arg.length() >= 2) {
                args[i] = arg.substring(1, arg.length() - 1);
            }
        }

        if (args.length != meta.tokenLength()) {
            sender.sendMessage(this.tokenNotAllow());
            return 0;
        }

        this.execute(sender, args);
        return 1;
    }

    protected abstract @NotNull Component onlyUseInGame();

    protected abstract @NotNull Component tokenNotAllow();

    protected abstract @NotNull Component disableInGame();

    protected abstract boolean havePermission(CommandSender sender, String permission);

    protected abstract boolean isDisabledInGame();

}
