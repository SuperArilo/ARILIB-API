package com.tty.api.persistence.model;

import com.tty.api.persistence.query.Patch;
import com.tty.api.persistence.query.Query;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;


public final class EntityModel<T, ID> {

    private final Class<T> type;
    private final String idProperty;
    private final String ownerProperty;
    private final Constructor<T> constructor;

    private EntityModel(Class<T> type, Getter<T, ID> id, Getter<T, ?> owner) {
        this.type = Objects.requireNonNull(type);
        this.idProperty = BeanAccess.property(type, id);
        this.ownerProperty = owner == null ? null : BeanAccess.property(type, owner);
        if (!exactKeyType(this.propertyType(this.idProperty)) || ownerProperty != null && !exactKeyType(this.propertyType(this.ownerProperty)))
            throw new IllegalArgumentException("Primary keys and collection owners must use String, Boolean, or integer" + " types");
        try {
            this.constructor = type.getDeclaredConstructor();
            this.constructor.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Entity needs a no-argument constructor", e);
        }
        for (var field : BeanAccess.fields(type).values()) {
            if (Modifier.isFinal(field.getModifiers()) || !BeanAccess.supported(field.getType()))
                throw new IllegalArgumentException("Entity snapshot requires writable scalar field: " + field.getName());
        }
    }

    public static <T, ID> EntityModel<T, ID> shared(Class<T> type, Getter<T, ID> id) {
        return new EntityModel<>(type, id, null);
    }

    private static boolean exactKeyType(Class<?> type) {
        return Set.of(String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class).contains(type);
    }

    public static <T, ID> EntityModel<T, ID> grouped(Class<T> type, Getter<T, ID> id, Getter<T, ?> owner) {
        return new EntityModel<>(type, id, Objects.requireNonNull(owner));
    }

    public Class<T> entityType() {
        return this.type;
    }

    public String idProperty() {
        return this.idProperty;
    }

    public String ownerProperty() {
        return this.ownerProperty;
    }

    public boolean grouped() {
        return this.ownerProperty != null;
    }

    @SuppressWarnings("unchecked")
    public ID id(T entity) {
        return (ID) BeanAccess.read(entity, this.idProperty);
    }

    public Object owner(T entity) {
        return grouped() ? BeanAccess.read(entity, this.ownerProperty) : null;
    }

    public Class<?> propertyType(String property) {
        return BeanAccess.boxed(BeanAccess.field(this.type, property).getType());
    }

    public Map<String, Object> values(T entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String property : BeanAccess.fields(this.type).keySet())
            result.put(property, BeanAccess.read(entity, property));
        return Collections.unmodifiableMap(result);
    }

    public T fromDatabaseValues(Map<String, ?> values) {
        for (String property : values.keySet()) BeanAccess.field(this.type, property);
        try {
            T result = this.constructor.newInstance();
            for (String property : BeanAccess.fields(this.type).keySet()) {
                Object value = databaseScalar(propertyType(property), values.get(property));
                BeanAccess.write(result, property, value);
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot decode entity", e);
        }
    }

    private Object databaseScalar(Class<?> expected, Object value) {
        if (value == null || expected.isInstance(value)) return BeanAccess.scalar(value);
        if (value instanceof Number number) {
            var decimal = new java.math.BigDecimal(number.toString());
            if (expected == Long.class) return decimal.longValueExact();
            if (expected == Integer.class) return decimal.intValueExact();
            if (expected == Short.class) return decimal.shortValueExact();
            if (expected == Byte.class) return decimal.byteValueExact();
            if (expected == Double.class) return BeanAccess.scalar(number.doubleValue());
            if (expected == Float.class) return BeanAccess.scalar(number.floatValue());
            if (expected == java.math.BigDecimal.class) return decimal.stripTrailingZeros();
            if (expected == Boolean.class && (decimal.compareTo(java.math.BigDecimal.ZERO) == 0 || decimal.compareTo(java.math.BigDecimal.ONE) == 0))
                return decimal.intValue() == 1;
        }

        throw new IllegalArgumentException("Unsupported JDBC value for " + expected.getName() + ": " + value.getClass().getName());
    }

    public Query<T> byId(ID id) {
        Objects.requireNonNull(id, "primary key");
        BeanAccess.validate(BeanAccess.field(this.type, this.idProperty), id);
        return Query.of(this.type).owner(this.idProperty, BeanAccess.scalar(id));
    }

    public void validateOwner(Object value) {
        if (!grouped()) throw new IllegalStateException("Shared repository has no owner");
        if (value == null) throw new IllegalArgumentException("Owner cannot be NULL");
        BeanAccess.validate(BeanAccess.field(this.type, this.ownerProperty), value);
    }

    public void validatePatch(Patch<T> patch) {
        if (patch.entityType() != this.type)
            throw new IllegalArgumentException("Patch entity type mismatch");
        if (patch.values().containsKey(this.idProperty))
            throw new IllegalArgumentException("Cannot patch primary key");
    }

    public T copy(T source) {
        if (source == null) return null;
        if (source.getClass() != this.type) throw new IllegalArgumentException("Entity type mismatch");
        try {
            T target = this.constructor.newInstance();
            for (var field : BeanAccess.fields(this.type).values())
                BeanAccess.write(target, field.getName(), BeanAccess.read(source, field.getName()));
            return target;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot snapshot entity", e);
        }
    }
}
