package com.github.aaronbittel.parser;

import java.util.Optional;

public class Parser {

    private final String source;
    private int pos = 0;

    public Parser(String source) {
        this.source = source;
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

        int start = pos;

        while (!isSeparator(current())) {
            advance();
        }

        int end = pos;

        String candidate = source.substring(start, end).toUpperCase();

        if (keyword.toUpperCase().equals(candidate)) {
            return true;
        }

        pos = start;
        return false;
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
