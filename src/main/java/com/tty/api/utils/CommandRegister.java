package com.tty.api.utils;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tty.api.AbstractJavaPlugin;
import com.tty.api.command.SuperHandsomeCommand;
import com.tty.api.dto.AliasItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.Map;

public class CommandRegister {

    public static void register(AbstractJavaPlugin plugin, String packagePath, Map<String, AliasItem> aliasItemMap) {
        plugin.getLog().debug("----------register commands ----------");
        long start = System.currentTimeMillis();
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            aliasItemMap.forEach((k, v) -> {
                if(!v.isEnable()) return;
                Class<?> executorClass;
                try {
                    executorClass = Class.forName(packagePath + "." + k, true, plugin.getClass().getClassLoader());
                } catch (ClassNotFoundException e) {
                    plugin.getLog().error("Error while constructing instruction. {} class not found!", k);
                    return;
                }
                Object executorInstance;
                try {
                    executorInstance = executorClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    plugin.getLog().error(e, "Error while constructing executor for instruction: {}", k);
                    return;
                }
                if (executorInstance instanceof SuperHandsomeCommand cmd) {
                    commands.register((LiteralCommandNode<CommandSourceStack>) cmd.toBrigadier(), v.getUsage());
                    plugin.getLog().debug((v.isEnable() ? "":"un" ) + "register command: {}", k);
                }
            });
            plugin.getLog().debug("register commands time: {}ms", (System.currentTimeMillis() - start));
        });
    }

}
