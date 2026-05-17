package com.github.aaronbittel.table;

import java.util.List;
import java.util.stream.IntStream;

public record Schema(String tablename, List<Column> columns, List<Integer> primaryKeys) {

    public Schema {
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
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

    public Row newRow() {
        return new Row(columns.size());
    }
}
