package com.tty.api;

import com.tty.api.enumType.NbtEnum;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NbtManager {

    private final AbstractJavaPlugin plugin;

    private final Map<NbtEnum, NamespacedKey> caches = new ConcurrentHashMap<>();

    public NbtManager(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    public <T> T getNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<T, T> type) {
        return itemStack.getItemMeta().getPersistentDataContainer().get(this.build(key), type);
    }

    public <T> void setNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<T, T> type, @NotNull T value) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.getPersistentDataContainer().set(this.build(key), type, value);
        itemStack.setItemMeta(itemMeta);
    }

    public void removeNbt(NbtEnum key, @NotNull ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.getPersistentDataContainer().remove(this.build(key));
        itemStack.setItemMeta(itemMeta);
    }

    public boolean hasNbt(NbtEnum key, @NotNull ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(this.build(key));
    }

    public <T> boolean hasNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<T, T> type) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(this.build(key), type);
    }

    private NamespacedKey build(NbtEnum key) {
        return this.caches.computeIfAbsent(key, k -> new NamespacedKey(this.plugin, k.getKey()));
    }

}
