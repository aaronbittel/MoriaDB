package com.github.aaronbittel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        String tableName = stmt.tableName();
        List<String> columns = stmt.columns().stream().map(Column::name).toList();

        if (getSchema(tableName).isPresent()) {
            throw new IllegalArgumentException(
                "Table '" + tableName + "' already exists");
        }

        List<Integer> primaryKeysIdxs = new ArrayList<>();
        for (String primaryKey : stmt.primaryKeys()) {
            int idx = columns.indexOf(primaryKey);
            if (idx == -1) {
                throw new IllegalArgumentException(
                    "Missing column name for primary key: " + primaryKey
                );
            }
            primaryKeysIdxs.add(idx);
        }

        Schema schema = new Schema(tableName, stmt.columns(), primaryKeysIdxs);

        byte[] schemaKey = ("@schema_" + tableName).getBytes();
        try {
            byte[] schemaData = mapper.writeValueAsBytes(schema);
            kv.setEx(schemaKey, schemaData, UpdateMode.INSERT);
            tables.put(tableName, schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Schema could not be converted to json as bytes: " + schema, e
            );
        }

        return SQLResult.of();
    }

    private SQLResult execInsert(StmtInsert stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );
        List<Column> columns = schema.columns();

        // check that provided values (types) match with schema
        // TODO: What about primary keys (auto-increment), or optional fields (later)
        if (columns.size() != stmt.values().size()) {
            throw new IllegalArgumentException(
                "Number of columns in insert statement, "
                + "do not match up with number of columns the table has");
        }

        List<Cell> cells = new ArrayList<>(columns.size());

        for (int i = 0; i < columns.size(); ++i) {
            Column column = columns.get(i);
            Cell cell = stmt.values().get(i);
            if (column.type() != cell.type()) {
                throw new IllegalArgumentException(
                    "Expected column type '" + column.type()
                    + "' for column '" + column.name() + "', but got '" + cell.type());
            }
            cells.add(cell);
        }

        if (insert(schema, new Row(cells))) {
            return new SQLResult(1, List.of(), List.of());
        }
        return SQLResult.of();
    }

    private SQLResult execSelect(StmtSelect stmt) {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );
        List<String> selectedColumns = stmt.columns();
        List<Column> columns = schema.columns();

        // TODO: put this on schema?
        List<String> columnNames = columns.stream().map(Column::name).toList();

        List<Integer> indices = new ArrayList<>();

        // check that all selected columns exist
        for (String column : selectedColumns) {
            int schemaIdx = columnNames.indexOf(column);
            if (schemaIdx == -1) {
                throw new IllegalArgumentException(
                    "selected column '" + column
                    + "' does not exist in table '" + stmt.tableName());
            }
            indices.add(schemaIdx);
        }
        Collections.sort(indices);

        Row row = makePrimaryKey(schema, stmt.keys());

        if (!select(schema, row)) {
            return SQLResult.of();
        }

        Row selectedRow = row.selectSubset(indices);

        return new SQLResult(0, selectedColumns, List.of(selectedRow));
    }

    private SQLResult execUpdate(StmtUpdate stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        // check that all provided columns exist
        for (NamedCell value : stmt.values()) {
            boolean found = false;
            for (Column column : schema.columns()) {
                if (column.name().equals(value.column())
                    && column.type() == value.value().type())
                {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new IllegalArgumentException(
                    "The provided column '" + value.column() + "' does not exist");
            }
        }

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

        Row row = makePrimaryKey(schema, stmt.keys());

        if (!delete(schema, row)) {
            return SQLResult.of();
        }

        return new SQLResult(1, List.of(), List.of());
    }

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

        List<Column> missingPks = new ArrayList<>();
        for (int i = 0; i < schema.columns().size(); ++i) {
            if (!schema.primaryKeys().contains(i)) continue;

            Column pk = schema.columns().get(i);
            boolean found = false;
            for (NamedCell key : keys) {
                if (pk.name().equals(key.column()) && pk.type() == key.value().type()) {
                    row.set(i, key.value());
                    found = true;
                    break;
                }
            }
            if (!found) {
                missingPks.add(pk);
            }
        }

        if (!missingPks.isEmpty()) {
            String missingPksStr = missingPks.stream()
                .map(Column::name)
                .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Currently it is necessary to provide all primary keys "
                + "in the WHERE-clause. The following primary keys are missing: "
                + missingPksStr);
        }

        return row;
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
