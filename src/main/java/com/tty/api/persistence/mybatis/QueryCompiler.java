package com.tty.api.persistence.mybatis;

import com.tty.api.persistence.model.EntityModel;
import com.tty.api.persistence.query.Query;
import com.tty.api.enumType.SQLType;

import java.util.*;

final class QueryCompiler<T> {

    record Sql(String expression, List<Object> parameters) {}

    private final Map<String, String> columns;
    private final EntityModel<T, ?> model;
    private final SQLType sqlType;

    QueryCompiler(EntityModel<T, ?> model, Map<String, String> columns, SQLType sqlType) {
        this.model = model;
        this.columns = columns;
        this.sqlType = sqlType;
    }

    String column(String property) {
        String name = this.columns.get(property);
        if (name == null) throw new IllegalArgumentException("Unmapped property: " + property);
        return SqlSyntax.column(this.sqlType, name);
    }

    String orderColumn(String property) {
        String column = column(property);
        return this.model.propertyType(property) == String.class ? SqlSyntax.exactString(this.sqlType, column) : column;
    }

    Sql compile(Query<T> query) {
        if (query.entityType() != this.model.entityType())
            throw new IllegalArgumentException("Query entity mismatch");

        var parameters = new ArrayList<>();
        return new Sql(condition(query.condition(), parameters), List.copyOf(parameters));
    }

    private String condition(Query.Condition condition, List<Object> parameters) {
        if (condition instanceof Query.Group(boolean and, Set<Query.Condition> children)) {
            if (children.isEmpty()) return and ? "1 = 1" : "1 = 0";
            var expressions = new ArrayList<String>();
            for (var child : children) expressions.add(condition(child, parameters));
            return "(" + String.join(and ? " AND " : " OR ", expressions) + ")";
        }
        var atom = (Query.Atom) condition;
        String column = column(atom.property());
        if (atom.operator() == Query.Operator.IS_NULL) return column + " IS NULL";
        if (atom.operator() == Query.Operator.IS_NOT_NULL) return column + " IS NOT NULL";
        if (atom.operator() == Query.Operator.IN && atom.values().isEmpty()) return "1 = 0";
        boolean string = this.model.propertyType(atom.property()) == String.class;
        if (atom.operator() == Query.Operator.IN) {
            var nativeParameters = new ArrayList<String>();
            var exactParameters = new ArrayList<String>();
            for (Object value : atom.values()) {
                String parameter = "{" + parameters.size() + "}";
                parameters.add(value);
                nativeParameters.add(parameter);
                exactParameters.add(
                        string && this.sqlType == SQLType.MYSQL
                                ? SqlSyntax.exactString(this.sqlType, parameter)
                                : parameter);
            }
            String left = string ? SqlSyntax.exactString(this.sqlType, column) : column;
            String exact = left + " IN (" + String.join(", ", exactParameters) + ")";

            return string ? "(" + column + " IN (" + String.join(", ", nativeParameters) + ") AND " + exact + ")" : exact;
        }
        var parts = new ArrayList<String>();
        for (Object value : atom.values()) {
            String parameter = "{" + parameters.size() + "}";
            parameters.add(value);
            String left = string ? SqlSyntax.exactString(this.sqlType, column) : column;
            String right = string && this.sqlType == SQLType.MYSQL ? SqlSyntax.exactString(this.sqlType, parameter) : parameter;
            String comparison = left + (atom.operator() == Query.Operator.NE ? " <> " : " = ") + right;

            if (string && atom.operator() != Query.Operator.NE)
                comparison = "(" + column + " = " + parameter + " AND " + comparison + ")";

            parts.add(comparison);
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

}
