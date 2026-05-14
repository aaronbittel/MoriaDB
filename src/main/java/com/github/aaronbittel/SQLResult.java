package com.github.aaronbittel;

import java.util.List;

import com.github.aaronbittel.table.Row;

public record SQLResult(int updated, List<String> headers, List<Row> values) {

    public SQLResult {
        headers = List.copyOf(headers);
        values = List.copyOf(values);
    }

    public static SQLResult of() {
        return new SQLResult(0, List.of(), List.of());
    }
}
