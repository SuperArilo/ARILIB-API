package com.tty.api.gui;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.annotations.gui.GuiMeta;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class BaseInventory implements InventoryHolder {

    private final Object lock = new Object();

    private AbstractJavaPlugin plugin;
    private volatile Inventory inventory;

    @Getter
    private final Executor executorAsync;

    @Getter
    private final Executor executorSync;

    protected BaseInventory(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
        this.executorAsync = task -> this.getPlugin().getScheduler().runAsync(this.getPlugin(), t -> task.run());
        this.executorSync = task -> this.getPlugin().getScheduler().run(this.getPlugin(), t -> task.run());
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

    protected abstract CompletableFuture<Boolean> onClose();

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

    public void close() {
        this.onClose().thenComposeAsync(i -> {
            this.plugin.getLog().debug("inventory {} has been cleaned.", this.getType());
            return CompletableFuture.completedFuture(true);
        }, this.executorAsync).whenCompleteAsync((i, ex) -> {
            if (ex != null) {
                this.plugin.getLog().error(ex);
            }
            synchronized (this.lock) {
                this.inventory = null;
                this.plugin = null;
            }
        }, this.executorAsync);
    }

    protected AbstractJavaPlugin getPlugin() {
        return this.plugin;
    }

}
