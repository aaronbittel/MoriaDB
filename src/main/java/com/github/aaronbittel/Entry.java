package com.github.aaronbittel;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

public record Entry(BytesKey key, byte[] value, boolean deleted) {

    public Entry {
        value = Arrays.copyOf(value, value.length);
    }

    // |  crc32  | key size | val size | deleted | key data | val data |
    // | 4 bytes | 4 bytes  | 4 bytes  | 1 byte  |   ...    |   ...    |
    public byte[] encode() {
        int payloadSize = Integer.BYTES * 3 + 1 + key.value().length + value.length;
        ByteBuffer payloadBuf = ByteBuffer.allocate(payloadSize).order(LITTLE_ENDIAN);
        payloadBuf.position(Integer.BYTES); // skip checksum

        byte[] keyBytes = key.value();
        payloadBuf
            .putInt(keyBytes.length)
            .putInt(value.length)
            .put((byte) (deleted ? 1 : 0))
            .put(keyBytes)
            .put(value);

        CRC32 crc = new CRC32();
        byte[] buf = payloadBuf.array();
        crc.update(buf, Integer.BYTES, buf.length - Integer.BYTES);

        payloadBuf.putInt(0, (int) crc.getValue()); // modifies buf

        return buf;
    }

    public static Entry decode(DataInput in) throws IOException {
        // checksum(4) + keySize(4) + valSize(4) + deleted(1)
        byte[] header = new byte[Integer.BYTES * 3 + 1];
        in.readFully(header, 0, header.length);

        ByteBuffer headerBuf = ByteBuffer.wrap(header).order(LITTLE_ENDIAN);

        int expectedChecksum = headerBuf.getInt();
        int keySize = headerBuf.getInt();
        int valSize = headerBuf.getInt();
        boolean deleted = headerBuf.get() != 0;

        byte[] keyData = new byte[keySize];
        in.readFully(keyData, 0, keyData.length);

        byte[] valData = new byte[valSize];
        in.readFully(valData, 0, valSize);

        CRC32 crc = new CRC32();
        crc.update(header, Integer.BYTES, header.length - Integer.BYTES);
        crc.update(keyData);
        crc.update(valData);

        int actualChecksum = (int) crc.getValue();

        if (expectedChecksum != actualChecksum) {
            throw new IOException("Checksum mismatch");
        }

        return new Entry(new BytesKey(keyData), valData, deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.hashCode(value));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Entry other) {
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
