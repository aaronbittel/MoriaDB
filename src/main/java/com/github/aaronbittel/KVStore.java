package com.github.aaronbittel;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class KVStore {

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

    private record BytesKey(byte[] data) {
        private BytesKey(byte[] data) {
            this.data = Arrays.copyOf(data, data.length);
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
            return String.format(
                "BytesKey{len=%d, data=%s}", data.length, HexFormat.of().formatHex(data));
        }
    }

    static final class Entry {
        private final BytesKey key;
        private final byte[] value;

        public Entry(byte[] key, byte[] value) {
            this.key = new BytesKey(key);
            this.value = Arrays.copyOf(value, value.length);
        }

        // | key size | val size | key data | val data |
        // | 4 bytes  | 4 bytes  |   ...    |   ...    |
        public byte[] encode() {
            int totalSize = Integer.BYTES * 2 + key.value().length + value.length;
            ByteBuffer buf = ByteBuffer.allocate(totalSize).order(LITTLE_ENDIAN);

            byte[] keyBytes = key.value();
            buf.putInt(keyBytes.length);
            buf.putInt(value.length);
            buf.put(keyBytes);
            buf.put(value);

            return buf.array();
        }

        public static Entry decode(InputStream in) throws IOException {
            byte[] keyBuf = in.readNBytes(4);
            int keySize = ByteBuffer.wrap(keyBuf).order(LITTLE_ENDIAN).getInt();

            byte[] valBuf = in.readNBytes(4);
            int valSize = ByteBuffer.wrap(valBuf).order(LITTLE_ENDIAN).getInt();

            byte[] keyData = in.readNBytes(keySize);
            byte[] valData = in.readNBytes(valSize);

            return new Entry(keyData, valData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, Arrays.hashCode(value));
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof KVStore.Entry other) {
                return key.equals(other.key) && Arrays.equals(value, other.value);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format(
                "Entry{key=%s, valueLen=%d, value=%s",
                key,
                value.length,
                HexFormat.of().formatHex(value)
            );
        }
    }
}
