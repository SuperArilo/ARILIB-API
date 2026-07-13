package com.tty.api.gui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public interface AsyncGuiClick {

    @NotNull Component whenPending();
    @NotNull Component whenDone();
    @NotNull Component whenError();

}
