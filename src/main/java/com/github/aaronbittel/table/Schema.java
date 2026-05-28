package com.github.aaronbittel.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.aaronbittel.parser.StmtCreateTable;

public record Schema(String tableName, List<Column> columns, List<Integer> primaryKeys) {

    public Schema {
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
    }

    public static Schema of(StmtCreateTable stmt) {
        List<String> columnNames = stmt.columns().stream().map(Column::name).toList();
        List<Integer> primaryKeysIdxs = getPrimaryKeysIdxs(stmt, columnNames);

        return new Schema(stmt.tableName(), stmt.columns(), primaryKeysIdxs);
    }

    public List<Integer> lookupColumns(List<String> selectedColumns) {
        List<String> columnNames = columns.stream().map(Column::name).toList();

        Map<Boolean, List<String>> m = selectedColumns.stream()
            .collect(Collectors.partitioningBy(columnNames::contains));

        if (!m.get(false).isEmpty()) {
            throw new IllegalArgumentException(
                "The following columns do not exist in the table '"
                + tableName + "': " + String.join(", ", m.get(false)));
        }

        return m.get(true).stream()
            .map(columnNames::indexOf)
            .toList();
    }

    public List<Column> getPrimaryKeyColumns() {
        return IntStream.range(0, columns.size())
            .filter(primaryKeys::contains)
            .mapToObj(columns::get)
            .toList();
    }

    public List<String> getPrimaryKeyNames() {
        return getPrimaryKeyColumns().stream().map(Column::name).toList();
    }

    private static List<Integer> getPrimaryKeysIdxs(
        StmtCreateTable stmt, List<String> columns)
    {
        List<Integer> primaryKeysIdxs = new ArrayList<>();
        for (String primaryKey : stmt.primaryKeys()) {
            int idx = columns.indexOf(primaryKey);
            if (idx == -1) {
                throw new IllegalStateException(
                    "Invariant violation: primary key '" + primaryKey
                    + "' not found after validation");
            }
            primaryKeysIdxs.add(idx);
        }

        return primaryKeysIdxs.stream().distinct().sorted().toList();
    }

    public Row newRow() {
        return new Row(columns.size());
    }
}
