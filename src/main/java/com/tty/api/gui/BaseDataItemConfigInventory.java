package com.tty.api.gui;

import com.tty.api.AbstractJavaPlugin;
import com.tty.api.utils.ComponentUtils;
import com.tty.api.dto.PageResult;
import com.tty.api.dto.gui.BaseDataMenu;
import com.tty.api.dto.gui.PageDisable;
import com.tty.api.enumType.FunctionType;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class BaseDataItemConfigInventory<T> extends BaseConfigInventory {

    protected int pageNum = 1;
    protected PageResult<T> lastPageResult;

    protected volatile boolean loading = false;

    private List<Integer> prevSlots = List.of();
    private List<Integer> nextSlots = List.of();

    private ItemStack prevOrigin;
    private ItemStack nextOrigin;

    private volatile CompletableFuture<PageResult<T>> currentRequest;

    public BaseDataItemConfigInventory(AbstractJavaPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected void whenRenderComplete(@NotNull Inventory inventory) {
        this.cachePageButtons();
        this.requestAndAccept(this::applyPageResult);
    }

    private void cachePageButtons() {
        BaseDataMenu menu = (BaseDataMenu) config();
        menu.getFunctionItems().forEach((k, v) -> {
            if (v.getType() == FunctionType.PREV_PAGE) {
                this.prevSlots = v.getSlot();
                this.prevOrigin = this.cloneFromInventory(this.prevSlots);
            }
            if (v.getType() == FunctionType.NEXT_PAGE) {
                this.nextSlots = v.getSlot();
                this.nextOrigin = this.cloneFromInventory(this.nextSlots);
            }
        });
    }

    private ItemStack cloneFromInventory(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) return null;
        ItemStack item = this.getInventory().getItem(slots.getFirst());
        return item == null ? null : item.clone();
    }

    public void prev() {
        if (this.loading || this.pageNum <= 1) return;
        this.pageNum--;
        this.requestAndAccept(this::applyPageResult);
    }

    public void next() {
        if (this.loading) return;
        if (this.lastPageResult != null && this.pageNum >= this.lastPageResult.totalPages()) return;
        this.pageNum++;
        this.requestAndAccept(this::applyPageResult);
    }

    protected abstract CompletableFuture<PageResult<T>> requestData();

    @NotNull
    protected abstract List<ItemStack> beforeRenderDataItem(List<T> data);

    private void requestAndAccept(Consumer<PageResult<T>> consumer) {
        CompletableFuture<PageResult<T>> future = this.requestData();
        if (future == null) {
            consumer.accept(PageResult.build(List.of(), 0, 0, this.pageNum));
            return;
        }
        if (this.currentRequest == future) return;
        this.currentRequest = future;
        this.loading = true;
        future.thenAccept(result -> {
            if (future.isCancelled()) {
                this.loading = false;
                return;
            }
            try {
                consumer.accept(result);
            } finally {
                this.loading = false;
            }
        }).exceptionally(ex -> {
            if (!future.isCancelled()) {
                this.getPlugin().getLog().error(ex, "request data error");
            }
            this.loading = false;
            return null;
        });
    }

    private void applyPageResult(PageResult<T> result) {
        this.lastPageResult = result;
        this.renderDataItem(this.beforeRenderDataItem(result.records()));
        this.updatePageButtons(result);
    }

    private void renderDataItem(List<ItemStack> list) {
        long l = System.currentTimeMillis();
        List<Integer> slotList = ((BaseDataMenu) config()).getDataItems().getSlot();
        if (list.size() > slotList.size()) throw new IllegalArgumentException("");

        for (int i = 0; i < slotList.size(); i++) {
            Integer slot = slotList.get(i);
            this.getInventory().clear(slot);
            if (i < list.size()) {
                ItemStack item = list.get(i);
                if (item != null) {
                    this.getInventory().setItem(slot, item);
                }
            }
        }
        this.getPlugin().getLog().debug("render data item time: {} ms, type: {}", (System.currentTimeMillis() -l), this.getType());
    }

    private void updatePageButtons(PageResult<T> result) {
        long total = result.totalPages();

        boolean disablePrev = this.pageNum <= 1;
        boolean disableNext = total <= 1 || this.pageNum >= total;

        this.updateFunction(this.prevSlots, this.prevOrigin, disablePrev);
        this.updateFunction(this.nextSlots, this.nextOrigin, disableNext);
    }

    private void updateFunction(List<Integer> slots, ItemStack origin, boolean disable) {
        if (slots == null || slots.isEmpty()) return;
        if (disable) {
            this.setDisablePageFunction(slots);
        } else if (origin != null) {
            for (Integer slot : slots) {
                this.getInventory().setItem(slot, origin.clone());
            }
        }
    }

    /**
     * 根据材质字符串来创建对于的 ItemStack
     * @param showMaterial 材质字符串
     * @return ItemStack 如果不存在则是 null
     */
    protected ItemStack createItemStack(@Nullable String showMaterial) {
        if (showMaterial == null) return null;
        ItemStack itemStack;
        try {
            itemStack = ItemStack.of(Material.valueOf(showMaterial.toUpperCase()));
            return itemStack;
        } catch (Exception e) {
            this.getPlugin().getLog().error(e, "create ItemStack error. material {}", showMaterial);
            return null;
        }
    }

    /**
     * 将指定的 ItemStack 的 ItemMeta 设置为高亮模式
     * @param itemMeta ItemStack 的 ItemMeta
     */
    protected void setHighlight(@NotNull ItemMeta itemMeta) {
        itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    @Override
    protected CompletableFuture<Boolean> onClose() {
        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture<PageResult<T>> req = this.currentRequest;
            if (req != null && !req.isDone()) {
                req.cancel(true);
            }
            this.currentRequest = null;
            this.lastPageResult = null;
            this.prevOrigin = null;
            this.nextOrigin = null;
            this.prevSlots = null;
            this.nextSlots = null;
            return true;
        }, this.getExecutor());
    }

    private void setDisablePageFunction(List<Integer> slots) {
        BaseDataMenu baseDataMenu = (BaseDataMenu) this.config();
        PageDisable pageDisable = baseDataMenu.getPageDisable();
        if (pageDisable == null) {
            this.getPlugin().getLog().warn("pageDisable config is null, cannot disable page button");
            return;
        }
        try {
            ItemStack itemStack = ItemStack.of(Material.valueOf(pageDisable.getMaterial().toUpperCase()));
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(ComponentUtils.text(pageDisable.getName()));
            itemStack.setItemMeta(itemMeta);
            for (Integer slot : slots) {
                this.getInventory().setItem(slot, itemStack);
            }
        } catch (IllegalArgumentException e) {
            this.getPlugin().getLog().error(e, "Invalid material for pageDisable: {}", pageDisable.getMaterial());
        }
    }

}