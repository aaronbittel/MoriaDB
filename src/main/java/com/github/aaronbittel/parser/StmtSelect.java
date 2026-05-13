package com.github.aaronbittel.parser;

import java.util.List;
import java.util.stream.Collectors;

public record StmtSelect(String tableName, List<String> columns, List<NamedCell> keys) {

    public StmtSelect {
        columns = List.copyOf(columns);
        keys = List.copyOf(keys);
    }

    @Override
    public String toString() {
        String columnStr = String.join(", ", columns);

        String out = "SELECT %s FROM %s".formatted(columnStr, tableName);

        if (keys.isEmpty()) {
            return out;
        }

        String keysStr = keys.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));

        return out + " WHERE " + keysStr;
    }
}
