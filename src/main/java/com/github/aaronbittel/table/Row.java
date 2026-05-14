package com.github.aaronbittel.table;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.aaronbittel.Cell;

public class Row {

    private final Cell[] cells;

    public Row(List<Cell> cells) {
        this.cells = cells.toArray(new Cell[0]);
    }

    public Row(Cell[] cells) {
        this.cells = Arrays.copyOf(cells, cells.length);
    }

    public Row(int size) {
        cells = new Cell[size];
    }

    public void set(int index, Cell cell) {
        if (index < 0 || index >= cells.length) {
            throw new IndexOutOfBoundsException();
        }
        cells[index] = cell;
    }

    public Row selectSubset(List<Integer> indices) {
        Row row = new Row(indices.size());
        int j = 0;
        for (int i = 0; i < cells.length; ++i) {
            if (indices.contains(i)) {
                row.cells[j++] = cells[i];
            }
        }
        return row;
    }

    public byte[] encodeKey(Schema schema) {
        List<Column> columns = schema.columns();

        if (cells.length != schema.columns().size()) {
            throw new IllegalArgumentException(
                    "Cell count and schema column count differ in length");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.writeBytes(schema.tablename().getBytes());
        baos.write((byte) 0);

        for (Integer pkIdx : schema.primaryKeys()) {
            Cell cell = cells[pkIdx];
            Column column = columns.get(pkIdx);
            if (column.type() != cell.type()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Expected schema type '%s' for column '%s', but got '%s'",
                        column.type(), column.name(), cell.type()));
            }
            cell.encode(baos);
        }

        return baos.toByteArray();
    }

    public byte[] encodeVal(Schema schema) {
        List<Column> columns = schema.columns();

        if (cells.length != columns.size()) {
            throw new IllegalArgumentException(
                "Cell count and schema column count differ in length");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int index = 0;
        List<Integer> pkIdxs = schema.primaryKeys();

        for (int i = 0; i < cells.length; ++i) {
            if (index < pkIdxs.size()) {
                if (i == pkIdxs.get(index)) { // skip primary keys
                    index++;
                    continue;
                }
            }
            Column column = columns.get(i);
            Cell cell = cells[i];
            if (cell == null || cell.type() != column.type()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Expected schema type '%s' for column '%s', but got '%s'",
                        column.type(),
                        column.name(),
                        cell == null ? "null" : cell.type()));
            }
            cell.encode(baos);
        }

        return baos.toByteArray();
    }

    public void decodeKey(Schema schema, byte[] keyData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(keyData);

        // skip tablename + '\0' byte
        int b;
        while ((b = bais.read()) != 0) { // NOPMD
            if (b == -1) {
                throw new IllegalStateException("Unexpected EOF reached");
            }
        }

        for (Integer pkIdx : schema.primaryKeys()) {
            Column column = schema.columns().get(pkIdx);
            Cell cell = switch (column.type()) {
                case INT -> Cell.Int.decode(bais);
                case STR -> Cell.Str.decode(bais);
                case NULL -> throw new IllegalArgumentException(
                    "Key column '" + column.name() + "' is 'NULL'");
            };
            cells[pkIdx] = cell;
        }
    }

    public void decodeVal(Schema schema, byte[] valData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(valData);

        List<Integer> pkIdxs = schema.primaryKeys();
        int index = 0;
        for (int i = 0; i < cells.length; ++i) {
            if (index < pkIdxs.size()) {
                if (i == pkIdxs.get(index)) { // skip primary keys
                    index++;
                    continue;
                }
            }
            Column column = schema.columns().get(i);
            Cell cell = switch (column.type()) {
                case INT -> Cell.Int.decode(bais);
                case STR -> Cell.Str.decode(bais);
                case NULL -> throw new IllegalArgumentException(
                    "Value column '" + column.name() + "' is 'NULL'");
            };
            cells[i] = cell;
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Row other) {
            return Arrays.equals(cells, other.cells);
        }
        return false;
    }

    @Override
    public String toString() {
        return Arrays.stream(cells)
            .map(Cell::toString)
            .collect(Collectors.joining(", "));
    }
}
