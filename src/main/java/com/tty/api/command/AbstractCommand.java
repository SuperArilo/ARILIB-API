package com.tty.api.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.tty.api.AbstractJavaPlugin;
import com.tty.api.annotations.command.CommandMeta;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCommand implements SuperHandsomeCommand {

    @Getter
    private final AbstractJavaPlugin plugin;

    protected AbstractCommand(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }


    public abstract int execute(CommandSender sender, String[] args);

    public abstract List<SuperHandsomeCommand> thenCommands();

    protected abstract @NotNull Component onlyUseInGame();

    protected abstract @NotNull Component tokenNotAllow();

    protected abstract @NotNull Component disableInGame();

    protected abstract boolean isEnableInGame();

    protected int preExecute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        if (!this.isEnableInGame()) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(this.disableInGame())).create();
        }

        CommandMeta meta = this.getClass().getAnnotation(CommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException("missing @CommandMeta on " + getClass());
        }
        if (!meta.allowConsole() && !(sender instanceof Player)) {
            throw new DynamicCommandExceptionType(name -> MessageComponentSerializer.message().serialize(this.onlyUseInGame())).create(sender.getName());
        }

        String[] args = this.getRealCommandArgs(ctx);

        if (args.length != meta.tokenLength()) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(this.tokenNotAllow())).create();
        }

        return this.execute(sender, args);
    }

    private String @NotNull [] getRealCommandArgs(CommandContext<CommandSourceStack> ctx) {
        String input = ctx.getInput();
        List<String> args = new ArrayList<>();
        boolean maySkipPluginName = true;
        for (ParsedCommandNode<CommandSourceStack> parsedNode : ctx.getNodes()) {
            CommandNode<?> node = parsedNode.getNode();
            if (maySkipPluginName && node.getName().equalsIgnoreCase(plugin.getName())) {
                maySkipPluginName = false;
                continue;
            }
            maySkipPluginName = false;
            StringRange range = parsedNode.getRange();
            args.add(input.substring(range.getStart(), range.getEnd()));
        }
        return args.toArray(new String[0]);
    }

}
