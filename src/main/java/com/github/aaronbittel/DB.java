package com.github.aaronbittel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.aaronbittel.parser.NamedCell;
import com.github.aaronbittel.parser.Stmt;
import com.github.aaronbittel.parser.StmtCreateTable;
import com.github.aaronbittel.parser.StmtDelete;
import com.github.aaronbittel.parser.StmtInsert;
import com.github.aaronbittel.parser.StmtSelect;
import com.github.aaronbittel.parser.StmtUpdate;
import com.github.aaronbittel.table.Column;
import com.github.aaronbittel.table.Row;
import com.github.aaronbittel.table.Schema;

public class DB {

    private final KVStore kv;
    private final Map<String, Schema> tables = new HashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public DB(KVStore kv) {
        this.kv = kv;
    }

    public void open() throws IOException {
        kv.open();
    }

    public void close() throws IOException {
        kv.close();
    }

    public SQLResult execStmt(Stmt stmt) throws IOException {
        return switch (stmt) {
            case StmtCreateTable createTable -> execCreateTable(createTable);
            case StmtInsert      insert      -> execInsert(insert);
            case StmtSelect      select      -> execSelect(select);
            case StmtUpdate      update      -> execUpdate(update);
            case StmtDelete      delete      -> execDelete(delete);
        };
    }

    private SQLResult execCreateTable(StmtCreateTable stmt) throws IOException {
        if (getSchema(stmt.tableName()).isPresent()) {
            throw new IllegalArgumentException(
                "Table '" + stmt.tableName() + "' already exists");
        }

        StmtValidator.validateCreateTable(stmt);
        storeSchema(Schema.of(stmt));

        return SQLResult.of();
    }

    private SQLResult execInsert(StmtInsert stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        StmtValidator.validateInsert(schema, stmt);

        if (insert(schema, new Row(stmt.values()))) {
            return new SQLResult(1, List.of(), List.of());
        }

        throw new IllegalArgumentException("A row with this primary key already exists");
    }

    private SQLResult execSelect(StmtSelect stmt) {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        StmtValidator.validateSelect(schema, stmt);

        List<String> selectedColumns = stmt.columns();
        List<Column> columns = schema.columns();

        // TODO: put this on schema?
        List<String> columnNames = columns.stream().map(Column::name).toList();

        List<Integer> indices = lookupColumns(columnNames, selectedColumns);

        Row row = makePrimaryKey(schema, stmt.keys());

        if (!select(schema, row)) {
            return new SQLResult(0, selectedColumns, List.of());
        }

        Row selectedRow = row.selectSubset(indices);

        return new SQLResult(0, selectedColumns, List.of(selectedRow));
    }

    private SQLResult execUpdate(StmtUpdate stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        StmtValidator.validateUpdate(schema, stmt);

        Row row = makePrimaryKey(schema, stmt.keys());
        fillNonPrimaryKey(schema, stmt.values(), row);

        if (!update(schema, row)) {
            return SQLResult.of();
        }

        return new SQLResult(1, List.of(), List.of());
    }

    public SQLResult execDelete(StmtDelete stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        StmtValidator.validateDelete(schema, stmt);

        Row row = makePrimaryKey(schema, stmt.keys());

        if (!delete(schema, row)) {
            return SQLResult.of();
        }

        return new SQLResult(1, List.of(), List.of());
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

    private void storeSchema(Schema schema) throws IOException {
        byte[] schemaKey = ("@schema_" + schema.tableName()).getBytes();
        try {
            byte[] schemaData = mapper.writeValueAsBytes(schema);
            kv.setEx(schemaKey, schemaData, UpdateMode.INSERT);
            tables.put(schema.tableName(), schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Schema could not be converted to json as bytes: " + schema, e
            );
        }
    }

    // Should this be private?
    public Optional<Schema> getSchema(String tableName) {
        if (tables.containsKey(tableName)) return Optional.of(tables.get(tableName));

        byte[] schemaKey = ("@schema_" + tableName).getBytes();
        Optional<byte[]> schemaValue = kv.get(schemaKey);

        if (schemaValue.isEmpty()) return Optional.empty();

        try {
            Schema schema = mapper.readValue(schemaValue.orElseThrow(), Schema.class);
            tables.put(tableName, schema);
            return Optional.of(schema);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Corrupted schema for table: " + tableName, e
            );
        }
    }

    private List<Integer> lookupColumns(
        List<String> columns, List<String> selectedColumns)
    {
        List<Integer> indices = new ArrayList<>();
        for (String column : selectedColumns) {
            int index = columns.indexOf(column);
            if (index == -1) {
                throw new IllegalStateException(
                    "Validation error: selected column '"
                    + column + "' does not exist in the schema");
            }
            indices.add(index);
        }
        return indices;
    }

    private void fillNonPrimaryKey(Schema schema, List<NamedCell> values, Row row) {
        for (int i = 0; i < schema.columns().size(); ++i) {
            if (schema.primaryKeys().contains(i)) continue;

            Column col = schema.columns().get(i);
            for (NamedCell value : values) {
                if (col.name().equals(value.column()) && col.type() == value.value().type()) {
                    row.set(i, value.value());
                    break;
                }
            }
        }
    }

    private Row makePrimaryKey(Schema schema, List<NamedCell> keys) {
        Row row = new Row(schema.columns().size());

        for (int i = 0; i < schema.columns().size(); ++i) {
            if (!schema.primaryKeys().contains(i)) continue;

            Column pk = schema.columns().get(i);
            boolean found = false;
            for (NamedCell key : keys) {
                if (StmtValidator.columnMatchesNamedCell(pk, key)) {
                    row.set(i, key.value());
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("validated stmt missing pk");
            }
        }

        return row;
    }
}
