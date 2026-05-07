package com.github.aaronbittel;

import java.io.InputStream;
import java.util.Arrays;

public class ChunkedInputStream extends InputStream {
    private final byte[] data;
    private int pos = 0;
    private final int chunkSize;

    public ChunkedInputStream(byte[] data, int chunkSize) {
        this.data = Arrays.copyOf(data, data.length);
        this.chunkSize = chunkSize;
    }

    @Override
    public int read() {
        if (pos >= data.length) return -1;
        return data[pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (pos >= data.length) return -1;

        int remaining = data.length - pos;
        int toRead = Math.min(Math.min(len, chunkSize), remaining);

        System.arraycopy(data, pos, b, off, toRead);
        pos += toRead;

        return toRead;
    }
}
