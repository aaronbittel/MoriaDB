package com.github.aaronbittel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
    void matches_keywords_case_insensitively_and_rejects_partial_matches() {
        Parser parser = new Parser(" select  HELLO ");
        assertThat(parser.tryKeyword("sel")).isFalse();
        assertThat(parser.tryKeyword("SELECT")).isTrue();
        assertThat(parser.tryKeyword("hello")).isTrue();
    }
}

