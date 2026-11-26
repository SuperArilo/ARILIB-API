package com.tty.api.persistence.query;

import com.tty.api.persistence.model.BeanAccess;
import com.tty.api.persistence.model.Getter;

import java.util.*;


public final class Query<T> {
    public enum Operator {
        EQ,
        NE,
        IN,
        IS_NULL,
        IS_NOT_NULL
    }

    public sealed interface Condition permits Atom, Group {
        boolean matches(Object entity);
        Set<Object> ownerValues(String property);
    }

    public record Atom(String property, Operator operator, List<Object> values) implements Condition {

        public Atom {
            Objects.requireNonNull(property);
            Objects.requireNonNull(operator);
            values = List.copyOf(values);
        }

        @Override
        public boolean matches(Object entity) {
            Object value = BeanAccess.read(entity, this.property);
            return switch (this.operator) {
                case IS_NULL -> value == null;
                case IS_NOT_NULL -> value != null;
                case EQ -> value != null && value.equals(this.values.getFirst());
                case NE -> value != null && !value.equals(this.values.getFirst());
                case IN -> value != null && this.values.contains(value);
            };
        }

        @Override
        public Set<Object> ownerValues(String name) {
            if (!this.property.equals(name)) return null;
            return switch (this.operator) {
                case EQ, IN -> Set.copyOf(this.values);
                case IS_NULL -> Collections.singleton(null);
                default -> null;
            };
        }
    }

    public record Group(boolean and, Set<Condition> children) implements Condition {

        public Group {
            children = Set.copyOf(children);
        }

        @Override
        public boolean matches(Object entity) {
            return this.and ? this.children.stream().allMatch(c -> c.matches(entity)) : this.children.stream().anyMatch(c -> c.matches(entity));
        }

        @Override
        public Set<Object> ownerValues(String name) {
            Set<Object> result = null;
            for (Condition c : this.children) {
                Set<Object> domain = c.ownerValues(name);
                if (this.and) {
                    if (domain != null) {
                        if (result == null) result = new HashSet<>(domain);
                        else result.retainAll(domain);
                    }
                } else {
                    if (domain == null) return null;
                    if (result == null) result = new HashSet<>();
                    result.addAll(domain);
                }
            }
            if (!this.and && this.children.isEmpty()) result = Set.of();
            return result == null ? null : Collections.unmodifiableSet(result);
        }
    }

    public record Order(String property, boolean ascending) {}

    private final Class<T> entityType;
    private final Condition condition;
    private final List<Order> orders;

    private Query(Class<T> type, Condition condition, List<Order> orders) {
        this.entityType = Objects.requireNonNull(type);
        this.condition = condition;
        this.orders = List.copyOf(orders);
    }

    public static <T> Query<T> of(Class<T> type) {
        return new Query<>(type, new Group(true, Set.of()), List.of());
    }

    public Class<T> entityType() {
        return this.entityType;
    }

    public Condition condition() {
        return this.condition;
    }

    public List<Order> orders() {
        return this.orders;
    }

    public boolean matches(T entity) {
        return entity != null && this.condition.matches(entity);
    }

    public Set<Object> ownerValues(String property) {
        return this.condition.ownerValues(property);
    }

    public <V> Query<T> eq(Getter<T, V> getter, V value) {
        return atom(
                getter,
                value == null ? Operator.IS_NULL : Operator.EQ,
                value == null ? List.of() : List.of(value));
    }

    public <V> Query<T> ne(Getter<T, V> getter, V value) {
        return atom(
                getter,
                value == null ? Operator.IS_NOT_NULL : Operator.NE,
                value == null ? List.of() : List.of(value));
    }

    public <V> Query<T> in(Getter<T, V> getter, Collection<? extends V> values) {
        return atom(getter, Operator.IN, values);
    }

    public Query<T> isNull(Getter<T, ?> getter) {
        return atom(getter, Operator.IS_NULL, List.of());
    }

    public Query<T> isNotNull(Getter<T, ?> getter) {
        return atom(getter, Operator.IS_NOT_NULL, List.of());
    }

    private Query<T> atom(Getter<T, ?> getter, Operator op, Collection<?> values) {
        String property = BeanAccess.property(this.entityType, getter);
        Class<?> fieldType = BeanAccess.boxed(BeanAccess.field(this.entityType, property).getType());
        if (op != Operator.IS_NULL
                && op != Operator.IS_NOT_NULL
                && (fieldType == Double.class
                        || fieldType == Float.class
                        || fieldType == java.math.BigDecimal.class))
            throw new IllegalArgumentException("Exact numeric filtering is supported for integer fields; floating/decimal" + " fields support NULL filters and sorting");

        List<Object> normalized = new ArrayList<>();
        for (Object value : values) {
            if (value == null)
                throw new IllegalArgumentException("Use isNull instead of NULL in IN");

            BeanAccess.validate(BeanAccess.field(this.entityType, property), value);
            normalized.add(BeanAccess.scalar(value));
        }
        if (op == Operator.IN) {
            normalized = new ArrayList<>(new LinkedHashSet<>(normalized));
            normalized.sort(Comparator.comparing(v -> v.getClass().getName() + ":" + v));
        }
        return new Query<>(this.entityType, combine(true, this.condition, new Atom(property, op, normalized)), this.orders);
    }

    public Query<T> owner(String property, Object value) {
        var atom = new Atom(
                        property,
                        value == null ? Operator.IS_NULL : Operator.EQ,
                        value == null ? List.of() : List.of(BeanAccess.scalar(value)));

        return new Query<>(this.entityType, combine(true, this.condition, atom), this.orders);
    }

    public Query<T> and(Query<T> other) {
        return this.merge(true, other);
    }

    public Query<T> or(Query<T> other) {
        return this.merge(false, other);
    }

    private Query<T> merge(boolean and, Query<T> other) {
        if (this.entityType != other.entityType || !other.orders.isEmpty())
            throw new IllegalArgumentException("Combine unordered conditions of the same entity type");

        return new Query<>(this.entityType, combine(and, this.condition, other.condition), this.orders);
    }

    private static Condition combine(boolean and, Condition a, Condition b) {
        Set<Condition> children = new HashSet<>();
        for (Condition c : List.of(a, b)) {
            if (c instanceof Group(boolean and1, Set<Condition> children1) && and1 == and) children.addAll(children1);
            else children.add(c);
        }

        return children.size() == 1 ? children.iterator().next() : new Group(and, children);
    }

    public Query<T> orderByAsc(Getter<T, ?> getter) {
        return this.order(getter, true);
    }

    public Query<T> orderByDesc(Getter<T, ?> getter) {
        return this.order(getter, false);
    }

    private Query<T> order(Getter<T, ?> getter, boolean ascending) {
        var copy = new ArrayList<>(this.orders);
        copy.add(new Order(BeanAccess.property(this.entityType, getter), ascending));
        return new Query<>(this.entityType, this.condition, copy);
    }

    public Query<T> withoutOrder() {
        return new Query<>(this.entityType, this.condition, List.of());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Query<?> q
                && this.entityType.equals(q.entityType)
                && this.condition.equals(q.condition)
                && this.orders.equals(q.orders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.entityType, this.condition, this.orders);
    }

    @Override
    public String toString() {
        var text = new StringBuilder(this.entityType.getSimpleName());
        if (this.condition instanceof Group group && group.children().isEmpty()) {
            text.append(" ALL");
        } else {
            text.append(' ').append(render(this.condition));
        }
        for (Order order : this.orders) {
            text.append(" ORDER BY ").append(order.property()).append(order.ascending() ? " ASC" : " DESC");
        }
        return text.toString();
    }

    private static String render(Condition condition) {
        if (condition instanceof Atom(String property, Query.Operator operator, List<Object> values)) {
            return switch (operator) {
                case IS_NULL -> property + " IS NULL";
                case IS_NOT_NULL -> property + " IS NOT NULL";
                case EQ -> property + " = " + values.getFirst();
                case NE -> property + " <> " + values.getFirst();
                case IN -> property + " IN " + values;
            };
        }
        var group = (Group) condition;
        if (group.children().isEmpty()) {
            return group.and() ? "TRUE" : "FALSE";
        }
        var text = new StringBuilder("(");
        boolean first = true;
        for (Condition child : group.children()) {
            if (!first) {
                text.append(group.and() ? " AND " : " OR ");
            }
            text.append(render(child));
            first = false;
        }
        return text.append(')').toString();
    }

}
