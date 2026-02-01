package com.tty.api.gui;

import com.tty.api.utils.ComponentUtils;
import com.tty.api.dto.gui.BaseMenu;
import com.tty.api.dto.gui.FunctionItems;
import com.tty.api.dto.gui.Mask;
import com.tty.api.enumType.FunctionType;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public abstract class BaseConfigInventory extends BaseInventory {

    @Getter
    private BaseMenu baseMenu;
    protected Player player;
    private JavaPlugin plugin;
    private NamespacedKey renderType;

    public BaseConfigInventory(JavaPlugin plugin, Player player) {
        this.baseMenu = this.config();
        this.plugin = plugin;
        this.player = player;
        this.renderType = new NamespacedKey(plugin, "type");
    }

    @Override
    protected void beforeCreate() {
        this.renderMasks();
        this.renderFunctionItems();
    }

    @Override
    protected @NotNull Component title() {
        return ComponentUtils.text(this.config().getTitle(), this.player);
    }

    @Override
    protected int size() {
        return this.config().getRow() * 9;
    }

    protected abstract @NotNull BaseMenu config();

    protected abstract Mask renderCustomMasks();

    protected abstract Map<String, FunctionItems> renderCustomFunctionItems();

    private void renderMasks() {
        long l = System.currentTimeMillis();
        Mask mask = this.renderCustomMasks();
        if (mask == null) {
            mask = this.config().getMask();
        }
        List<TextComponent> collect = mask.getLore().stream().map(ComponentUtils::text).toList();
        for (Integer i : mask.getSlot()) {
            ItemStack itemStack = ItemStack.of(Material.valueOf(mask.getMaterial().toUpperCase()));
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(ComponentUtils.text(mask.getName()));
            itemMeta.getPersistentDataContainer().set(this.renderType, PersistentDataType.STRING, FunctionType.MASK_ICON.name());
            itemMeta.lore(collect);
            itemStack.setItemMeta(itemMeta);
            this.inventory.setItem(i, itemStack);
        }
        this.getLog().debug("render masks time: {} ms. type: {}", (System.currentTimeMillis() - l), this.getType());
    }

    protected void renderFunctionItems() {
        long l = System.currentTimeMillis();
        Map<String, FunctionItems> functionItems = this.renderCustomFunctionItems();
        if (functionItems == null || functionItems.isEmpty()) {
            functionItems = this.config().getFunctionItems();
        }
        functionItems.forEach((k, v) -> {
            FunctionType functionType = v.getType();
            if (functionType == null) {
                this.getLog().error("render function item on {} error.", k);
                return;
            }
            ItemStack o = ItemStack.of(Material.valueOf(v.getMaterial().toUpperCase()));
            ItemMeta mo = o.getItemMeta();
            mo.displayName(ComponentUtils.text(v.getName()));
            mo.lore(v.getLore().stream().map(ComponentUtils::text).toList());
            mo.getPersistentDataContainer().set(this.renderType, PersistentDataType.STRING, functionType.name());
            o.setItemMeta(mo);
            for (Integer integer : v.getSlot()) {
                this.inventory.setItem(integer, o);
            }
        });
        this.getLog().debug("render function item time: {} ms. type: {}", (System.currentTimeMillis() - l), this.getType());
    }

    protected String replaceKey(String content, Map<String, String> map) {
        if (content == null || map == null || map.isEmpty()) {
            return content;
        }
        String result = content;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result = result.replace("<" + entry.getKey() + ">" , entry.getValue());
            }
        }
        return result;
    }

    /**
     * 给指定的 ItemStack 的 ItemMeta 设置 NBT
     * @param itemMeta ItemStack 的 ItemMeta
     * @param key key 名
     * @param type 类型
     * @param value 值
     * @param <T> 类型 T
     */
    protected <T> void setNBT(@NotNull ItemMeta itemMeta, String key, PersistentDataType<T, T> type, T value) {
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, key), type, value);
    }

    @Override
    public void clean() {
        this.baseMenu = null;
        this.player = null;
        this.renderType = null;
        this.plugin = null;
    }

}
