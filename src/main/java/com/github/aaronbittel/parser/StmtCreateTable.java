package com.github.aaronbittel.parser;

import java.util.List;
import java.util.stream.Collectors;

import com.github.aaronbittel.table.Column;

public record StmtCreateTable(
    String tableName,
    List<Column> columns,
    List<String> primaryKeys
) implements Stmt {

    public StmtCreateTable {
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
    }

    @Override
    public String toString() {
        String columnsStr = columns.stream()
            .map(column -> String.format("%s %s,", column.name(), column.type().asType()))
            .collect(Collectors.joining("\n"));
        String primaryKeysStr = String.join(", ", primaryKeys);
        return
        """
        CREATE TABLE %s (
            %s
            primary key (%s)
        );
        """.formatted(tableName, columnsStr, primaryKeysStr);
    }
}
