package com.tty.api.gui;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.Log;
import com.tty.api.annotations.gui.GuiMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class BaseInventory implements InventoryHolder {

    private final Object lock = new Object();

    private AbstractJavaPlugin plugin;
    private Inventory inventory;

    protected BaseInventory(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (this.inventory != null) return this.inventory;
        synchronized (this.lock) {
            if (this.inventory != null) return this.inventory;
            if (this.size() < 9) {
                throw new IllegalArgumentException("inventory size must be >= 9");
            }
            this.inventory = Bukkit.createInventory(this, this.size(), this.title());
            this.afterCreatedInventory(this.inventory);
            return this.inventory;
        }
    }

    /**
     * 获取当前创建的 gui 的 slot 数量
     * @return slot 数量
     */
    protected abstract int size();

    /**
     * 获取当前创建的 gui 的标题
     * @return 标题
     */
    protected abstract @NotNull Component title();

    /**
     * 第一次创建 inventory 的时候会执行，如果已经创建后再调用则不会执行
     * @param inventory 创建的 inventory
     */
    protected abstract void afterCreatedInventory(@NotNull Inventory inventory);

    /**
     * gui 清理钩子函数
     */
    protected abstract void clean();

    /**
     * 返回当前创建的 gui type 类型
     * @return gui 类型，如果有
     */
    public String getType() {
        GuiMeta annotation = this.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) {
            throw new IllegalStateException("gui type is null");
        }
        return annotation.type();
    }

    public void cleanup() {
        synchronized (this.lock) {
            if (this.inventory != null) {
                this.inventory.clear();
            }
            this.inventory = null;
        }
        this.plugin = null;
        this.clean();
    }

    protected Log getLog() {
        return this.plugin.getLog();
    }

    protected AbstractJavaPlugin getBaseJavaPlugin() {
        return this.plugin;
    }

}
