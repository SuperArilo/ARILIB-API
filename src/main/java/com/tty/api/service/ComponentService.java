package com.tty.api.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface ComponentService {

    TextComponent text(String content);
    TextComponent text(String content, Map<String, Component> placeholders);
    TextComponent text(String content, OfflinePlayer player);
    TextComponent text(String content, OfflinePlayer player, Map<String, Component> placeholders);
    Component textList(List<String> list, Map<String, Component> placeholders);
    Component textList(List<String> list);

    /**
     * 设置MC的全屏通知效果(Title
     * @param title 显示的title文本
     * @param subTitle 显示的副标题文本
     * @param fadeIn 进入动画时间 毫秒
     * @param stay 停留时间 毫秒
     * @param fadeOut 退出动画时间 毫秒
     * @return 返沪一个为Player设置的Title对象
     */
    Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, long fadeIn, long stay, long fadeOut);
    Title setPlayerTitle(@NotNull String title, @NotNull Component subTitle, long fadeIn, long stay, long fadeOut);
    Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, Map<String, Component> placeholders, long fadeIn, long stay, long fadeOut);
    Component setClickEventText(Component component, ClickEvent.Action action, String actionText);
    TextComponent setClickEventText(String content, ClickEvent.Action action, String actionText);
    TextComponent setClickEventText(String content, Map<String, Component> placeholders, ClickEvent.Action action, String actionText);
    TextComponent setHoverText(String content, String showText);
    Component setHoverItemText(ItemStack itemStack);
    Component setEntityHoverText(Entity entity);


}
