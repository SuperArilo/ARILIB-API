package com.tty.api.dto.gui;

import com.google.gson.annotations.Expose;
import lombok.Data;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class BaseItem implements Serializable {
    private String name;
    @Nullable
    @Expose
    /* 直接设置 item，不会被 yml 解析 */
    private ItemStack itemStack;
    private String material;
    private List<Integer> slot;
    private List<String> lore = new ArrayList<>();
}
