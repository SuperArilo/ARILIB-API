package com.tty.api.persistence.query;

import com.tty.api.persistence.model.BeanAccess;
import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.model.Getter;

import java.util.*;

public final class Patch<T> {
    private final Class<T> type;
    private final Map<String, Object> values;

    private Patch(Class<T> type, Map<String, Object> values) {
        this.type = type;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static <T> Patch<T> of(Class<T> type) {
        return new Patch<>(Objects.requireNonNull(type), Map.of());
    }

    public static <T> Patch<T> fromValues(Class<T> type, Map<String, Object> values) {
        return new Patch<>(type, values);
    }

    public static <T> Patch<T> replacing(T entity, EntityModel<T, ?> model) {
        var values = new LinkedHashMap<>(model.values(model.copy(entity)));
        values.remove(model.idProperty());
        return new Patch<>(model.entityType(), values);
    }

    public <V> Patch<T> set(Getter<T, V> getter, V value) {
        String property = BeanAccess.property(this.type, getter);
        BeanAccess.validate(BeanAccess.field(this.type, property), value);
        var copy = new LinkedHashMap<>(this.values);
        copy.put(property, BeanAccess.scalar(value));
        return new Patch<>(this.type, copy);
    }

    public Class<T> entityType() {
        return this.type;
    }

    public Map<String, Object> values() {
        return this.values;
    }

    public T apply(T entity, EntityModel<T, ?> model) {
        model.validatePatch(this);
        T result = model.copy(entity);
        this.values.forEach((p, v) -> BeanAccess.write(result, p, v));
        return result;
    }

}
