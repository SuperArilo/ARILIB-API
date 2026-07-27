package com.tty.api.service.placeholder;

import com.tty.api.utils.ColorConverterLegacy;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public interface PlaceholderEngine {

    boolean t = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

    CompletableFuture<Component> render(String template, OfflinePlayer context);
    CompletableFuture<Component> renderList(List<String> templates, OfflinePlayer context);
    CompletableFuture<List<Component>> renderAsComponentList(List<String> list, OfflinePlayer context);

    Component directRender(String template);
    Component directRender(String template, OfflinePlayer context);
    Component directRender(String template, Map<String, Component> map);
    Component directRender(String template, OfflinePlayer context, Map<String, Component> map);
    Component directRenderList(List<String> templates, OfflinePlayer context);
    List<Component> directRenderAsComponentList(List<String> list, OfflinePlayer context);

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

    @SuppressWarnings("PatternValidation")
    default Component build(@Nullable String template, Map<String, Component> placeholders) {
        if (template == null) {
            template = "";
        }
        template = ColorConverterLegacy.convert(template);
        TagResolver resolver;
        if (placeholders == null || placeholders.isEmpty()) {
            resolver = TagResolver.empty();
        } else {
            TagResolver.Builder builder = TagResolver.builder();
            for (Map.Entry<String, Component> e : placeholders.entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                Component value = e.getValue();
                builder.tag(key, Tag.selfClosingInserting(Objects.requireNonNullElseGet(value, Component::empty)));
            }
            resolver = builder.build();
        }

        Component component = MiniMessage.miniMessage().deserialize(template, resolver);
        if (component instanceof TextComponent tc) {
            return tc.decoration(TextDecoration.ITALIC, false);
        }
        return Component.empty().append(component.decoration(TextDecoration.ITALIC, false));
    }

    default String processPlaceholder(@Nullable String content, OfflinePlayer offlinePlayer) {
        if (content == null || content.isEmpty()) return content;
        return t ? ColorConverterLegacy.convert(PlaceholderAPI.setPlaceholders(offlinePlayer, content)):content;
    }

}
