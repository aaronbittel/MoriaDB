package com.github.aaronbittel;

import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.IntStream;

public record Entry(BytesKey key, byte[] value, boolean deleted) {

    public Entry {
        value = Arrays.copyOf(value, value.length);
    }

    // | key size | val size | deleted | key data | val data |
    // | 4 bytes  | 4 bytes  | 1 byte  |   ...    |   ...    |
    public byte[] encode() {
        int totalSize = Integer.BYTES * 2 + 1 + key.value().length + value.length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        byte[] keyBytes = key.value();
        buf.putInt(keyBytes.length);
        buf.putInt(value.length);
        buf.put((byte) (deleted ? 1 : 0));
        buf.put(keyBytes);
        buf.put(value);

        return buf.array();
    }

    public static Entry decode(DataInput in) throws IOException {
        int keySize = in.readInt();
        int valSize = in.readInt();

        boolean deleted = in.readByte() != 0;

        byte[] keyData = new byte[keySize];
        in.readFully(keyData, 0, keySize);

        byte[] valData = new byte[valSize];
        in.readFully(valData, 0, valSize);

        return new Entry(new BytesKey(keyData), valData, deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.hashCode(value));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Entry other) {
            return key.equals(other.key) && Arrays.equals(value, other.value);
        }
        return false;
    }

    @Override
    public String toString() {
        boolean printable = IntStream.range(0, value.length)
                .allMatch(i -> {
                    int b = value[i] & 0xFF;
                    return b >= 32 && b <= 126;
                });

        String valueStr = printable
                ? new String(value)
                : HexFormat.of().formatHex(value);

        return String.format(
                "Entry{key=%s, value=%s, deleted=%s}",
                key, valueStr, deleted
        );
    }
}
