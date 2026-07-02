package com.tty.api.gui;

import com.google.common.reflect.TypeToken;
import com.tty.api.AbstractJavaPlugin;
import com.tty.api.dto.gui.BaseMenu;
import com.tty.api.dto.gui.FunctionItems;
import com.tty.api.dto.gui.Mask;
import com.tty.api.enumType.FunctionType;
import com.tty.api.enumType.IconKeyType;
import com.tty.api.enumType.NbtGuiValue;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseConfigInventory extends BaseInventory {

    @Getter
    private final BaseMenu baseMenu;

    @Getter
    private final OfflinePlayer offlinePlayer;

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<([^>]+)>");

    public BaseConfigInventory(AbstractJavaPlugin plugin, OfflinePlayer offlinePlayer) {
        super(plugin);
        this.baseMenu = this.config();
        this.offlinePlayer = offlinePlayer;
    }

    @Override
    protected @NotNull Component title() {
        String title = this.config().getTitle();
        return this.getPlugin().getComponentTool().text(title, this.offlinePlayer);
    }

    @Override
    protected int size() {
        return this.config().getRow() * 9;
    }

    protected abstract @NotNull BaseMenu config();

    @Override
    protected void afterCreatedInventory(@NotNull Inventory inventory) {
        CompletableFuture.supplyAsync(() -> {
            Mask mask = this.getPlugin().getConfigurationManager().deepCopy(this.baseMenu.getMask(), Mask.class);
            this.beforeRenderMasksAsync(mask);
            return mask;
        }, this.getPlugin().getExecutorAsync())
        .thenComposeAsync(i -> {
            this.renderMasks(i);
            return CompletableFuture.completedFuture(true);
        }, this.getPlugin().getExecutorSync())
        .thenComposeAsync(i -> {
            Map<String, FunctionItems> copy = this.getPlugin().getConfigurationManager().deepCopy(this.baseMenu.getFunctionItems(), new TypeToken<Map<String, FunctionItems>>() {}.getType());
            this.beforeRenderFunctionItemsAsync(copy);
            return CompletableFuture.completedFuture(copy);
        }, this.getPlugin().getExecutorAsync())
        .thenComposeAsync(i -> {
            this.renderFunctionItems(i);
            return CompletableFuture.completedFuture(true);
        }, this.getPlugin().getExecutorSync())
        .thenAcceptAsync(Void -> this.whenRenderComplete(inventory), this.getPlugin().getExecutorAsync())
        .exceptionallyAsync(throwable -> {
            this.getPlugin().getLog().warn(throwable, "GUI async render failed");
            this.getInventory().close();
            return null;
        }, this.getPlugin().getExecutorSync());
    }

    protected abstract void beforeRenderMasksAsync(@Nullable Mask mask);

    protected abstract void beforeRenderFunctionItemsAsync(@Nullable Map<String, FunctionItems> functionItems);

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
        List<TextComponent> collect = mask.getLore().stream().map(i -> this.getPlugin().getComponentTool().text(i)).toList();

        for (Integer i : mask.getSlot()) {
            ItemStack itemStack = ItemStack.of(Material.valueOf(mask.getMaterial().toUpperCase()));
            this.getPlugin().getNbtManager().setNbt(NbtGuiValue.GUI_MASK_ICON, itemStack, PersistentDataType.STRING, FunctionType.MASK_ICON.getName());

            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(this.getPlugin().getComponentTool().text(mask.getName(), this.getOfflinePlayer()));
            itemMeta.lore(collect);
            itemStack.setItemMeta(itemMeta);
            this.getInventory().setItem(i, itemStack);
        }
        this.getPlugin().getLog().debug("render masks time: {} ms. type: {}", (System.currentTimeMillis() - l), this.getType());
    }

    private void renderFunctionItems(@Nullable Map<String, FunctionItems> functionItems) {
        if (functionItems == null) return;
        long l = System.currentTimeMillis();
        for (FunctionItems value : functionItems.values()) {
            FunctionType functionType = value.getType();
            if (functionType == null) continue;

            //判断字段 item stack 是否存在
            ItemStack prvateItemStack = value.getItemStack();
            if (prvateItemStack == null) {
                ItemStack o = ItemStack.of(Material.valueOf(value.getMaterial().toUpperCase()));
                this.getPlugin().getNbtManager().setNbt(NbtGuiValue.GUI_FUNCTION_ICON, o, PersistentDataType.STRING, functionType.getName());

                ItemMeta mo = o.getItemMeta();
                String name = value.getName();
                if (name != null) {
                    mo.displayName(this.getPlugin().getComponentTool().text(name, this.getOfflinePlayer()));
                }
                mo.lore(value.getLore().stream().map(i -> this.getPlugin().getComponentTool().text(i, this.getOfflinePlayer())).toList());
                o.setItemMeta(mo);
                for (Integer integer : value.getSlot()) {
                    this.getInventory().setItem(integer, o);
                }
            } else {
                this.getPlugin().getNbtManager().setNbt(NbtGuiValue.GUI_FUNCTION_ICON, prvateItemStack, PersistentDataType.STRING, functionType.getName());
                ItemMeta itemMeta = prvateItemStack.getItemMeta();
                prvateItemStack.setItemMeta(itemMeta);
                for (Integer integer : value.getSlot()) {
                    this.getInventory().setItem(integer, prvateItemStack);
                }
            }
        }
        this.getPlugin().getLog().debug("render function item time: {} ms. type: {}", (System.currentTimeMillis() - l), this.getType());
    }

    protected String replaceKey(String content, Map<IconKeyType, String> map) {

        if (content == null || map == null || map.isEmpty()) return content;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String replacement = map.get(IconKeyType.fromKey(matcher.group(1)));
            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
