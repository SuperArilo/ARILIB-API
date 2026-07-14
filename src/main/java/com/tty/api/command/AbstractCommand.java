package com.tty.api.command;

import com.mojang.brigadier.Command;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractCommand implements SuperHandsomeCommand {

    @Getter
    private final AbstractJavaPlugin plugin;

    private static final CommandManager COMMAND_MANAGER =  new CommandManager();

    protected AbstractCommand(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract void execute(CommandSender sender, String[] args);

    public abstract List<SuperHandsomeCommand> thenCommands();

    protected abstract @NotNull Component onlyUseInGame();

    protected abstract @NotNull Component tokenNotAllow();

    protected abstract @NotNull Component disableInGame();

    protected abstract @NotNull Component taskAlreadyExits();

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

        if (!COMMAND_MANAGER.tryAcquire(sender)) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(this.taskAlreadyExits())).create();
        }

        try {
            this.execute(sender, args);
        } catch (Exception e) {
            this.plugin.getLog().error(e);
        } finally {
            COMMAND_MANAGER.release(sender);
        }

        return Command.SINGLE_SUCCESS;
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

    private static class CommandManager {
        private final Set<String> busyKeys = ConcurrentHashMap.newKeySet();

        private String keyOf(CommandSender sender) {
            if (sender instanceof Player player) {
                return "player:" + player.getUniqueId();
            }
            return "console";
        }

        public boolean tryAcquire(CommandSender sender) {
            return this.busyKeys.add(this.keyOf(sender));
        }

        public void release(CommandSender sender) {
            this.busyKeys.remove(this.keyOf(sender));
        }
    }

}
