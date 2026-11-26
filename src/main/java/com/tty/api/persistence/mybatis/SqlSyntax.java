package com.tty.api.persistence.mybatis;

import com.tty.api.enumType.SQLType;

final class SqlSyntax {

    private SqlSyntax() {}

    static String column(SQLType sqlType, String name) {
        String plain = name;
        if ((name.startsWith("`") && name.endsWith("`")) || (name.startsWith("\"") && name.endsWith("\""))) {
            plain = name.substring(1, name.length() - 1);
        }

        if (!plain.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsupported mapped column: " + name);
        }

        return switch (sqlType) {
            case MYSQL -> "`" + plain + "`";
            case SQLITE -> "\"" + plain + "\"";
        };

    }

    static String exactString(SQLType sqlType, String expression) {
        return switch (sqlType) {
            case MYSQL -> "CAST(CONVERT(" + expression + " USING utf8mb4) AS BINARY)";
            case SQLITE -> expression + " COLLATE BINARY";
        };
    }
}
