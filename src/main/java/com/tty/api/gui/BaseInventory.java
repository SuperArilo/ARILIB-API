package com.tty.api.gui;

import com.tty.api.Log;
import com.tty.api.annotations.gui.GuiMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class BaseInventory implements InventoryHolder {

    private static final Log log = Log.create();
    protected Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        if (this.size() < 9) {
            throw new IllegalArgumentException("inventory size must be >= 9");
        }
        this.inventory = Bukkit.createInventory(this, this.size(), this.title());
        this.beforeCreate();
        return this.inventory;
    }

    protected abstract int size();

    protected abstract @NotNull Component title();

    protected abstract void beforeCreate();

    public String getType() {
        GuiMeta annotation = this.getClass().getAnnotation(GuiMeta.class);
        if (annotation == null) {
            throw new NullPointerException("gui type is null");
        }
        return annotation.type();
    }

    public abstract void clean();

    public void cleanup() {
        if (this.inventory != null) {
            this.inventory.clear();
        }
        this.inventory = null;
        this.clean();
    }

    protected Log getLog() {
        return log;
    }

    protected void debug(boolean debug) {
        log.setDebug(debug);
    }

}
