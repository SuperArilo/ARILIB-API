package com.tty.api.listener;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.annotations.function_type.FunctionHandler;
import com.tty.api.annotations.gui.GuiMeta;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.GuiKeyEnum;
import com.tty.api.enumType.NbtGuiValue;
import com.tty.api.gui.AsyncGuiClick;
import com.tty.api.gui.BaseInventory;
import lombok.Getter;
import net.kyori.adventure.text.Component;
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

import java.util.*;

@SuppressWarnings("unchecked")
public abstract class BaseGuiListener<T extends BaseInventory> implements Listener {

    private final AbstractJavaPlugin plugin;
    @Getter
    private final GuiKeyEnum guiType;

    private FunctionHandler<T> functionHandler;

    protected BaseGuiListener(@NotNull AbstractJavaPlugin plugin, @NotNull GuiKeyEnum guiType) {
        this.guiType = guiType;
        this.plugin = plugin;
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
        if (annotation == null || !annotation.type().equals(this.guiType.getType())) return;

        T holder = (T) topHolder;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick() && !clickedInventory.equals(topInventory)) {
            this.whenShiftClick(event, holder);
            return;
        }

        if (!clickedInventory.equals(topInventory)) return;

        this.whenClick(event, holder);

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || event.isShiftClick()) return;

        ItemMeta itemMeta = currentItem.getItemMeta();
        if (itemMeta == null) return;

        FunctionType type = this.ItemNBT_TypeCheck(this.plugin.getNbtManager().getNbt(NbtGuiValue.GUI_FUNCTION_ICON, currentItem, PersistentDataType.STRING));
        if (type == null) return;

        if (event.getWhoClicked() instanceof Player player) {
            if (this.functionHandler == null) {
                this.functionHandler = this.registry();
            }
            int slot = event.getRawSlot();
            Inventory topInv = event.getView().getTopInventory();

            if (type.equals(FunctionType.SAVE)) {
                this.functionHandler.dispatch(type, event, (T) topHolder, player,
                    () -> {
                        if (!(this instanceof AsyncGuiClick click)) return;
                        ItemStack item = topInv.getItem(slot);
                        if (item == null || item.getType().isAir()) return;
                        ItemMeta meta = item.getItemMeta();
                        List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
                        lore.removeIf(c -> c.equals(click.whenPending()) || c.equals(click.whenDone()) || c.equals(click.whenError()));
                        lore.add(click.whenPending());
                        meta.lore(lore);
                        item.setItemMeta(meta);
                        topInv.setItem(slot, item);
                    },
                    () -> {
                        if (!(this instanceof AsyncGuiClick click)) return;
                        ItemStack item = topInv.getItem(slot);
                        if (item == null || item.getType().isAir()) return;
                        ItemMeta meta = item.getItemMeta();
                        List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
                        lore.removeIf(c -> c.equals(click.whenPending()) || c.equals(click.whenError()));
                        lore.add(click.whenDone());
                        meta.lore(lore);
                        item.setItemMeta(meta);
                        topInv.setItem(slot, item);

                        plugin.getScheduler().runLater(t -> {
                            ItemStack laterItem = topInv.getItem(slot);
                            if (laterItem == null || laterItem.getType().isAir()) return;
                            ItemMeta laterMeta = laterItem.getItemMeta();
                            List<Component> laterLore = laterMeta.hasLore() ? new ArrayList<>(Objects.requireNonNull(laterMeta.lore())) : new ArrayList<>();
                            if (!laterLore.removeIf(c -> c.equals(click.whenDone()))) {
                                t.cancel();
                                return;
                            }
                            laterMeta.lore(laterLore);
                            laterItem.setItemMeta(laterMeta);
                            topInv.setItem(slot, laterItem);
                        }, 20L);
                    },
                    () -> {
                        if (!(this instanceof AsyncGuiClick click)) return;
                        plugin.getScheduler().run(i -> {
                            ItemStack item = topInv.getItem(slot);
                            if (item == null || item.getType().isAir()) return;
                            ItemMeta meta = item.getItemMeta();
                            List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
                            lore.removeIf(c -> c.equals(click.whenPending()) || c.equals(click.whenDone()));
                            lore.add(click.whenError());
                            meta.lore(lore);
                            item.setItemMeta(meta);
                            topInv.setItem(slot, item);
                        });
                    }
                );
            } else {
                this.functionHandler.dispatch(type, event, (T) topHolder, player, null, null, null);
            }
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

    protected abstract void whenShiftClick(InventoryClickEvent event, T holder);

    protected abstract void whenDrag(InventoryDragEvent event, T holder);

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