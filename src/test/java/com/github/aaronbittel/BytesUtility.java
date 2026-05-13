package com.github.aaronbittel;

public final class BytesUtility {

    public static byte[] bytes(String s) {
        return s.getBytes();
    }

    static BytesKey bytesKey(String s) {
        return new BytesKey(s.getBytes());
    }
}
