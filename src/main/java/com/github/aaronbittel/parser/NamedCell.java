package com.github.aaronbittel.parser;

import com.github.aaronbittel.Cell;

public record NamedCell(String column, Cell value) {

    @Override
    public String toString() {
        return "%s = %s".formatted(column, value);
    }
}
