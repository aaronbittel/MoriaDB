package com.github.aaronbittel;

import java.io.IOException;

public class Moria {
    public static void main(String[] args) throws IOException {
        KVStore kv = new KVStore(new Log("moria.db"));
        kv.open();
        kv.set("key1".getBytes(), "value".getBytes());
        kv.set("second key".getBytes(), "second value".getBytes());
        kv.set("another key".getBytes(), "another value".getBytes());
        kv.delete("second key".getBytes());
        kv.close();
    }
}
