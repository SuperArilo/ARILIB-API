package com.tty.api.annotations.function_type;

import com.tty.api.enumType.FunctionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FunctionHandler {
    FunctionType value();
}

