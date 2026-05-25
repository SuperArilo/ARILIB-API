package com.tty.api.dto;

import java.util.function.Consumer;

public record TempRegisterService<T>(String pluginName, Class<T> tClass, Consumer<T> consumer) {

    public static <T> TempRegisterService<T> of(String pluginName, Class<T> tClass, Consumer<T> t) {
        return new TempRegisterService<>(pluginName, tClass, t);
    }

}
