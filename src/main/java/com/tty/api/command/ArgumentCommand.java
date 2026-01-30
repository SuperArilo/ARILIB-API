package com.tty.api.command;

import org.bukkit.command.CommandSender;

import java.util.List;


public interface ArgumentCommand extends SuperHandsomeCommand {

    void execute(CommandSender sender, String[] args);
    List<SuperHandsomeCommand> thenCommands();

}
