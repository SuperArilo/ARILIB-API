package com.tty.api.gui;

import com.tty.api.dto.PageResult;
import com.tty.api.dto.gui.BaseDataMenu;
import com.tty.api.dto.gui.PageDisable;
import com.tty.api.enumType.FunctionType;
import com.tty.api.Log;
import com.tty.api.service.ComponentService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class BaseDataItemConfigInventory<T> extends BaseConfigInventory {

    protected int pageNum = 1;
    protected List<T> data;

    protected PageResult<T> lastPageResult = null;

    protected volatile boolean loading = false;

    public BaseDataItemConfigInventory(JavaPlugin plugin, Player player, ComponentService service) {
        super(plugin, player, service);
    }

    /**
     * 上一页
     */
    public void prev() {
        if (this.loading) return;
        if (this.pageNum <= 1) {
            return;
        }
        this.pageNum--;
        this.renderFunctionItems();
        this.requestAndAccept(result -> {
            this.lastPageResult = result;
            this.data = result.records();
            this.renderDataItem();
            this.checkDisable(result);
        });
    }

    /**
     * 下一页
     */
    public void next() {
        if (this.loading) return;
        if (this.lastPageResult != null && this.pageNum >= this.lastPageResult.totalPages()) {
            return;
        }
        this.pageNum++;
        this.renderFunctionItems();
        this.requestAndAccept(result -> {
            if (result.totalPages() > 0 && this.pageNum > result.totalPages()) {
                this.pageNum = Math.toIntExact(result.totalPages());
                return;
            }
            this.lastPageResult = result;
            this.data = result.records();
            this.renderDataItem();
            this.checkDisable(result);
        });
    }

    @Override
    protected void beforeCreate() {
        super.beforeCreate();
        this.renderFunctionItems();
        this.requestAndAccept(result -> {
            this.lastPageResult = result;
            this.data = result.records();
            this.renderDataItem();
            this.checkDisable(result);
        });
    }

    protected abstract CompletableFuture<PageResult<T>> requestData();

    protected abstract Map<Integer, ItemStack> getRenderItem();

    private void requestAndAccept(Consumer<PageResult<T>> onSuccess) {
        CompletableFuture<PageResult<T>> future = this.requestData();
        if (future == null) {
            PageResult<T> empty = PageResult.build(List.of(), 0, 0, this.pageNum);
            this.lastPageResult = empty;
            onSuccess.accept(empty);
            Log.warn("{}: requestData returned null, using empty result", this.getType());
            return;
        }

        this.loading = true;
        future.thenAccept(result -> {
            try {
                if (result != null) this.lastPageResult = result;
                onSuccess.accept(result != null ? result : PageResult.build(List.of(), 0, 0, this.pageNum));
            } catch (Exception e) {
                Log.error(e, "{}: processing request result error!", this.getType());
            } finally {
                this.loading = false;
            }
        }).exceptionally(ex -> {
            Log.error(ex, "{}: request data error!", this.getType());
            this.loading = false;
            return null;
        });
    }

    private void renderDataItem() {
        if (this.inventory == null) return;
        long l = System.currentTimeMillis();
        Map<Integer, ItemStack> renderItem;
        try {
            renderItem = this.getRenderItem();
        } catch (Exception e) {
            Log.error("get render item error.", e);
            return;
        }
        if (renderItem == null || renderItem.isEmpty()) return;
        BaseDataMenu baseDataMenu = (BaseDataMenu) this.config();
        for (Integer index : baseDataMenu.getDataItems().getSlot()) {
            if (this.inventory == null) return;
            // 先清除位置
            this.inventory.clear(index);
            if (renderItem.containsKey(index)) {
                this.inventory.setItem(index, renderItem.get(index));
            }
        }
        Log.debug("{}: submit render task time: {}ms", this.getType(), (System.currentTimeMillis() - l));
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
            Log.error(e, "create ItemStack error. material {}", showMaterial);
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
    public void clean() {
        this.data = null;
        this.lastPageResult = null;
    }

    /**
     * 检查并设置翻页按钮的禁用状态
     */
    private void checkDisable(PageResult<T> result) {
        this.renderFunctionItems();
        long totalPages = result.totalPages();
        if (totalPages <= 1) {
            this.setPrevDisable();
            this.setNextDisable();
        } else {
            if (this.pageNum <= 1) {
                this.setPrevDisable();
            }
            if (this.pageNum >= totalPages) {
                this.setNextDisable();
            }
        }
    }

    private void setPrevDisable() {
        BaseDataMenu baseDataMenu = (BaseDataMenu) this.config();
        baseDataMenu.getFunctionItems().forEach(((k, v) -> {
            if (v.getType().equals(FunctionType.PREV)) {
                this.setDisablePageFunction(v.getSlot());
            }
        }));
    }

    private void setNextDisable() {
        BaseDataMenu baseDataMenu = (BaseDataMenu) this.config();
        baseDataMenu.getFunctionItems().forEach(((k, v) -> {
            if (v.getType().equals(FunctionType.NEXT)) {
                this.setDisablePageFunction(v.getSlot());
            }
        }));
    }

    private void setDisablePageFunction(List<Integer> slots) {
        BaseDataMenu baseDataMenu = (BaseDataMenu) this.config();
        PageDisable pageDisable = baseDataMenu.getPageDisable();
        if (pageDisable == null) {
            Log.warn("pageDisable config is null, cannot disable page button");
            return;
        }
        try {
            ItemStack itemStack = ItemStack.of(Material.valueOf(pageDisable.getMaterial()));
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(this.componentService.text(pageDisable.getName()));
            itemStack.setItemMeta(itemMeta);
            for (Integer slot : slots) {
                if (this.inventory != null) {
                    this.inventory.setItem(slot, itemStack);
                }
            }
        } catch (IllegalArgumentException e) {
            Log.error(e, "Invalid material for pageDisable: {}", pageDisable.getMaterial());
        }
    }

}