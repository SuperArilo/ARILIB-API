package com.tty.api.gui;

import com.tty.api.dto.gui.BaseMenu;
import com.tty.api.dto.gui.FunctionItems;
import com.tty.api.dto.gui.Mask;
import com.tty.api.enumType.FunctionType;
import com.tty.api.service.ComponentService;
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
    protected ComponentService componentService;

    public BaseConfigInventory(JavaPlugin plugin, Player player, ComponentService componentService) {
        this.baseMenu = this.config();
        this.plugin = plugin;
        this.player = player;
        this.renderType = new NamespacedKey(plugin, "type");
        this.componentService = componentService;
    }

    @Override
    protected void beforeCreate() {
        this.renderMasks();
        this.renderFunctionItems();
    }

    @Override
    protected @NotNull Component title() {
        return this.componentService.text(this.config().getTitle(), this.player);
    }

    @Override
    protected int size() {
        return this.config().getRow() * 9;
    }

    protected abstract @NotNull BaseMenu config();

    protected abstract Mask renderCustomMasks();

    protected abstract Map<String, FunctionItems> renderCustomFunctionItems();

    private void renderMasks() {
        Mask mask = this.renderCustomMasks();
        if (mask == null) {
            mask = this.config().getMask();
        }
        List<TextComponent> collect = mask.getLore().stream().map(this.componentService::text).toList();
        for (Integer i : mask.getSlot()) {
            ItemStack itemStack = ItemStack.of(Material.valueOf(mask.getMaterial().toUpperCase()));
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(this.componentService.text(mask.getName()));
            itemMeta.getPersistentDataContainer().set(this.renderType, PersistentDataType.STRING, FunctionType.MASK_ICON.name());
            itemMeta.lore(collect);
            itemStack.setItemMeta(itemMeta);
            this.inventory.setItem(i, itemStack);
        }
    }

    protected void renderFunctionItems() {
        Map<String, FunctionItems> functionItems = this.renderCustomFunctionItems();
        if (functionItems == null || functionItems.isEmpty()) {
            functionItems = this.config().getFunctionItems();
        }
        functionItems.forEach((k, v) -> {
            ItemStack o = ItemStack.of(Material.valueOf(v.getMaterial().toUpperCase()));
            ItemMeta mo = o.getItemMeta();
            mo.displayName(this.componentService.text(v.getName()));
            mo.lore(v.getLore().stream().map(this.componentService::text).toList());
            mo.getPersistentDataContainer().set(this.renderType, PersistentDataType.STRING, v.getType().name());
            o.setItemMeta(mo);
            for (Integer integer : v.getSlot()) {
                this.inventory.setItem(integer, o);
            }
        });
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
        this.componentService = null;
    }
}
