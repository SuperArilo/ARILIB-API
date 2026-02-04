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

import java.util.ArrayList;
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

    protected abstract @NotNull Component onlyUseInGame();

    protected abstract @NotNull Component tokenNotAllow();

    protected abstract @NotNull Component disableInGame();

    protected abstract boolean isDisabledInGame();

    protected int preExecute(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (this.isDisabledInGame()) {
            sender.sendMessage(this.disableInGame());
            return 0;
        }

        CommandMeta meta = this.getClass().getAnnotation(CommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException("missing @CommandMeta on " + getClass());
        }
        if (!meta.allowConsole() && !(sender instanceof Player)) {
            sender.sendMessage(this.onlyUseInGame());
            return 0;
        }

        String[] args = getRealCommandArgs(ctx);

        if (args.length != meta.tokenLength()) {
            sender.sendMessage(this.tokenNotAllow());
            return 0;
        }

        this.execute(sender, args);
        return 1;
    }

    private static String @NotNull [] getRealCommandArgs(CommandContext<CommandSourceStack> ctx) {
        String input = ctx.getInput().trim();
        for (String name : PLUGIN_NAMES) {
            String plain = name + " ";
            String namespaced = name + ":" + name + " ";
            if (input.startsWith(plain)) {
                input = input.substring(plain.length()).trim();
                break;
            } else if (input.startsWith(namespaced)) {
                input = input.substring(namespaced.length()).trim();
                break;
            }
        }

        return tokenizeArgs(input);
    }

    private static String @NotNull [] tokenizeArgs(String input) {
        if (input.isEmpty()) return new String[0];
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escape) {
                current.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args.toArray(new String[0]);
    }

}
