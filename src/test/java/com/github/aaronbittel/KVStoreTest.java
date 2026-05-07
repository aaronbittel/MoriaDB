package com.github.aaronbittel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KVStoreTest {

    KVStore kv;

    @BeforeEach
    void setup() {
        kv = new KVStore();
    }

    @Test
    void set_new_key_returns_false_and_value_can_be_retrieved() {
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void get_unknown_key_returns_empty_and_delete_returns_false() {
        assertThat(kv.get(b("xxx"))).isEmpty();
        assertThat(kv.delete(b("xxx"))).isFalse();
    }

    @Test
    void delete_existing_key_removes_entry() {
        kv.set(b("k1"), b("v1"));

        assertThat(kv.delete(b("k1"))).isTrue();
        assertThat(kv.get(b("k1"))).isEmpty();
    }

    @Test
    void set_existing_key_returns_true_and_overwrites_value() {
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
        assertThat(kv.set(b("k1"), b("v2"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v2"));
    }

    @Test
    void modifying_input_key_after_insert_does_not_affect_store() {
        byte[] key = b("k1");
        byte[] value = b("v1");

        kv.set(key, value);
        key[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(value);
        assertThat(kv.get(key)).isEmpty();
    }

    @Test
    void modifying_input_value_after_insert_does_not_affect_store() {
        byte[] key = b("k1");
        byte[] value = b("v1");

        kv.set(key, value);
        value[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void entry_encode_returns_expected_binary_format() {
        KVStore.Entry entry = new KVStore.Entry(b("k1"), b("value1"));

        // | key size | val size | key data | val data |
        // | 4 bytes  | 4 bytes  |   ...    |   ...    |

        byte[] expected = new byte[]{
            2, 0, 0, 0, 6, 0, 0, 0, 'k', '1', 'v', 'a', 'l', 'u', 'e', '1',
        };
        assertThat(entry.encode()).isEqualTo(expected);
    }

    @Test
    void decode_valid_kv_entry_returns_entry() throws IOException {
        byte[] data = new byte[]{
            2, 0, 0, 0, 6, 0, 0, 0, 'k', '1', 'v', 'a', 'l', 'u', 'e', '1',
        };
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        KVStore.Entry entry = KVStore.Entry.decode(bais);

        KVStore.Entry expected = new KVStore.Entry(b("k1"), b("value1"));

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_valid_entry_stops_at_expected_length() throws IOException {
        byte[] data = new byte[]{
            2, 0, 0, 0, 6, 0, 0, 0, 'k', '1', 'v', 'a', 'l', 'u', 'e', '1', 0, 4, 5,
        };
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        KVStore.Entry entry = KVStore.Entry.decode(bais);

        KVStore.Entry expected = new KVStore.Entry(b("k1"), b("value1"));

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_entry_from_chunked_stream_parses_correctly() throws IOException {
        byte[] data = new byte[]{
            2, 0, 0, 0, 6, 0, 0, 0, 'k', '1', 'v', 'a', 'l', 'u', 'e', '1'
        };
        ChunkedInputStream cis = new ChunkedInputStream(data, 3);
        KVStore.Entry entry = KVStore.Entry.decode(cis);

        KVStore.Entry expected = new KVStore.Entry(b("k1"), b("value1"));

        assertThat(entry).isEqualTo(expected);
    }

    static byte[] b(String s) {
        return s.getBytes();
    }
}
