package com.tty.api.dto;

import lombok.Getter;

import java.util.function.Consumer;

public class TempRegisterService<T> {

    @Getter
    private final String pluginName;
    @Getter
    private final Class<T> tClass;
    @Getter
    private final Consumer<T> consumer;

    TempRegisterService(String pluginName, Class<T> tClass, Consumer<T> consumer) {
        this.pluginName = pluginName;
        this.tClass = tClass;
        this.consumer = consumer;
    }

    public static <T> TempRegisterService<T> of(String pluginName, Class<T> tClass, Consumer<T> t) {
        return new TempRegisterService<>(pluginName, tClass, t);
    }

}
