package com.github.aaronbittel;

import com.github.aaronbittel.table.Row;
import com.github.aaronbittel.table.Schema;
import java.io.IOException;
import java.util.Optional;

public class DB {

    private final KVStore kv;

    public DB(KVStore kv) {
        this.kv = kv;
    }

    public void open() throws IOException {
        kv.open();
    }

    public void close() throws IOException {
        kv.close();
    }

    public boolean select(Schema schema, Row row) {
        Optional<byte[]> value = kv.get(row.encodeKey(schema));
        if (value.isEmpty()) return false;

        row.decodeVal(schema, value.get());
        return true;
    }

    public boolean insert(Schema schema, Row row) throws IOException {
        byte[] key = row.encodeKey(schema);
        byte[] value = row.encodeVal(schema);
        return kv.setEx(key, value, UpdateMode.INSERT);
    }

    public boolean upsert(Schema schema, Row row) throws IOException {
        byte[] key = row.encodeKey(schema);
        byte[] value = row.encodeVal(schema);
        return kv.set(key, value);
    }

    public boolean update(Schema schema, Row row) throws IOException {
        byte[] key = row.encodeKey(schema);
        byte[] value = row.encodeVal(schema);
        return kv.setEx(key, value, UpdateMode.UPDATE);
    }

    public boolean delete(Schema schema, Row row) throws IOException {
        byte[] key = row.encodeKey(schema);
        return kv.delete(key);
    }
}
