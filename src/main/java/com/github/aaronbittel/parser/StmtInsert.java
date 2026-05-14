package com.github.aaronbittel.parser;

import java.util.List;
import java.util.stream.Collectors;

import com.github.aaronbittel.Cell;

public record StmtInsert(String tableName, List<Cell> values) {

    public StmtInsert {
        values = List.copyOf(values);
    }

    @Override
    public String toString() {
        String valuesStr = values.stream()
            .map(Cell::toString)
            .collect(Collectors.joining(", "));
        return "INSERT INTO %s values (%s)".formatted(tableName, valuesStr);
    }
}
