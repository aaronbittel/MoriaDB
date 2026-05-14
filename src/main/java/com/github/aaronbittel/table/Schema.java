package com.github.aaronbittel.table;

import java.util.List;

import com.github.aaronbittel.parser.NamedCell;

public record Schema(String tablename, List<Column> columns, List<Integer> primaryKeys) {

    public Schema {
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
    }

    public Row newRow() {
        return new Row(columns.size());
    }
}
