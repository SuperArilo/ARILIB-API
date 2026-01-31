package com.tty.api.listener;

import com.tty.api.FormatUtils;
import com.tty.api.annotations.gui.GuiMeta;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import com.tty.api.gui.BaseInventory;
import com.tty.api.state.EditGuiState;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

public abstract class BaseEditFunctionGuiListener extends BaseGuiListener {

    protected BaseEditFunctionGuiListener(GuiKeyEnum guiType) {
        super(guiType);
    }

    @Override
    public void passClick(InventoryClickEvent event) {}

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EditGuiState state = this.isHaveState(player);
        if (state == null) return;
        BaseInventory i = state.getI();
        GuiMeta annotation = i.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        if (!annotation.type().equals(this.guiType.getType())) return;
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

    public abstract EditGuiState isHaveState(Player player);

    /**
     * 检查玩家的输入内容
     * @param message 玩家的输入内容
     * @param state 玩家的输入状态类
     * @return true 检查通过，反之
     */
    public abstract boolean onTitleEditStatus(String message, EditGuiState state);

    public abstract void whenTimeout(Player player);
}
