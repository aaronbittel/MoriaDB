package com.github.aaronbittel;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class KVStore {

    private final Map<BytesKey, byte[]> kv = new HashMap<>();
    private final Log log;

    public KVStore(String filename) {
        log = new Log(filename);
    }

    public void open() throws IOException {
        kv.clear();
        log.open();

        try {
            while (true) {
                KVStore.Entry entry = log.read();
                if (entry.deleted) {
                    kv.remove(entry.key);
                } else {
                    kv.put(entry.key, entry.value);
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
        log.write(new KVStore.Entry(key, value, false));
        return oldValue != null;
    }

    public boolean delete(byte[] key) throws IOException {
        byte[] oldValue = kv.remove(new BytesKey(key));
        if (oldValue == null) return false;
        log.write(new KVStore.Entry(key, oldValue, true));
        return true;
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

    static final class Entry {
        private final BytesKey key;
        private final byte[] value;
        private final boolean deleted;

        public Entry(byte[] key, byte[] value, boolean deleted) {
            this.key = new BytesKey(key);
            this.value = Arrays.copyOf(value, value.length);
            this.deleted = deleted;
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
            boolean deleted = deletedByte == 0 ? false : true;

            byte[] keyData = in.readNBytes(keySize);
            if (keyData.length != keySize) {
                throw new EOFException();
            }
            byte[] valData = in.readNBytes(valSize);
            if (valData.length != valSize) {
                throw new EOFException();
            }

            return new Entry(keyData, valData, deleted);
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
}
