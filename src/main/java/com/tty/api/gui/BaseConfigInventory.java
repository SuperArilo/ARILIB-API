package com.tty.api.gui;

import com.tty.api.BaseJavaPlugin;
import com.tty.api.dto.gui.BaseMenu;
import com.tty.api.dto.gui.FunctionItems;
import com.tty.api.dto.gui.Mask;
import com.tty.api.enumType.FunctionType;
import com.tty.api.utils.ComponentUtils;
import com.tty.api.utils.GuiNBTKeys;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseConfigInventory extends BaseInventory {

    @Getter
    private BaseMenu baseMenu;
    protected Player player;

    private final NamespacedKey GUI_RENDER_MASK_KEY;
    private final NamespacedKey GUI_RENDER_FUNCTION_ICON_KEY;

    private static final Pattern PLACEHOLDER_PATTERN = java.util.regex.Pattern.compile("<([^>]+)>");

    public BaseConfigInventory(BaseJavaPlugin plugin, Player player) {
        super(plugin);
        this.baseMenu = this.config();
        this.player = player;
        this.GUI_RENDER_MASK_KEY = new NamespacedKey(this.getBaseJavaPlugin(), GuiNBTKeys.GUI_RENDER_MASK);
        this.GUI_RENDER_FUNCTION_ICON_KEY = new NamespacedKey(this.getBaseJavaPlugin(), GuiNBTKeys.GUI_RENDER_FUNCTION_ICON);
    }

    @Override
    protected void afterCreatedInventory(@NotNull Inventory inventory) {
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
            itemMeta.getPersistentDataContainer().set(GUI_RENDER_MASK_KEY, PersistentDataType.STRING, FunctionType.MASK_ICON.name());
            itemMeta.lore(collect);
            itemStack.setItemMeta(itemMeta);
            this.getInventory().setItem(i, itemStack);
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
            mo.getPersistentDataContainer().set(GUI_RENDER_FUNCTION_ICON_KEY, PersistentDataType.STRING, functionType.name());
            o.setItemMeta(mo);
            for (Integer integer : v.getSlot()) {
                this.getInventory().setItem(integer, o);
            }
        });
        this.getLog().debug("render function item time: {} ms. type: {}", (System.currentTimeMillis() - l), this.getType());
    }

    protected String replaceKey(String content, Map<String, String> map) {

        if (content == null || map == null || map.isEmpty()) return content;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = map.get(key);
            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        if (this.getBaseJavaPlugin() == null) {
            this.getLog().debug("plugin in inventory is null, cannot set NBT for key: {}", key);
            return;
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(this.getBaseJavaPlugin(), key), type, value);
    }

    @Override
    protected void clean() {
        this.baseMenu = null;
        this.player = null;
    }

}
