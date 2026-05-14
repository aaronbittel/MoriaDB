package com.github.aaronbittel.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.aaronbittel.Cell;
import com.github.aaronbittel.table.CellType;
import com.github.aaronbittel.table.Column;

public class Parser {

    private final String source;
    private int pos = 0;

    public Parser(String source) {
        this.source = source;
    }

    public StmtCreateTable parseCreateTable() {
        expectKeyword("CREATE");
        expectKeyword("TABLE");

        String tableName = expectName("Create Table Statement must have a tableName");

        expectPunctuation("(", "Missing '(' for columns");

        List<Column> columns = new ArrayList<>();

        do {
            String columnName = expectName("Expected column name");
            CellType type = CellType.of(expectName("Expected column type"));
            expectPunctuation(",", "Expected ',' after column type");
            columns.add(new Column(columnName, type));
        } while (!tryKeyword("primary key"));

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (Column column : columns) {
            if (!seen.add(column.name())) {
                duplicates.add(column.name());
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                "Columns '%s' are duplicated".formatted(String.join(", ", duplicates)));
        }

        expectPunctuation("(", "Expected '(' for primary keys");

        List<String> primaryKeys = new ArrayList<>();

        do {
            String primaryKey = expectName("Expecting at least one primary key column");

            boolean valid = false;
            for (Column column : columns) {
                if (column.name().equals(primaryKey)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new IllegalArgumentException(
                    "Primary Key '%s' was provided, but there is no such column"
                        .formatted(primaryKey));
            }

            primaryKeys.add(primaryKey);
        } while (tryPunctuation(","));

        seen = new HashSet<>();
        duplicates = new HashSet<>();

        for (String pk : primaryKeys) {
            if (!seen.add(pk)) {
                duplicates.add(pk);
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                "Primary Keys '%s' are duplicated"
                    .formatted(String.join(", ", duplicates)));
        }

        expectPunctuation(")", "Expected ')' to close primary key list");
        expectPunctuation(")", "Expected ')' to close Create Table Statement");
        expectPunctuation(";", "Expected ';' at the end of create table statement");

        return new StmtCreateTable(tableName, columns, primaryKeys);
    }

    public StmtInsert parseInsert() {
        expectKeyword("INSERT");
        expectKeyword("INTO");

        String tableName = expectName("Missing table name");

        expectKeyword("VALUES");

        expectPunctuation("(", "Expected '(' to start values list");

        List<Cell> values = new ArrayList<>();
        do {
            values.add(parseValue());
        } while (tryPunctuation(","));

        expectPunctuation(")", "Expected ')' to end values list");

        expectPunctuation(";", "Expected ';' at the end of insert statement");

        return new StmtInsert(tableName, values);
    }

    public StmtUpdate parseUpdate() {
        expectKeyword("UPDATE");

        String tableName = expectName("Missing table name");

        expectKeyword("SET");

        List<NamedCell> values = new ArrayList<>();
        do {
            values.add(parseAssignment());
        } while (tryPunctuation(","));

        expectKeyword("WHERE");

        List<NamedCell> keys = new ArrayList<>();
        do {
            keys.add(parseAssignment());
        } while (tryKeyword("AND"));

        expectPunctuation(";", "Expected ';' at the end of update statement");

        return new StmtUpdate(tableName, keys, values);
    }

    public StmtDelete parseDelete() {
        expectKeyword("DELETE");
        expectKeyword("FROM");

        String tableName = expectName("Missing table name");

        expectKeyword("WHERE");

        List<NamedCell> keys = new ArrayList<>();
        do {
            keys.add(parseAssignment());
        } while (tryKeyword("AND"));

        expectPunctuation(";", "Expected ';' at the end of delete statement");

        return new StmtDelete(tableName, keys);
    }

    public StmtSelect parseSelect() {
        expectKeyword("SELECT");

        List<String> columns = new ArrayList<>();
        String columnName = expectName("At least one column must be selected");
        columns.add(columnName);

        while (tryPunctuation(",")) {
            columnName = expectName("trailing ',' is not allowed in columns list");
            columns.add(columnName);
        }

        expectKeyword("FROM");

        String tableName = expectName("No table name provided");

        List<NamedCell> namedCells = new ArrayList<>();

        if (tryKeyword("WHERE")) {
            do {
                namedCells.add(parseAssignment());
            } while (tryKeyword("AND"));
        }

        expectPunctuation(";", "Expected ';' at the end of select statement");

        return new StmtSelect(tableName, columns, namedCells);
    }

    public Optional<String> tryName() {
        skipWhitespace();

        int start = pos;

        if (!isNameStart(current())) {
            return Optional.empty();
        }

        do {
            advance();
        } while (isNameContinue(current()));

        int end = pos;

        return Optional.of(source.substring(start, end));
    }

    public boolean tryKeyword(String keyword) {
        skipWhitespace();

        if (keyword.length() > remainingLength()) return false;

        String candidate = source.substring(pos, pos + keyword.length());

        if (!keyword.equalsIgnoreCase(candidate)) return false;
        if (!isSeparator(source.charAt(pos + keyword.length()))) return false;

        pos += keyword.length();

        return true;
    }

    public Cell parseValue() {
        skipWhitespace();

        char c = current();
        if (c == '"' || c == '\'') {
            return parseString();
        } else if (Character.isDigit(c) || c == '+' || c == '-') {
            return parseInt();
        } else {
            throw new IllegalArgumentException(
                "Illegal character at pos=" + pos + ". Expected 'String' or 'Int'");
        }
    }

    private NamedCell parseAssignment() {
        skipWhitespace();
        String columnName = tryName().orElseThrow(() ->
            new IllegalArgumentException("Expected identifier after WHERE clause"));

        if (!tryPunctuation("=")) {
            throw new IllegalArgumentException("Expected '=' for WHERE clause");
        }

        Cell cell = parseValue();

        return new NamedCell(columnName, cell);
    }

    private Cell parseString() {
        StringBuilder sb = new StringBuilder();

        char startingQuote = current();
        advance();

        while (!isEnd() && current() != startingQuote) {
            char cur = current();
            switch (cur) {
                case '\\' -> {
                    advance();
                    char next = current();
                    if (next == '\\' || next == '\"' || next == '\'') {
                        sb.append(next);
                    } else {
                        throw new IllegalArgumentException(
                            "Unknown escape sequence " + "\\" + next);
                    }
                }
                case '\'', '\"' -> throw new IllegalArgumentException(
                    "Quotes '//'', '\"' inside of strings must be escaped"
                );
                default -> sb.append(cur);
            }
            advance();
        }

        if (current() != startingQuote) {
            throw new IllegalArgumentException("Unterminated string literal");
        }
        advance();

        return new Cell.Str(sb.toString().getBytes());
    }

    private Cell parseInt() {
        int start = pos;
        do {
            advance();
        } while (Character.isDigit(current()));
        int end = pos;
        return new Cell.Int(Long.parseLong(source.substring(start, end)));
    }

    private boolean tryPunctuation(String punct) {
        skipWhitespace();

        if (punct.length() > remainingLength()) return false;
        if (!punct.equals(source.substring(pos, pos + punct.length()))) return false;

        pos += punct.length();
        return true;
    }

    private String expectName(String msg) {
        return tryName().orElseThrow(() -> new IllegalArgumentException(msg));
    }

    private void expectKeyword(String keyword) {
        if (!tryKeyword(keyword)) {
            throw new IllegalArgumentException("Expected '" + keyword + "' keyword");
        }
    }

    private void expectPunctuation(String punct, String msg) {
        if (!tryPunctuation(punct)) {
            throw new IllegalArgumentException(msg);
        }
    }

    private int remainingLength() {
        return source.length() - pos;
    }

    private static boolean isSeparator(char c) {
        return c < 128 && !isNameContinue(c);
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameContinue(char c) {
        return Character.isLetter(c) || Character.isDigit(c) || c == '_';
    }

    private void advance() {
        if (isEnd()) return;
        pos++;
    }

    private char current() {
        return isEnd() ? 0 : source.charAt(pos);
    }

    private void skipWhitespace() {
        if (isEnd()) return;
        while (Character.isWhitespace(current())) {
            pos++;
        }
    }

    private boolean isEnd() {
        return pos >= source.length();
    }
}
