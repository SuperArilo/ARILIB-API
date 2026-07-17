package com.tty.api;

import com.tty.api.enumType.NbtEnum;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
    public <P, C> C getNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<P, C> type) {
        return itemStack.getItemMeta().getPersistentDataContainer().get(this.build(key), type);
    }

    public <P, C> void setNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<P, C> type, @NotNull C value) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.getPersistentDataContainer().set(this.build(key), type, value);
        itemStack.setItemMeta(itemMeta);
    }

    public <P, C> void setNbt(NbtEnum key, @NotNull Player player, PersistentDataType<P, C> type, @NotNull C value) {
        player.getPersistentDataContainer().set(this.build(key), type, value);
    }

    public void removeNbt(NbtEnum key, @NotNull ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.getPersistentDataContainer().remove(this.build(key));
        itemStack.setItemMeta(itemMeta);
    }

    public void removeNbt(NbtEnum key, @NotNull Player player) {
        player.getPersistentDataContainer().remove(this.build(key));
    }

    public boolean hasNbt(NbtEnum key, @NotNull ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(this.build(key));
    }

    public <P, C> boolean hasNbt(NbtEnum key, @NotNull ItemStack itemStack, PersistentDataType<P, C> type) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(this.build(key), type);
    }

    public boolean hasNbt(NbtEnum key, @NotNull Player player) {
        return player.getPersistentDataContainer().has(this.build(key));
    }

    private NamespacedKey build(NbtEnum key) {
        return this.caches.computeIfAbsent(key, k -> new NamespacedKey(this.plugin, k.getKey()));
    }

}
