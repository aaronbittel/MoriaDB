package com.github.aaronbittel;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class KVStore {

    private final Map<BytesKey, byte[]> kv = new HashMap<>();
    private final Log log;

    public KVStore(Log log) {
        this.log = log;
    }

    public void open() throws IOException {
        kv.clear();
        log.open();

        try {
            while (true) {
                Entry entry = log.read();
                if (entry.deleted()) {
                    kv.remove(entry.key());
                } else {
                    kv.put(entry.key(), entry.value());
                }
            }
        } catch (EOFException _) {}
    }

    public void close() throws IOException {
        log.close();
    }

    public Optional<byte[]> get(byte[] key) {
        byte[] value = kv.get(new BytesKey(key));
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public boolean set(byte[] key, byte[] value) throws IOException {
        byte[] oldValue = kv.put(new BytesKey(key), Arrays.copyOf(value, value.length));
        log.write(new Entry(new BytesKey(key), value, false));
        return oldValue != null;
    }

    public boolean delete(byte[] key) throws IOException {
        byte[] oldValue = kv.remove(new BytesKey(key));
        if (oldValue == null) return false;
        log.write(new Entry(new BytesKey(key), oldValue, true));
        return true;
    }
}
