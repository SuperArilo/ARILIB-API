package com.tty.api.listener;

import com.tty.api.annotations.function_type.FunctionHandlerRegistry;
import com.tty.api.annotations.gui.GuiMeta;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import com.tty.api.gui.BaseConfigInventory;
import com.tty.api.utils.GuiNBTKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class BaseGuiListener<T extends InventoryHolder> implements Listener {

    private final FunctionHandlerRegistry registry;
    private final JavaPlugin plugin;

    private final NamespacedKey clickFunctionIcon;

    protected final GuiKeyEnum guiType;

    protected BaseGuiListener(@NotNull JavaPlugin plugin, @Nullable  FunctionHandlerRegistry registry, @NotNull GuiKeyEnum guiType) {
        this.guiType = guiType;
        this.registry = registry;
        this.plugin = plugin;
        this.clickFunctionIcon = new NamespacedKey(this.plugin, GuiNBTKeys.GUI_RENDER_FUNCTION_ICON);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        Inventory bottomInventory = view.getBottomInventory();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        T topHolder = (T) topInventory.getHolder();
        T clickedHolder = (T) clickedInventory.getHolder();

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
        boolean isCustomGui = annotation.type().equals(this.guiType.getType());
        if (clickedInventory.equals(topInventory) && isCustomGui) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.isShiftClick()) return;

            ItemStack currentItem = event.getCurrentItem();

            FunctionType type = this.ItemNBT_TypeCheck(currentItem.getItemMeta().getPersistentDataContainer().get(this.clickFunctionIcon, PersistentDataType.STRING));
            if(type == null) return;
            this.passClick(event);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (this.registry == null) return;
            this.registry.dispatch(type, event, topHolder, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();

        if (!(topInventory.getHolder() instanceof BaseConfigInventory holder)) return;

        GuiMeta annotation = holder.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        if (!annotation.type().equals(this.guiType.getType())) return;

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
    protected FunctionType ItemNBT_TypeCheck(String rawType) throws IllegalArgumentException {
        if(rawType == null) return null;
        return FunctionType.valueOf(rawType.toUpperCase());
    }

}
