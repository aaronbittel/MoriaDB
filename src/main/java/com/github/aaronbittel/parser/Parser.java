package com.github.aaronbittel.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.aaronbittel.Cell;

public class Parser {

    private final String source;
    private int pos = 0;

    public Parser(String source) {
        this.source = source;
    }

    public StmtSelect parseSelect() {
        if (!tryKeyword("SELECT")) {
            throw new IllegalArgumentException("Expected 'SELECT' keyword");
        }

        List<String> columns = new ArrayList<>();
        String columnName = tryName().orElseThrow(() ->
            new IllegalArgumentException("At least one column must be selected"));
        columns.add(columnName);

        while (tryPunctuation(",")) {
            columnName = tryName().orElseThrow(() ->
                new IllegalArgumentException(
                    "trailing ',' is not allowed in columns list"));
            columns.add(columnName);
        }

        if (!tryKeyword("FROM")) {
            throw new IllegalArgumentException("Expected 'FROM' keyword");
        }

        String tableName = tryName().orElseThrow(() ->
            new IllegalArgumentException("No table name provided"));

        List<NamedCell> namedCells = new ArrayList<>();

        if (tryKeyword("WHERE")) {
            do {
                namedCells.add(parseEqual());
            } while (tryKeyword("AND"));
        }

        if (!tryPunctuation(";")) {
            throw new IllegalArgumentException("Expected ';' at the end of statement");
        }

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

    // TODO: simplify see tryPunctuation
    public boolean tryKeyword(String keyword) {
        skipWhitespace();

        int start = pos;

        while (!isSeparator(current())) {
            advance();
        }

        int end = pos;

        String candidate = source.substring(start, end);

        if (keyword.equalsIgnoreCase(candidate)) {
            return true;
        }

        pos = start;
        return false;
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

    private NamedCell parseEqual() {
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

        if (punct.length() > source.length() - pos) return false;
        if (!punct.equals(source.substring(pos, pos + punct.length()))) return false;

        pos += punct.length();
        return true;
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

    boolean isEnd() {
        return pos >= source.length();
    }
}
