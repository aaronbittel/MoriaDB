package com.github.aaronbittel;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.IntStream;

public record BytesKey(byte[] data) {
    public BytesKey {
        data = Arrays.copyOf(data, data.length);
    }

    public byte[] value() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BytesKey(byte[] data1)) {
            return Arrays.equals(data, data1);
        }
        return false;
    }

    @Override
    public String toString() {
        boolean printable = IntStream.range(0, data.length)
            .allMatch(i -> {
                int b = data[i] & 0xFF;
                return b >= 32 && b <= 126;
            });
        if (printable) {
            return new String(data);
        }
        return String.format(
            "{len=%d, data=%s}",
            data.length, HexFormat.of().formatHex(data));
    }
}

