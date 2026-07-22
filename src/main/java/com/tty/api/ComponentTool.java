package com.tty.api;

import com.tty.api.utils.ColorConverterLegacy;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
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
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ComponentTool {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static TextComponent text(@Nullable String content) {
        return build(null, content, null);
    }

    public static TextComponent text(@Nullable String content, Map<String, Component> placeholders) {
        return build(null, content, placeholders);
    }

    public static TextComponent text(@Nullable String content, OfflinePlayer player) {
        return build(player, content, null);
    }

    public static TextComponent text(@Nullable String content, OfflinePlayer player, Map<String, Component> placeholders) {
        return build(player, content, placeholders);
    }

    public static Component textList(List<String> list, Map<String, Component> placeholders) {
        return Component.join(JoinConfiguration.separator(Component.newline()), list.stream().map(s -> text(s, placeholders)).toList());
    }

    public static Component textList(List<String> list) {
        return textList(list, null);
    }

    public static Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title),
                text(subTitle),
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public static Title setPlayerTitle(@NotNull Component title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                title,
                subTitle,
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public static Title setPlayerTitle(@NotNull String title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title),
                subTitle,
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public static Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, Map<String, Component> placeholders, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title, placeholders),
                text(subTitle, placeholders),
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public static Component setClickEventText(Component component, ClickEvent event) {
        return component.clickEvent(event);
    }

    public static TextComponent setClickEventText(String content, ClickEvent event) {
        return text(content).clickEvent(event);
    }

    public static TextComponent setClickEventText(String content, Map<String, Component> placeholders, ClickEvent event) {
        return text(content, placeholders).clickEvent(event);
    }

    public static TextComponent setHoverText(String content, String showText) {
        return text(content).hoverEvent(HoverEvent.showText(text(showText)));
    }

    public static Component setHoverItemText(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Component.empty();
        } else {
            return itemStack.displayName().hoverEvent(itemStack.asHoverEvent(showItem -> showItem));
        }
    }

    public static Component setEntityHoverText(@Nullable Entity entity) {
        if (entity == null) {
            return Component.empty();
        }
        return Component.empty().append(entity.name()).hoverEvent(HoverEvent.showText(Component.text(entity.getType().key().asString())));
    }

    @SuppressWarnings("PatternValidation")
    private static TextComponent build(OfflinePlayer player,@Nullable String template, Map<String, Component> placeholders) {
        if (template == null) {
            template = "";
        }
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            template = PlaceholderAPI.setPlaceholders(player, template);
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

        Component comp = MM.deserialize(template, resolver);
        if (comp instanceof TextComponent tc) {
            return tc.decoration(TextDecoration.ITALIC, false);
        }
        return Component.empty().append(comp.decoration(TextDecoration.ITALIC, false));
    }
    
}
