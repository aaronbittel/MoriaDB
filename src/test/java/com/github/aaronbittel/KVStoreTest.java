package com.github.aaronbittel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KVStoreTest {

    KVStore kv;

    @BeforeEach
    void setup() {
        kv = new KVStore();
    }

    @Test
    void Set_key_can_be_retrieved() {
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    @Test
    void Unknown_key_returns_empty_result() {
        assertThat(kv.get(b("xxx"))).isEmpty();
        assertThat(kv.delete(b("xxx"))).isFalse();
    }

    @Test
    void Deleted_key_cannot_be_retrieved() {
        kv.set(b("k1"), b("v1"));

        assertThat(kv.delete(b("k1"))).isTrue();
        assertThat(kv.get(b("k1"))).isEmpty();
    }

    @Test
    void Updating_key_returns_new_value() {
        assertThat(kv.set(b("k1"), b("v1"))).isFalse();
        assertThat(kv.set(b("k1"), b("v2"))).isTrue();
        assertThat(kv.get(b("k1"))).hasValue(b("v2"));
    }

    @Test
    void Updating_key_after_insertion_does_not_modify_key() {
        byte[] key = b("k1");
        byte[] value = b("v1");

        kv.set(key, value);
        key[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(value);
        assertThat(kv.get(key)).isEmpty();
    }

    @Test
    void Updating_value_after_insertion_does_not_modify_value() {
        byte[] key = b("k1");
        byte[] value = b("v1");

        kv.set(key, value);
        value[0] = 'n';
        assertThat(kv.get(b("k1"))).hasValue(b("v1"));
    }

    static byte[] b(String s) {
        return s.getBytes();
    }
}
