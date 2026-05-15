package com.github.aaronbittel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.github.aaronbittel.table.CellType;
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

        if (getSchema(tableName).isPresent()) {
            throw new IllegalArgumentException(
                "Table '" + tableName + "' already exists");
        }

        validateCreateTable(stmt);

        List<String> columnNames = stmt.columns().stream().map(Column::name).toList();
        List<Integer> primaryKeysIdxs = getPrimaryKeysIdxs(stmt, columnNames);

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

    private List<Integer> getPrimaryKeysIdxs(
        StmtCreateTable stmt, List<String> columns)
    {
        List<Integer> primaryKeysIdxs = new ArrayList<>();
        for (String primaryKey : stmt.primaryKeys()) {
            int idx = columns.indexOf(primaryKey);
            if (idx == -1) {
                throw new IllegalStateException(
                    "Invariant violation: primary key '" + primaryKey
                    + "' not found after validation");
            }
            primaryKeysIdxs.add(idx);
        }

        return primaryKeysIdxs.stream().distinct().sorted().toList();
    }

    private void validateCreateTable(StmtCreateTable stmt) {
        List<String> columns = stmt
            .columns()
            .stream()
            .map(Column::name)
            .toList();

        List<String> duplicateColumns = getDuplicates(columns);
        if (!duplicateColumns.isEmpty()) {
            throw new IllegalArgumentException(
                "The following columns are duplicated: "
                + String.join(", ", duplicateColumns));
        }

        List<String> duplicatePrimaryKeys = getDuplicates(stmt.primaryKeys());
        if (!duplicatePrimaryKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "The following primary keys are duplicated: "
                + String.join(", ", duplicatePrimaryKeys));
        }

        List<String> missingPrimaryKeys = new ArrayList<>();
        for (String primaryKey : stmt.primaryKeys()) {
            int idx = columns.indexOf(primaryKey);
            if (idx == -1) missingPrimaryKeys.add(primaryKey);
        }
        if (!missingPrimaryKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "The following primary keys are missing: "
                + String.join(", ", missingPrimaryKeys));
        }
    }

    private void validateSelect(Schema schema, StmtSelect stmt) {
        List<Column> columns = schema.columns();
        List<String> columnNames = columns.stream().map(Column::name).toList();

        // check that all selected columns exist
        List<String> unknownSelectedColumns = new ArrayList<>();
        for (String column : stmt.columns()) {
            if (columnNames.indexOf(column) == -1) {
                unknownSelectedColumns.add(column);
            }
        }
        if (!unknownSelectedColumns.isEmpty()) {
            throw new IllegalArgumentException(
                "selected column(s) '" + String.join(", ", unknownSelectedColumns)
                + "' do not exist in table '" + stmt.tableName());
        }

        // getAllPrimaryKeys as Columns
        List<Column> primaryKeyColumns = new ArrayList<>(schema.primaryKeys().size());
        for (Integer idx : schema.primaryKeys()) {
            primaryKeyColumns.add(schema.columns().get(idx));
        }

        // check if complete primary key is present
        List<String> missingPrimaryKeys = new ArrayList<>();
        for (Column pkColumn : primaryKeyColumns) {
            boolean found = false;
            for (NamedCell cell : stmt.keys()) {
                if (pkColumn.name().equals(cell.column())
                    && pkColumn.type() == cell.value().type())
                {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missingPrimaryKeys.add(pkColumn.name());
            }
        }
        if (!missingPrimaryKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "Currently it is necessary to provide all primary keys "
                + "in the select statement. The following primary keys are missing "
                + "in the where clause: "
                + String.join(", ", missingPrimaryKeys));
        }
    }

    private List<String> getDuplicates(List<String> elements) {
        List<String> duplicates = new ArrayList<>(elements.size());
        Set<String> seen = new HashSet<>();
        for (String elem : elements) {
            if (!seen.add(elem)) {
                duplicates.add(elem);
            }
        }
        return duplicates;
    }

    private SQLResult execInsert(StmtInsert stmt) throws IOException {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        validateInsert(schema, stmt);

        if (insert(schema, new Row(stmt.values()))) {
            return new SQLResult(1, List.of(), List.of());
        }

        throw new IllegalArgumentException("A row with this primary key already exists");
    }

    private void validateInsert(Schema schema, StmtInsert insert) {
        record ColumnMisMatch(String name, CellType expected, CellType got) {

            @Override
            public String toString() {
                return "Column '%s' -> expected type '%s', but got '%s'"
                    .formatted(name, expected, got);
            }
        }

        int expectedSize = schema.columns().size();
        int providedSize = insert.values().size();
        int minLength = expectedSize > providedSize ? providedSize : expectedSize;

        List<ColumnMisMatch> mismatches = new ArrayList<>(minLength);
        for (int i = 0; i < minLength; ++i) {
            Column expectedColumn = schema.columns().get(i);
            Cell providedValue = insert.values().get(i);
            if (expectedColumn.type() != providedValue.type()) {
                mismatches.add(
                    new ColumnMisMatch(
                        expectedColumn.name(),
                        expectedColumn.type(),
                        providedValue.type()));
            }
        }
        if (!mismatches.isEmpty()) {
            String message = mismatches.stream()
                .map(m -> "- " + m)
                .collect(Collectors.joining("\n"));

            throw new IllegalArgumentException(
                "For the following columns the expected and received column types "
                + "did not match up: %n" + message);
        }

        if (expectedSize > providedSize) {
            String message = schema.columns()
                .stream()
                .skip(providedSize)
                .map(col -> "- %s (%s)".formatted(col.name(), col.type()))
                .collect(Collectors.joining("\n"));
            throw new IllegalArgumentException(
                "Value for the following columns in missing: " + message);
        }

        if (expectedSize < providedSize) {
            throw new IllegalArgumentException(
                String.format(
                    "%d values were provided, but table only has %d columns",
                    providedSize, expectedSize));
        }
    }

    private SQLResult execSelect(StmtSelect stmt) {
        Schema schema = getSchema(stmt.tableName()).orElseThrow(() ->
            new IllegalArgumentException("Table '" + stmt.tableName() + "' not found")
        );

        validateSelect(schema, stmt);

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

        validateUpdate(schema, stmt);

        Row row = makePrimaryKey(schema, stmt.keys());
        fillNonPrimaryKey(schema, stmt.values(), row);

        if (!update(schema, row)) {
            return SQLResult.of();
        }

        return new SQLResult(1, List.of(), List.of());
    }

    private void validateUpdate(Schema schema, StmtUpdate update) {
        List<String> providedKeys = update
            .keys()
            .stream()
            .map(NamedCell::column)
            .toList();

        List<String> duplicatedKeys = getDuplicates(providedKeys);
        if (!duplicatedKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "Currently it is only supported to select in where clause "
                + "by primary key which must not be duplicated"
                + "The following keys are duplicated in the where clause: "
                + String.join(", ", duplicatedKeys));
        }

        List<String> providedValues = update
            .values()
            .stream()
            .map(NamedCell::column)
            .toList();
        List<String> duplicatedValues = getDuplicates(providedValues);
        if (!duplicatedValues.isEmpty()) {
            throw new IllegalArgumentException(
                "The following values are duplicated in the set section: "
                + String.join(", ", duplicatedValues));
        }

        for (NamedCell value : update.values()) {
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
