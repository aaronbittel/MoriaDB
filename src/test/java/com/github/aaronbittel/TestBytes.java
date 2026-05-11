package com.github.aaronbittel;

public final class TestBytes {

    private TestBytes() {}

    public static byte[] b(String s) {
        return s.getBytes();
    }

    static BytesKey bk(String s) {
        return new BytesKey(s.getBytes());
    }
}
