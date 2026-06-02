package com.tty.api.listener;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.annotations.function_type.FunctionHandler;
import com.tty.api.annotations.gui.GuiMeta;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import com.tty.api.gui.BaseInventory;
import com.tty.api.utils.GuiNBTKeys;
import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGuiListener<T extends BaseInventory> implements Listener {

    @Getter
    private final NamespacedKey FunctionIconNamespacedKey;
    private final AbstractJavaPlugin plugin;
    protected final GuiKeyEnum guiType;

    private FunctionHandler<T> functionHandler;

    protected BaseGuiListener(@NotNull AbstractJavaPlugin plugin, @NotNull GuiKeyEnum guiType) {
        this.guiType = guiType;
        this.plugin = plugin;
        this.FunctionIconNamespacedKey = new NamespacedKey(this.plugin, GuiNBTKeys.GUI_RENDER_FUNCTION_ICON);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        InventoryHolder topHolder = topInventory.getHolder();
        InventoryHolder clickedHolder = clickedInventory.getHolder();
        if (topHolder == null || clickedHolder == null) return;

        if (!(topHolder instanceof BaseInventory)) return;

        GuiMeta annotation = topHolder.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        boolean isCustomGui = annotation.type().equals(this.guiType.getType());
        if (!isCustomGui) return;

        T holder = (T) topHolder;

        if (event.isShiftClick() && !clickedInventory.equals(topInventory)) {
            this.whenShiftClick(event, holder);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && clickedInventory.equals(topInventory)) {
            this.whenDoubleClick(event, holder);
            return;
        }

        if (!clickedInventory.equals(topInventory)) return;

        this.whenClick(event, holder);

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || event.isShiftClick()) return;

        ItemMeta meta = currentItem.getItemMeta();
        if (meta == null) return;

        FunctionType type = this.ItemNBT_TypeCheck(meta.getPersistentDataContainer().get(this.FunctionIconNamespacedKey, PersistentDataType.STRING));
        if (type == null) return;

        this.passClick(event);

        if (event.getWhoClicked() instanceof Player player) {
            if (this.functionHandler == null) {
                this.functionHandler = this.registry();
            }
            this.functionHandler.dispatch(type, event, (T) topHolder, player);
        }

    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();

        if (!(topInventory.getHolder() instanceof BaseInventory holder)) return;

        GuiMeta annotation = holder.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) return;
        if (!annotation.type().equals(this.guiType.getType())) return;

        int topSize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                this.whenDrag(event, (T) holder);
                break;
            }
        }
    }

    @NotNull protected abstract FunctionHandler<T> registry();

    /**
     * 自定义是否取消掉点击事件
     *
     * @param event  点击事件
     * @param holder holder
     */
    protected abstract void whenClick(InventoryClickEvent event, T holder);

    protected abstract void whenDoubleClick(InventoryClickEvent event, T holder);

    protected abstract void whenShiftClick(InventoryClickEvent event, T holder);

    protected abstract void whenDrag(InventoryDragEvent event, T holder);

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
        if (rawType == null) return null;
        try {
            return FunctionType.valueOf(rawType.toUpperCase());
        } catch (Exception e) {
            this.plugin.getLog().warn(e, "Invalid FunctionType found in GUI item NBT: {}", rawType);
        }
        return null;
    }
}