package com.github.aaronbittel.parser;

import static com.github.aaronbittel.TestBytes.b;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.aaronbittel.Cell;

class ParserTest {

    @Test
    void parses_single_identifier() {
        Parser parser = new Parser("a");
        assertThat(parser.tryName()).hasValue("a");
    }

    @Test
    void parses_multiple_identifiers_separated_by_whitespace() {
        Parser parser = new Parser("a b0 _0_");
        assertThat(parser.tryName()).hasValue("a");
        assertThat(parser.tryName()).hasValue("b0");
        assertThat(parser.tryName()).hasValue("_0_");
    }

    @Test
    void returns_empty_when_no_more_identifiers() {
        Parser parser = new Parser("a");
        parser.tryName();
        assertThat(parser.tryName()).isEmpty();
    }

    @Test
    void matches_keywords_case_insensitively_and_requires_full_match() {
        Parser parser = new Parser(" select  HELLO ");
        assertThat(parser.tryKeyword("sel")).isFalse();
        assertThat(parser.tryKeyword("SELECT")).isTrue();
        assertThat(parser.tryKeyword("hello")).isTrue();
    }

    @Test
    void throws_exception_when_no_valid_value_can_be_parsed() {
        Parser parser = new Parser("abc");
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as("No valid Int nor String")
            .isThrownBy(parser::parseValue);
    }

    static Stream<Arguments> numberCases() {
        return Stream.of(
            Arguments.of(" -123 ", new Cell.Int(-123)),
            Arguments.of("0", new Cell.Int(0)),
            Arguments.of("-0", new Cell.Int(0)),
            Arguments.of("+0", new Cell.Int(0)),
            Arguments.of(" 5", new Cell.Int(5)),
            Arguments.of("0012412  ", new Cell.Int(12412)),
            Arguments.of("+5124", new Cell.Int(5124))
        );
    }

    @ParameterizedTest
    @MethodSource("numberCases")
    void parse_numbers_correctly_as_int_cell(String value, Cell expected) {
        Parser parser = new Parser(value);
        assertThat(parser.parseValue()).isEqualTo(expected);
    }

    static Stream<Arguments> badNumberCases() {
        return Stream.of(
            Arguments.of("+", "sign without digits"),
            Arguments.of("-", "sign without digits"),
            Arguments.of("+-1241", "invalid sign sequence '+-'"),
            Arguments.of("+ 1241", "space between sign and digits is invalid")
        );
    }

    @ParameterizedTest
    @MethodSource("badNumberCases")
    void parse_number_throws_exception(String value, String description) {
        Parser parser = new Parser(value);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseValue);
    }

    static Stream<Arguments> stringCases() {
        return Stream.of(
            Arguments.of("  \"hello\"   ", new Cell.Str(b("hello"))),
            Arguments.of("'hi'", new Cell.Str(b("hi"))),
            Arguments.of("   \"abc\\'d\"", new Cell.Str(b("abc'd"))),
            Arguments.of("'hel\\\\lo'", new Cell.Str(b("hel\\lo")))
        );
    }

    @ParameterizedTest
    @MethodSource("stringCases")
    void parse_strings_correctly_as_str_cell(String value, Cell expected) {
        Parser parser = new Parser(value);
        assertThat(parser.parseValue()).isEqualTo(expected);
    }

    static Stream<Arguments> badStringCases() {
        return Stream.of(
            Arguments.of("\"abc", "Missing terminating quote"),
            Arguments.of("'abc\"'", "Unescaped \" inside string"),
            Arguments.of("abc\\b", "Illegal escaped character 'b'")
        );
    }

    @ParameterizedTest
    @MethodSource("badStringCases")
    void parse_throws_expected_exception_when_string_is_illegal(
        String value, String description) {

        Parser parser = new Parser(value);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseValue);
    }
}

