package com.github.aaronbittel;

import java.io.IOException;
import java.util.zip.CRC32;

public class Moria {
    public static void main(String[] args) throws IOException {
        KVStore kv = new KVStore(new Log("moria.db"));
        kv.open();
        kv.set("key1".getBytes(), "value".getBytes());
        kv.set("second key".getBytes(), "second value".getBytes());
        kv.set("another key".getBytes(), "another value".getBytes());
        kv.delete("second key".getBytes());
        kv.close();

        CRC32 checksum = new CRC32();
        checksum.update(new byte[]{
            0x00, 0x00, 0x00, 0x02,
            0x00, 0x00, 0x00, 0x06,
            0x00,
            0x6B, 0x31,
            0x76, 0x61, 0x6C, 0x75, 0x65, 0x31
        });
        System.out.println(Long.toHexString(checksum.getValue()));
    }
}
