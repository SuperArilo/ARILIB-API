package com.tty.api.listener;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import com.tty.api.gui.BaseInventory;
import com.tty.api.state.EditGuiState;
import com.tty.api.utils.FormatUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public abstract class BaseEditFunctionGuiListener<T extends BaseInventory, D> extends BaseGuiListener<T> {

    protected BaseEditFunctionGuiListener(AbstractJavaPlugin plugin, GuiKeyEnum guiType) {
        super(plugin, guiType);
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EditGuiState<D> state = this.isHaveState(player);
        if (state == null || !this.getGuiType().equals(state.getType())) return;
        event.setCancelled(true);
        String message = FormatUtils.componentToString(event.message());

        //玩家手动输入 cancel 取消操作
        if (FunctionType.CANCEL.name().equals(message.toUpperCase())) {
            state.setOver(true);
            player.clearTitle();
            this.whenTimeout(player);
            return;
        }

        //玩家输入内容检查通过
        if (this.onTitleEditStatus(message, state)) {
            player.clearTitle();
            state.setOver(true);
        }
    }

    public abstract EditGuiState<D> isHaveState(Player player);

    /**
     * 检查玩家的输入内容
     * @param message 玩家的输入内容
     * @param state 玩家的输入状态类
     * @return true 检查通过，反之
     */
    public abstract boolean onTitleEditStatus(String message, EditGuiState<D> state);

    public abstract void whenTimeout(Player player);

}
