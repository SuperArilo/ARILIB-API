package com.tty.api.listener;

import com.tty.api.Log;
import com.tty.api.annotations.gui.GuiMeta;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiType;
import com.tty.api.gui.BaseConfigInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;


public abstract class BaseGuiListener implements Listener {

    protected final GuiType guiType;

    protected BaseGuiListener(GuiType guiType) {
        this.guiType = guiType;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        Inventory bottomInventory = view.getBottomInventory();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        BaseConfigInventory topHolder = topInventory.getHolder() instanceof BaseConfigInventory t ? t : null;
        BaseConfigInventory clickedHolder = clickedInventory.getHolder() instanceof BaseConfigInventory c ? c : null;

        if (topHolder != null && event.isShiftClick()) {
            if (clickedInventory.equals(topInventory) || clickedInventory.equals(bottomInventory)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && clickedInventory.equals(topInventory) && topHolder != null) {
            event.setCancelled(true);
            return;
        }

        if (clickedHolder == null) return;
        GuiMeta annotation = clickedHolder.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        GuiType type = annotation.type();
        boolean isCustomGui = type.equals(this.guiType);
        if (clickedInventory.equals(topInventory) && isCustomGui) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.isShiftClick()) return;
            this.passClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();

        if (!(topInventory.getHolder() instanceof BaseConfigInventory holder)) return;

        GuiMeta annotation = holder.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        if (!annotation.type().equals(this.guiType)) return;

        int topSize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                break;
            }
        }
    }

    /**
     * 当点击通过 GUI 检查时调用，由子类实现具体点击处理逻辑
     *
     * @param event InventoryClickEvent
     */
    public abstract void passClick(InventoryClickEvent event);

    /**
     * 检查GUI里的 function icon 是否合法
     * @param rawType function icon 的字符串
     * @return 返回指定的 FunctionType
     */
    protected FunctionType ItemNBT_TypeCheck(String rawType) {
        if(rawType == null) return null;
        FunctionType type;
        try {
            type = FunctionType.valueOf(rawType.toUpperCase());
            return type;
        } catch (Exception e) {
            Log.debug(e, "Function type {} error", rawType);
            return null;
        }
    }

}
