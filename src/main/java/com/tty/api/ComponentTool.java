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
import java.util.concurrent.CompletableFuture;

public class ComponentTool {

    private final MiniMessage MM = MiniMessage.miniMessage();
    private final AbstractJavaPlugin plugin;

    public ComponentTool(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    public TextComponent text(@Nullable String content) {
        return build(null, content, null);
    }

    public TextComponent text(@Nullable String content, Map<String, Component> placeholders) {
        return build(null, content, placeholders);
    }

    public TextComponent text(@Nullable String content, OfflinePlayer player) {
        return build(player, content, null);
    }

    public TextComponent text(@Nullable String content, OfflinePlayer player, Map<String, Component> placeholders) {
        return build(player, content, placeholders);
    }

    public Component textList(List<String> list, Map<String, Component> placeholders) {
        return Component.join(JoinConfiguration.separator(Component.newline()), list.stream().map(s -> text(s, placeholders)).toList());
    }

    public Component textList(List<String> list) {
        return textList(list, null);
    }

    public Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title),
                text(subTitle),
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public Title setPlayerTitle(@NotNull Component title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                title,
                subTitle,
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public Title setPlayerTitle(@NotNull String title, @NotNull Component subTitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title),
                subTitle,
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public Title setPlayerTitle(@NotNull String title, @NotNull String subTitle, Map<String, Component> placeholders, Duration fadeIn, Duration stay, Duration fadeOut) {
        return Title.title(
                text(title, placeholders),
                text(subTitle, placeholders),
                Title.Times.times(fadeIn, stay, fadeOut)
        );
    }

    public Component setClickEventText(Component component, ClickEvent.Action action, String actionText) {
        return component.clickEvent(ClickEvent.clickEvent(action, actionText));
    }

    public TextComponent setClickEventText(String content, ClickEvent.Action action, String actionText) {
        return text(content).clickEvent(ClickEvent.clickEvent(action, actionText));
    }

    public TextComponent setClickEventText(String content, Map<String, Component> placeholders, ClickEvent.Action action, String actionText) {
        return text(content, placeholders).clickEvent(ClickEvent.clickEvent(action, actionText));
    }

    public TextComponent setHoverText(String content, String showText) {
        return text(content).hoverEvent(HoverEvent.showText(text(showText)));
    }

    public CompletableFuture<Component> setHoverItemText(ItemStack itemStack) {
        CompletableFuture<Component> future = new CompletableFuture<>();
        this.plugin.getScheduler().run(i -> {
            if (itemStack == null || itemStack.isEmpty()) {
                future.complete(Component.empty());
            } else {
                future.complete(itemStack.displayName().hoverEvent(itemStack.asHoverEvent(showItem -> showItem)));
            }
        });
        return future;
    }

    public CompletableFuture<Component> setEntityHoverText(@Nullable Entity entity) {
        CompletableFuture<Component> future = new CompletableFuture<>();
        if (entity == null) {
            future.complete(Component.empty());
            return future;
        }
        this.plugin.getScheduler().runAtRegion(entity.getLocation(), i -> future.complete(Component.empty().append(entity.name()).hoverEvent(HoverEvent.showText(Component.text(entity.getType().key().asString())))));
        return future;
    }

    @SuppressWarnings("PatternValidation")
    private TextComponent build(OfflinePlayer player,@Nullable String template, Map<String, Component> placeholders) {
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

        Component comp = this.MM.deserialize(template, resolver);
        if (comp instanceof TextComponent tc) {
            return tc.decoration(TextDecoration.ITALIC, false);
        }
        return Component.empty().append(comp.decoration(TextDecoration.ITALIC, false));
    }
    
}
