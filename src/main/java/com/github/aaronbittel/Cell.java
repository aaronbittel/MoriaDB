package com.github.aaronbittel;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.github.aaronbittel.table.CellType;

public sealed interface Cell permits Cell.Int, Cell.Str, Cell.Null {

    void encode(ByteArrayOutputStream baos);

    CellType type();

    record Int(long value) implements Cell {

        public void encode(ByteArrayOutputStream baos) {
            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).order(LITTLE_ENDIAN);
            buf.putLong(value);
            baos.writeBytes(buf.array());
        }

        @Override
        public CellType type() {
            return CellType.INT;
        }

        public static Cell.Int decode(ByteArrayInputStream bais) {
            byte[] data = new byte[Long.BYTES];
            bais.readNBytes(data, 0, data.length);
            ByteBuffer buf = ByteBuffer.wrap(data).order(LITTLE_ENDIAN);
            return new Cell.Int(buf.getLong());
        }
    }

    record Str(byte[] data) implements Cell {

        public void encode(ByteArrayOutputStream baos) {
            ByteBuffer buf = ByteBuffer
                .allocate(Integer.BYTES + data.length)
                .order(LITTLE_ENDIAN);

            buf.putInt(data.length);
            buf.put(data);
            baos.writeBytes(buf.array());
        }

        @Override
        public CellType type() {
            return CellType.STR;
        }

        public static Cell.Str decode(ByteArrayInputStream bais) {
            byte[] sizeData = new byte[Integer.BYTES];
            bais.readNBytes(sizeData, 0, sizeData.length);
            ByteBuffer buf = ByteBuffer.wrap(sizeData).order(LITTLE_ENDIAN);
            int size = buf.getInt();

            byte[] data = new byte[size];
            bais.readNBytes(data, 0, data.length);
            return new Cell.Str(data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Cell.Str(byte[] otherData)) {
                return Arrays.equals(data, otherData);
            }
            return false;
        }
    }

    /**
     * Represents an absent or unspecified cell value.
     *
     * <p>Primarily used for partial rows where a column value is intentionally omitted,
     * such as lookup or select operations that only require the primary key while
     * other fields remain unspecified.
     */
    record Null() implements Cell {

        public void encode(ByteArrayOutputStream baos) {} // do nothing

        public CellType type() {
            return CellType.NULL;
        }
    }
}
