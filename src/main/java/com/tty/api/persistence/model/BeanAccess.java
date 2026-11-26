package com.tty.api.persistence.model;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BeanAccess {

    private static final Map<Class<?>, Map<String, Field>> FIELDS = new ConcurrentHashMap<>();

    private BeanAccess() {}

    public static Map<String, Field> fields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, cls -> {
            Map<String, Field> result = new TreeMap<>();
            for (Class<?> c = cls; c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.isSynthetic()) continue;
                    if (result.containsKey(f.getName())) throw new IllegalArgumentException("Shadowed field: " + f.getName());

                    f.setAccessible(true);
                    result.put(f.getName(), f);
                }
            }
            return Collections.unmodifiableMap(result);
        });
    }

    public static String property(Class<?> type, Getter<?, ?> getter) {
        Objects.requireNonNull(getter, "getter");
        try {

            var method = getter.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            var lambda = (SerializedLambda) method.invoke(getter);
            String name = lambda.getImplMethodName();
            if (lambda.getCapturedArgCount() != 0 || !(name.startsWith("get") && name.length() > 3 || name.startsWith("is") && name.length() > 2))
                throw new IllegalArgumentException("Use an unbound getter method reference");

            Class<?> declared = Class.forName(lambda.getImplClass().replace('/', '.'), false, type.getClassLoader());
            if (!declared.isAssignableFrom(type)) throw new IllegalArgumentException("Getter belongs to another entity type");
            String stem = name.substring(name.startsWith("is") ? 2 : 3);
            String property = stem.length() > 1 && Character.isUpperCase(stem.charAt(0)) && Character.isUpperCase(stem.charAt(1)) ? stem : Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
            field(type, property);

            return property;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot resolve getter", e);
        }
    }

    public static Field field(Class<?> type, String name) {
        Field field = fields(type).get(name);
        if (field == null) throw new IllegalArgumentException("Unknown property " + type.getName() + "." + name);
        return field;
    }

    public static Object read(Object bean, String name) {
        try {
            return scalar(field(bean.getClass(), name).get(bean));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void write(Object bean, String name, Object value) {
        Field f = field(bean.getClass(), name);
        validate(f, value);
        try {
            f.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void validate(Field field, Object value) {
        if (value == null && field.getType().isPrimitive())
            throw new IllegalArgumentException("NULL for primitive " + field.getName());
        if (value != null && !boxed(field.getType()).isInstance(value))
            throw new IllegalArgumentException("Wrong value type for " + field.getName());
        scalar(value);
    }

    public static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }

    public static boolean supported(Class<?> type) {
        return Set.of(String.class, Boolean.class, Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class, BigDecimal.class).contains(boxed(type));
    }

    public static Object scalar(Object value) {
        if (value == null) return null;
        if (!supported(value.getClass()))
            throw new IllegalArgumentException("Unsupported mutable/scalar value: " + value.getClass());
        switch (value) {
            case BigDecimal n -> {
                return n.stripTrailingZeros();
            }
            case Double n -> {
                if (!Double.isFinite(n)) throw new IllegalArgumentException("Non-finite double");
                return n == 0d ? 0d : n;
            }
            case Float n -> {
                if (!Float.isFinite(n)) throw new IllegalArgumentException("Non-finite float");
                return n == 0f ? 0f : n;
            }
            default -> {}
        }
        return value;
    }
}
