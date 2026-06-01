package com.tty.api.gui;

import com.tty.api.AbstractJavaPlugin;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseConfigInventory extends BaseInventory {

    @Getter
    private BaseMenu baseMenu;

    @Getter
    private OfflinePlayer offlinePlayer;

    private final NamespacedKey GUI_RENDER_MASK_KEY;
    private final NamespacedKey GUI_RENDER_FUNCTION_ICON_KEY;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<([^>]+)>");

    public BaseConfigInventory(AbstractJavaPlugin plugin, OfflinePlayer offlinePlayer) {
        super(plugin);
        this.baseMenu = this.config();
        this.offlinePlayer = offlinePlayer;
        this.GUI_RENDER_MASK_KEY = new NamespacedKey(this.getPlugin(), GuiNBTKeys.GUI_RENDER_MASK);
        this.GUI_RENDER_FUNCTION_ICON_KEY = new NamespacedKey(this.getPlugin(), GuiNBTKeys.GUI_RENDER_FUNCTION_ICON);
    }

    @Override
    protected @NotNull Component title() {
        String title = this.config().getTitle();
        return ComponentUtils.text(title, this.offlinePlayer);
    }

    @Override
    protected int size() {
        return this.config().getRow() * 9;
    }

    protected abstract @NotNull BaseMenu config();

    @Override
    protected void afterCreatedInventory(@NotNull Inventory inventory) {

        Executor syncExecutor = task -> this.getPlugin().getScheduler().runAsync(this.getPlugin(), t -> task.run());

        this.beforeRenderMasks(this.baseMenu.getMask())
                .thenCombineAsync(
                        this.beforeRenderFunctionItems(this.baseMenu.getFunctionItems()),
                        (mask, items) -> {
                            this.renderMasks(mask);
                            this.renderFunctionItems(items);
                            return CompletableFuture.completedFuture(null);
                        },
                        syncExecutor)
                .thenAcceptAsync(Void -> this.whenRenderComplete(inventory), syncExecutor)
                .exceptionally(throwable -> {
                    this.getLog().warn("GUI async render failed", throwable);
                    syncExecutor.execute(() -> this.whenRenderComplete(inventory));
                    return null;
                });
    }

    protected abstract @NotNull CompletableFuture<Mask> beforeRenderMasks(@Nullable Mask mask);

    protected abstract @NotNull CompletableFuture< Map<String, FunctionItems>> beforeRenderFunctionItems(@Nullable Map<String, FunctionItems> functionItems);

    /**
     * 所有渲染完成后
     * @param inventory inventory 对象
     */
    protected abstract void whenRenderComplete(@NotNull Inventory inventory);

    private void renderMasks(@Nullable Mask mask) {
        long l = System.currentTimeMillis();
        if (mask == null) {
            mask = this.config().getMask();
        }
        if (mask == null) return;
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

    private void renderFunctionItems(@Nullable Map<String, FunctionItems> functionItems) {
        if (functionItems == null) return;
        long l = System.currentTimeMillis();
        for (FunctionItems value : functionItems.values()) {
            FunctionType functionType = value.getType();
            if (functionType == null) return;

            //判断字段 item stack 是否存在
            ItemStack prvateItemStack = value.getItemStack();
            if (prvateItemStack == null) {
                ItemStack o = ItemStack.of(Material.valueOf(value.getMaterial().toUpperCase()));
                ItemMeta mo = o.getItemMeta();

                String name = value.getName();
                if (name != null) {
                    mo.displayName(ComponentUtils.text(name));
                } else if (mo instanceof SkullMeta skullMeta) {
                    skullMeta.setPlayerProfile(this.offlinePlayer.getPlayerProfile());
                    skullMeta.displayName(ComponentUtils.text(this.offlinePlayer.getName()));
                }
                mo.lore(value.getLore().stream().map(ComponentUtils::text).toList());
                mo.getPersistentDataContainer().set(GUI_RENDER_FUNCTION_ICON_KEY, PersistentDataType.STRING, functionType.name());

                o.setItemMeta(mo);
                for (Integer integer : value.getSlot()) {
                    this.getInventory().setItem(integer, o);
                }
            } else {
                ItemMeta itemMeta = prvateItemStack.getItemMeta();
                itemMeta.getPersistentDataContainer().set(GUI_RENDER_FUNCTION_ICON_KEY, PersistentDataType.STRING, functionType.name());
                prvateItemStack.setItemMeta(itemMeta);
                for (Integer integer : value.getSlot()) {
                    this.getInventory().setItem(integer, prvateItemStack);
                }
            }
        }
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
        if (this.getPlugin() == null) {
            this.getLog().debug("plugin in inventory is null, cannot set NBT for key: {}", key);
            return;
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(this.getPlugin(), key), type, value);
    }

    @Override
    protected void clean() {
        this.baseMenu = null;
        this.offlinePlayer = null;
    }

}
