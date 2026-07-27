package com.tty.api.service.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface PlaceholderResolve {

    CompletableFuture<String> resolve(OfflinePlayer context);

    static PlaceholderResolve ofWhenNull(Supplier<CompletableFuture<String>> supplier) {
        return context -> supplier.get();
    }

    static PlaceholderResolve ofWhenNullSync(Supplier<String> supplier) {
        return context -> CompletableFuture.completedFuture(supplier.get());
    }

    static PlaceholderResolve of(Function<Player, CompletableFuture<String>> playerFunc, Function<OfflinePlayer, CompletableFuture<String>> offlineFunc) {
        return context -> {
            if (context == null) {
                throw new IllegalArgumentException("context not allowed null");
            }
            if (context instanceof Player player) {
                return playerFunc.apply(player);
            } else {
                return offlineFunc.apply(context);
            }
        };
    }

    static PlaceholderResolve ofSync(Function<Player, String> playerFunc, Function<OfflinePlayer, String> offlineFunc) {
        return context -> {
            if (context == null) {
                throw new IllegalArgumentException("context not allowed null");
            }
            if (context instanceof Player player) {
                return CompletableFuture.completedFuture(playerFunc.apply(player));
            } else {
                return CompletableFuture.completedFuture(offlineFunc.apply(context));
            }
        };
    }

    static PlaceholderResolve ofPlayer(Function<Player, CompletableFuture<String>> function) {
        return of(function, offlinePlayer -> CompletableFuture.completedFuture(""));
    }

    static PlaceholderResolve ofPlayerSync(Function<Player, String> function) {
        return ofSync(function, offlinePlayer -> "");
    }

    static PlaceholderResolve ofOfflinePlayer(Function<OfflinePlayer, CompletableFuture<String>> function) {
        return function::apply;
    }

    static PlaceholderResolve ofOfflinePlayerSync(Function<OfflinePlayer, String> function) {
        return context -> CompletableFuture.completedFuture(function.apply(context));
    }

}
