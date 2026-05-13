package com.github.aaronbittel.table;

public enum CellType {
    INT,
    STR,
    NULL;

    public static CellType of(String input) {
        return switch (input) {
            case "int64" -> INT;
            case "string" -> STR;
            default -> throw new IllegalArgumentException("Unknown column type " + input);
        };
    }

    public String asType() {
        return switch (this) {
            case INT -> "int64";
            case STR -> "string";
            case NULL -> throw new IllegalArgumentException("'NULL' is no valid type");
        };
    }
}
