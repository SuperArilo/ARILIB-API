package com.tty.api.service.placeholder;

import com.tty.api.utils.ColorConverterLegacy;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface PlaceholderEngine {

    boolean t = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    /**
     * 渲染指定内容，可经过 PlaceholderAPI 和插件自定义占位符替换
     * @param template 渲染内容
     * @param context 上下文要求
     * @return 生成的 Component
     */
    CompletableFuture<Component> render(String template, OfflinePlayer context);

    /**
     * 将指定字符串数组渲染为可换行的 Component ，可经过 PlaceholderAPI 和插件自定义占位符替换
     * @param templates 渲染内容
     * @param context 上下文要求
     * @return 生成的 Component
     */
    CompletableFuture<Component> renderList(List<String> templates, OfflinePlayer context);

    /**
     * 直接将数组字符串渲染为 Component 数组，可经过 PlaceholderAPI 和插件自定义占位符替换
     * @param list 渲染内容
     * @param context 上下文要求
     * @return 生成的 Component
     */
    CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context);

    /**
     * 直接渲染文字，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换
     * @param template 渲染内容
     * @return 生成的 Component
     */
    Component directRender(String template);

    /**
     * 直接渲染文字，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换
     * @param template 渲染内容
     * @param context 上下文要求
     * @return 生成的 Component
     */
    Component directRender(String template, OfflinePlayer context);

    /**
     * 直接渲染文字，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换，可手动替换占位符
     * @param template 渲染内容
     * @param map 可手动替换的占位符
     * @return 生成的 Component
     */
    Component directRender(String template, Map<String, Component> map);

    /**
     * 直接渲染文字，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换
     * @param template 渲染内容
     * @param context 上下文要求
     * @param map 可手动替换的占位符
     * @return 生成的 Component
     */
    Component directRender(String template, OfflinePlayer context, Map<String, Component> map);

    /**
     * 直接将数组字符串渲染为可换行 Component，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换
     * @param templates 渲染数组内容
     * @param context 上下文要求
     * @return 生成的 Component
     */
    Component directRenderList(List<String> templates, OfflinePlayer context);

    /**
     * 直接将数组字符串渲染为 Component 数组，可经过 PlaceholderAPI, 无法经过插件自定义占位符替换，可手动替换占位符
     * @param templates 渲染数组内容
     * @param context 上下文要求
     * @return 生成的 Component 数组
     */
    List<Component> directRenderAsComponentList(List<String> templates, OfflinePlayer context);

    void shutdown();

    default CompletableFuture<Component> render(String template, Player player) {
        return render(template, (OfflinePlayer) player);
    }

    default CompletableFuture<Component> renderList(List<String> templates, Player player) {
        return renderList(templates, (OfflinePlayer) player);
    }

    default CompletableFuture<List<Component>> renderAsComponentList(List<String> list, Player player) {
        return renderAsComponentList(list, (OfflinePlayer) player);
    }

    default Title playerTitle(@NotNull String title, @NotNull String subTitle, Map<String, Component> placeholders, Duration fadeIn, Duration stay, Duration fadeOut) {
        return this.playerTitle(this.build(title, placeholders), this.build(subTitle, placeholders), fadeIn, stay, fadeOut);
    }

    default Title playerTitle(@NotNull String title, @NotNull String subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return this.playerTitle(this.build(title, null), this.build(subTitle, null), fadeIn, stay, fadeOut);
    }

    default Title playerTitle(@NotNull String title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return this.playerTitle(this.build(title, null), subTitle, fadeIn, stay, fadeOut);
    }

    default Title playerTitle(@NotNull Component title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(title, subTitle, Title.Times.times(fadeIn, stay, fadeOut));
    }

    default Component setClickEventText(Component component, ClickEvent event) {
        return component.clickEvent(event);
    }

    default Component setClickEventText(String content, ClickEvent event) {
        return this.build(content, null).clickEvent(event);
    }

    default Component setClickEventText(String content, Map<String, Component> placeholders, ClickEvent event) {
        return this.build(content, placeholders).clickEvent(event);
    }

    default Component setHoverText(String content, String showText) {
        return this.build(content, null).hoverEvent(HoverEvent.showText(this.build(showText, null)));
    }

    default Component setHoverItemText(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Component.empty();
        } else {
            return itemStack.displayName().hoverEvent(itemStack.asHoverEvent(showItem -> showItem));
        }
    }

    default Component build(@Nullable String template, Map<String, Component> placeholders) {
        if (template == null) template = "";
        Component result = MiniMessage.miniMessage().deserialize(ColorConverterLegacy.convert(template));
        if (placeholders != null) {
            for (Map.Entry<String, Component> entry : placeholders.entrySet()) {
                result = result.replaceText(TextReplacementConfig.builder().matchLiteral("<" + entry.getKey() + ">").replacement(entry.getValue()).build());
            }
        }
        if (result instanceof TextComponent tc) {
            return tc.decoration(TextDecoration.ITALIC, false);
        }
        return Component.empty().append(result.decoration(TextDecoration.ITALIC, false));
    }

    default String processPlaceholder(@Nullable String content, OfflinePlayer offlinePlayer) {
        if (content == null || content.isEmpty()) return content;
        return t ? PlaceholderAPI.setPlaceholders(offlinePlayer, content):content;
    }

}
