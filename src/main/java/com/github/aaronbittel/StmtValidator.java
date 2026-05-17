package com.github.aaronbittel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.github.aaronbittel.parser.NamedCell;
import com.github.aaronbittel.parser.StmtCreateTable;
import com.github.aaronbittel.parser.StmtDelete;
import com.github.aaronbittel.parser.StmtInsert;
import com.github.aaronbittel.parser.StmtSelect;
import com.github.aaronbittel.parser.StmtUpdate;
import com.github.aaronbittel.table.CellType;
import com.github.aaronbittel.table.Column;
import com.github.aaronbittel.table.Schema;

public class StmtValidator {

    public static void validateCreateTable(StmtCreateTable stmt) {
        List<String> columnNames = stmt
            .columns()
            .stream()
            .map(Column::name)
            .toList();

        requireNoDuplicates(columnNames, "The following columns are duplicated");
        requireNoDuplicates(stmt.primaryKeys(), "The following primary keys are duplicated");
        requirePrimaryKeysExist(stmt.primaryKeys(), columnNames);
    }

    public static void validateInsert(Schema schema, StmtInsert stmt) {
        int expectedSize = schema.columns().size();
        int providedSize = stmt.values().size();
        int minLength = Math.min(expectedSize, providedSize);

        List<String> mismatches = new ArrayList<>(minLength);
        for (int i = 0; i < minLength; ++i) {
            Column expectedColumn = schema.columns().get(i);
            Cell providedValue = stmt.values().get(i);
            if (expectedColumn.type() != providedValue.type()) {
                mismatches.add(
                    "Column '%s' -> expected type '%s', but got '%s'"
                        .formatted(
                            expectedColumn.name(),
                            expectedColumn.type(),
                            providedValue.type()));
            }
        }

        requireEmpty(mismatches,
            "For the following columns the expected and received column types "
            + "did not match");

        if (expectedSize > providedSize) {
            String message = schema.columns()
                .stream()
                .skip(providedSize)
                .map(col -> "- %s (%s)".formatted(col.name(), col.type()))
                .collect(Collectors.joining("\n"));
            throw new IllegalArgumentException(
                "Value for the following columns is missing: " + message);
        }

        if (expectedSize < providedSize) {
            throw new IllegalArgumentException(
                String.format(
                    "%d values were provided, but table only has %d columns",
                    providedSize, expectedSize));
        }
    }

    public static void validateSelect(Schema schema, StmtSelect stmt) {
        requireAllSelectedColumnsExist(schema, stmt);
        requireNoPrimaryKeysMissing(stmt.keys(), schema.getPrimaryKeyColumns());
    }

    public static void validateUpdate(Schema schema, StmtUpdate stmt) {
        List<String> providedKeys = stmt
            .keys()
            .stream()
            .map(NamedCell::column)
            .toList();

        requireNoDuplicates(providedKeys,
            "Duplicate keys are not allowed in the WHERE clause. Duplicated keys");

        List<String> providedValues = stmt
            .values()
            .stream()
            .map(NamedCell::column)
            .toList();

        requireNoDuplicates(providedValues,
            "The following values are duplicated in the set section");

        requireNoPrimaryKeysMissing(stmt.keys(), schema.getPrimaryKeyColumns());
        requireAllSetValuesExist(schema.columns(), stmt.values());
        requireNoPrimaryKeyInValueList(stmt.values(), schema.getPrimaryKeyNames());
        requireAllNonPrimaryKeysInSetList(schema, stmt.values());
    }

    public static void validateDelete(Schema schema, StmtDelete stmt) {
        List<String> keyNames = stmt.keys()
                .stream()
                .map(NamedCell::column)
                .toList();
        requireNoDuplicates(keyNames, "The following keys are duplicated");

        requireCompletePrimaryKeyWhereClause(schema, stmt.keys());
    }

    private static void requireCompletePrimaryKeyWhereClause(
        Schema schema, List<NamedCell> keys)
    {
        Map<String, CellType> primaryKeys = schema.getPrimaryKeyColumns().stream()
            .collect(Collectors.toMap(Column::name, Column::type));

        List<String> unknownKeys = keys.stream()
            .map(NamedCell::column)
            .collect(Collectors.toList());

        for (NamedCell key : keys) {
            if (primaryKeys.remove(key.column(), key.value().type())) {
                unknownKeys.remove(key.column());
            }
        }

        List<String> primaryKeyErrors = primaryKeys.entrySet()
            .stream()
            .map(entry -> "%s is missing or has wrong type (%s)"
                .formatted(entry.getKey(), entry.getValue()))
            .toList();

        requireEmpty(primaryKeyErrors,
            "The following primary keys are missing from the where-clause");

        requireEmpty(unknownKeys,
            "The following columns are no primary keys of the table");
    }

    private static void requireNoPrimaryKeyInValueList(
        List<NamedCell> setValues, List<String> primaryKeyNames)
    {
        List<String> primaryKeysInValueList = setValues.stream()
            .map(NamedCell::column)
            .filter(primaryKeyNames::contains)
            .toList();

        requireEmpty(primaryKeysInValueList,
            "Updating primary key values is not allowed. "
            + "The following primary keys were listed");

    }

    private static void requireAllNonPrimaryKeysInSetList(
        Schema schema, List<NamedCell> setValues)
    {
        List<String> missingUpdateValues = new ArrayList<>();
        for (int i = 0; i < schema.columns().size(); ++i) {
            if (schema.primaryKeys().contains(i)) continue;
            Column column = schema.columns().get(i);
            boolean found = setValues.stream()
                .anyMatch(setValue -> columnMatchesNamedCell(column, setValue));
            if (!found) {
                missingUpdateValues.add(column.name());
            }
        }
        requireEmpty(missingUpdateValues,
            "Currently to update a row all non-primary key columns must be provided. "
            + "The following columns are missing");
    }

    private static void requireAllSetValuesExist(
        List<Column> columns, List<NamedCell> setValues)
    {
        List<String> unknownSetValues = new ArrayList<>();
        for (NamedCell setValue : setValues) {
            boolean found = columns.stream()
                .anyMatch(column -> columnMatchesNamedCell(column, setValue));
            if (!found) {
                unknownSetValues.add(setValue.column());
            }
        }

        requireEmpty(unknownSetValues, "The following columns do not exist");
    }

    private static void requireNoPrimaryKeysMissing(
        List<NamedCell> keys, List<Column> primaryKeyColumns)
    {
        List<String> missingPrimaryKeys = new ArrayList<>();
        for (Column pkColumn : primaryKeyColumns) {
            boolean found = keys.stream()
                .anyMatch(key -> columnMatchesNamedCell(pkColumn, key));
            if (!found) {
                missingPrimaryKeys.add(pkColumn.name());
            }
        }
        requireEmpty(missingPrimaryKeys,
            "Currently it is necessary to provide all primary keys "
            + "in the select statement. The following primary keys are missing "
            + "in the where clause");
    }

    private static void requireAllSelectedColumnsExist(Schema schema, StmtSelect stmt) {
        List<String> columnNames = schema.columns()
            .stream()
            .map(Column::name)
            .toList();

        List<String> unknownSelectedColumns = new ArrayList<>();
        for (String column : stmt.columns()) {
            if (!columnNames.contains(column)) {
                unknownSelectedColumns.add(column);
            }
        }
        requireEmpty(unknownSelectedColumns, "The following columns do not exist");
    }

    private static void requirePrimaryKeysExist(
        List<String> keys, List<String> columnNames)
    {
        List<String> missingPrimaryKeys = keys.stream()
            .filter(Predicate.not(columnNames::contains))
            .toList();
        requireEmpty(missingPrimaryKeys, "The following primary keys are missing: ");
    }

    private static void requireNoDuplicates(List<String> values, String message) {
        List<String> duplicates = getDuplicates(values);
        requireEmpty(duplicates, message);
    }

    private static List<String> getDuplicates(List<String> values) {
        List<String> duplicates = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                duplicates.add(value);
            }
        }
        return duplicates;
    }

    private static void requireEmpty(Collection<String> values, String message) {
        if (!values.isEmpty()) {
            throw new IllegalArgumentException(message + ": " + String.join(", ", values));
        }
    }

    public static boolean columnMatchesNamedCell(Column column, NamedCell namedCell) {
        return column.name().equals(namedCell.column())
            && column.type() == namedCell.value().type();
    }
}
