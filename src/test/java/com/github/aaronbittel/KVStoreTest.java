package com.github.aaronbittel;

import static com.github.aaronbittel.TestBytes.b;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

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
    void set_returns_false_when_value_is_unchanged() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
    }

    @Test
    void set_new_key_returns_true_and_stores_value() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void get_unknown_key_returns_empty() throws IOException {
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
    void set_Upsert_existing_key_returns_true_and_overwrites_value() throws IOException {
        assertThat(kv.set(b("k1"), b("v1"))).isTrue();
        assertThat(kv.set(b("k1"), b("v2"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v2"));
    }

    @Test
    void modifying_key_after_set_Upsert_does_not_affect_store() throws IOException {
        byte[] key = b("k1");
        byte[] value = b("v1");

        assertThat(kv.set(key, value)).isTrue();
        key[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(value);
        assertThat(kv.get(key)).isEmpty();
    }

    @Test
    void modifying_value_after_set_Upsert_does_not_affect_store() throws IOException {
        byte[] key = b("k1");
        byte[] value = b("v1");

        assertThat(kv.set(key, value)).isTrue();
        value[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void reopening_kv_restores_persisted_entries() throws IOException {
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
    void reloading_kv_restores_persisted_entries() throws IOException {
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
    void recovers_from_truncated_log() throws IOException {
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
    void recovers_from_checksum_failure() throws IOException {
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

    @Test
    void set_insert_mode_does_not_update_value_and_returns_false() throws IOException {
        assertThat(kv.setEx(b("k1"), b("v1"), UpdateMode.INSERT)).isTrue();
        assertThat(kv.setEx(b("k1"), b("v2"), UpdateMode.INSERT)).isFalse();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void set_update_mode_does_not_insert_if_key_is_not_present_and_returns_false() throws IOException {
        assertThat(kv.setEx(b("k1"), b("v1"), UpdateMode.UPDATE)).isFalse();
        assertThat(kv.get(b("k1"))).isEmpty();
    }

    @Test
    void set_update_mode_updates_value_if_key_is_present_and_returns_true() throws IOException {
        assertThat(kv.setEx(b("k1"), b("v1"), UpdateMode.INSERT)).isTrue();
        assertThat(kv.setEx(b("k1"), b("new value"), UpdateMode.UPDATE)).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("new value"));
    }
}
