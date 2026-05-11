package com.github.aaronbittel;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
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
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(LITTLE_ENDIAN);

        byte[] keyBytes = key.value();
        buf.putInt(keyBytes.length);
        buf.putInt(value.length);
        buf.put((byte) (deleted ? 1 : 0));
        buf.put(keyBytes);
        buf.put(value);

        return buf.array();
    }

    public static Entry decode(InputStream in) throws IOException {
        byte[] keyBuf = in.readNBytes(4);
        if (keyBuf.length != 4) {
            throw new EOFException();
        }
        int keySize = ByteBuffer.wrap(keyBuf).order(LITTLE_ENDIAN).getInt();

        byte[] valBuf = in.readNBytes(4);
        if (valBuf.length != 4) {
            throw new EOFException();
        }
        int valSize = ByteBuffer.wrap(valBuf).order(LITTLE_ENDIAN).getInt();

        byte deletedByte = (byte) in.read();
        if (deletedByte == -1) {
            throw new EOFException();
        }
        boolean deleted = deletedByte != 0;

        byte[] keyData = in.readNBytes(keySize);
        if (keyData.length != keySize) {
            throw new EOFException();
        }
        byte[] valData = in.readNBytes(valSize);
        if (valData.length != valSize) {
            throw new EOFException();
        }

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
