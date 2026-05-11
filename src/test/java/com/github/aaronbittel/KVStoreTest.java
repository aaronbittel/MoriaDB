package com.github.aaronbittel;

import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KVStoreTest {

    static String TEST_DB = ".test.db";

    KVStore kv;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(Path.of(TEST_DB));
        kv = new KVStore(new Log(TEST_DB));
        kv.open();
    }

    @AfterEach
    void teardown() throws IOException {
        Files.deleteIfExists(Path.of(TEST_DB));
        kv.close();
    }

    @Test
    void setting_same_value_to_same_key_should_return_false() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
    }

    @Test
    void set_new_key_returns_true_and_value_can_be_retrieved() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void get_unknown_key_returns_empty_and_delete_returns_false() throws IOException {
        assertThat(kv.get(b("xxx"))).isEmpty();
        assertThat(kv.delete(b("xxx"))).isFalse();
    }

    @Test
    void delete_existing_key_removes_entry() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();

        assertThat(kv.delete(b("k1"))).isTrue();
        assertThat(kv.get(b("k1"))).isEmpty();
    }

    @Test
    void set_existing_key_returns_true_and_overwrites_value() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k1"), b("v2"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v2"));
    }

    @Test
    void modifying_input_key_after_insert_does_not_affect_store() throws IOException {
        byte[] key = b("k1");
        byte[] value = b("v1");

        assertThat(kv.set(key, value)).isTrue();
        key[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(value);
        assertThat(kv.get(key)).isEmpty();
    }

    @Test
    void modifying_input_value_after_insert_does_not_affect_store() throws IOException {
        byte[] key = b("k1");
        byte[] value = b("v1");

        assertThat(kv.set(key, value)).isTrue();
        value[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void entry_encode_returns_expected_binary_format() {
        Entry entry = new Entry(bk("k1"), b("value1"), false);

        // |  crc32  | key size | val size | deleted | key data | val data |
        // | 4 bytes | 4 bytes  | 4 bytes  | 1 byte  |   ...    |   ...    |

        byte[] expected = new byte[]{
            (byte) 0xB5, (byte) 0xBD, (byte) 0x9A, (byte) 0xA5,
            0, 0, 0, 2,
            0, 0, 0, 6,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };
        assertThat(entry.encode()).isEqualTo(expected);
    }

    @Test
    void decode_valid_kv_entry_returns_entry() throws IOException {
        byte[] data = new byte[]{
            (byte) 0xB5, (byte) 0xBD, (byte) 0x9A, (byte) 0xA5,
            0, 0, 0, 2,
            0, 0, 0, 6,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        Entry entry = Entry.decode(in);

        Entry expected = new Entry(bk("k1"), b("value1"), false);

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_valid_entry_stops_at_expected_length() throws IOException {
        byte[] data = new byte[]{
            (byte) 0xB5, (byte) 0xBD, (byte) 0x9A, (byte) 0xA5,
            0, 0, 0, 2,
            0, 0, 0, 6,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1',
            0, 5, 6
        };
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        Entry entry = Entry.decode(in);

        Entry expected = new Entry(bk("k1"), b("value1"), false);

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_entry_from_chunked_stream_parses_correctly() throws IOException {
        byte[] data = new byte[]{
            (byte) 0xB5, (byte) 0xBD, (byte) 0x9A, (byte) 0xA5,
            0, 0, 0, 2,
            0, 0, 0, 6,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };
        DataInput in = new DataInputStream(new ChunkedInputStream(data, 3));
        Entry entry = Entry.decode(in);

        Entry expected = new Entry(bk("k1"), b("value1"), false);

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void reopening_kv_restores_saved_entries() throws IOException {
        assertThat(kv.set(b("key1"), b("value"))).isTrue();
        assertThat(kv.set(b("second key"), b("second value"))).isTrue();
        assertThat(kv.set(b("another key"), b("another value"))).isTrue();
        assertThat(kv.delete(b("second key"))).isTrue();
        kv.close();

        kv.open();
        assertThat(kv.get(b("key1"))).hasValue(b("value"));
        assertThat(kv.get(b("second key"))).isEmpty();
        assertThat(kv.get(b("another key"))).hasValue(b("another value"));
    }

    @Test
    void reloading_kv_restores_saved_entries() throws IOException {
        assertThat(kv.set(b("key1"), b("value"))).isTrue();
        assertThat(kv.set(b("second key"), b("second value"))).isTrue();
        assertThat(kv.set(b("another key"), b("another value"))).isTrue();
        assertThat(kv.delete(b("second key"))).isTrue();
        kv.close();

        kv = new KVStore(new Log(TEST_DB));
        kv.open();
        assertThat(kv.get(b("key1"))).hasValue(b("value"));
        assertThat(kv.get(b("second key"))).isEmpty();
        assertThat(kv.get(b("another key"))).hasValue(b("another value"));
    }

    @Test
    void simulate_truncated_log() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k2"), b("v1"))).isTrue();
        kv.close();

        try (FileChannel channel = FileChannel.open(Path.of(TEST_DB), WRITE)) {
            channel.truncate(channel.size() - 1);
        }

        kv.open();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
        assertThat(kv.get(b("k2"))).isEmpty();
    }

    @Test
    void simulate_incorrect_checksum() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k2"), b("v1"))).isTrue();
        kv.close();

        try (FileChannel channel = FileChannel.open(Path.of(TEST_DB), WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x00});
            assertThat(channel.write(buf, channel.size() - 2)).isEqualTo(1);
        }

        kv.open();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
        assertThat(kv.get(b("k2"))).isEmpty();
    }

    static byte[] b(String s) {
        return s.getBytes();
    }

    static BytesKey bk(String s) {
        return new BytesKey(s.getBytes());
    }
}
