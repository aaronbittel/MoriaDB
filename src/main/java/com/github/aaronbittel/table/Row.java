package com.github.aaronbittel.table;

import com.github.aaronbittel.Cell;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

public class Row {

    private final Cell[] cells;

    public Row(List<Cell> cells) {
        this.cells = cells.toArray(new Cell[0]);
    }

    public Row(int size) {
        cells = new Cell[size];
    }

    public byte[] encodeKey(Schema schema) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.writeBytes(schema.tablename().getBytes());
        baos.write((byte) 0);

        for (Integer pkIdx : schema.primaryKeys()) {
            Cell cell = cells[pkIdx];
            cell.encode(baos);
        }

        return baos.toByteArray();
    }

    public byte[] encodeVal(Schema schema) {
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
            cells[i].encode(baos);
        }

        return baos.toByteArray();
    }

    public void decodeKey(Schema schema, byte[] keyData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(keyData);

        // skip tablename + '\0' byte
        int b;
        while ((b = bais.read()) != 0) {
            if (b == -1) {
                throw new IllegalStateException("Unexpected EOF reached");
            }
        }

        for (Integer pkIdx : schema.primaryKeys()) {
            Column column = schema.columns().get(pkIdx);
            Cell cell = switch (column.type()) {
                case INT -> Cell.Int.decode(bais);
                case STR -> Cell.Str.decode(bais);
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
}
