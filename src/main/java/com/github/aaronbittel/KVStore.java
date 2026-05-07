package com.github.aaronbittel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class KVStore {

    private static final class BytesKey {
        private byte[] data;

        public BytesKey(byte[] data) {
            this.data = Arrays.copyOf(data, data.length);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BytesKey other) {
                return Arrays.equals(data, other.data);
            }
            return false;
        }
    }

    private final Map<BytesKey, byte[]> kv = new HashMap<>();

    public Optional<byte[]> get(byte[] key) {
        byte[] value = kv.get(new BytesKey(key));
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public boolean set(byte[] key, byte[] value) {
        byte[] oldValue = kv.put(new BytesKey(key), Arrays.copyOf(value, value.length));
        return oldValue != null;
    }

    public boolean delete(byte[] key) {
        return kv.remove(new BytesKey(key)) != null;
    }
}
