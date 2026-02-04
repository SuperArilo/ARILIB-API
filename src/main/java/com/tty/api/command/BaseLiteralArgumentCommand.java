package com.tty.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tty.api.annotations.command.CommandMeta;
import com.tty.api.annotations.command.LiteralCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;


public abstract class BaseLiteralArgumentCommand extends AbstractCommand {

    @Override
    public LiteralCommandNode<CommandSourceStack> toBrigadier() {
        CommandMeta meta = this.getClass().getAnnotation(CommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " lost @CommandMeta");
        }
        LiteralArgumentBuilder<CommandSourceStack> top_mian = Commands.literal(meta.displayName());
        top_mian.requires(ctx -> this.havePermission(ctx.getSender(), meta.permission()));
        LiteralCommand annotation = this.getClass().getAnnotation(LiteralCommand.class);
        if (annotation != null && annotation.directExecute()) {
            top_mian.executes(this::preExecute);
        }
        for (SuperHandsomeCommand subCommand : this.thenCommands()) {
            top_mian.then(subCommand.toBrigadier());
        }
        return top_mian.build();
    }

    protected abstract boolean havePermission(CommandSender sender, String permission);

}
