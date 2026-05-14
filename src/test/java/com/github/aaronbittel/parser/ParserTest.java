package com.github.aaronbittel.parser;

import static com.github.aaronbittel.BytesUtility.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.aaronbittel.Cell;
import com.github.aaronbittel.table.CellType;
import com.github.aaronbittel.table.Column;

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
            Arguments.of("0012412  ", new Cell.Int(12_412)),
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
            Arguments.of("  \"hello\"   ", new Cell.Str(bytes("hello"))),
            Arguments.of("'hi'", new Cell.Str(bytes("hi"))),
            Arguments.of("   \"abc\\'d\"", new Cell.Str(bytes("abc'd"))),
            Arguments.of("'hel\\\\lo'", new Cell.Str(bytes("hel\\lo")))
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
        String value, String description)
    {

        Parser parser = new Parser(value);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseValue);
    }

    static Stream<Arguments> validStatements() {
        return Stream.of(
            Arguments.of(
                "insert into t values (1, 'x');",
                new StmtInsert(
                    "t",
                    List.of(new Cell.Int(1), new Cell.Str(bytes("x"))))),
            Arguments.of(
                "update t set value = 123 where id = 1;",
                new StmtUpdate(
                    "t",
                    List.of(new NamedCell("id", new Cell.Int(1))),
                    List.of(new NamedCell("value", new Cell.Int(123))))),
            Arguments.of(
                "delete from t where id = 5;",
                new StmtDelete(
                    "t",
                    List.of(new NamedCell("id", new Cell.Int(5))))),
            Arguments.of(
                """
                create table t (
                    a int64,
                    b string,
                    c string,
                    primary key (b, c)
                );
                """,
                new StmtCreateTable(
                    "t",
                    List.of(
                        new Column("a", CellType.INT),
                        new Column("b", CellType.STR),
                        new Column("c", CellType.STR)
                    ),
                    List.of("b", "c"))),
            Arguments.of(
                "select a,b from t where c=1 and d='e';",
                new StmtSelect(
                    "t",
                    List.of("a", "b"),
                    List.of(
                        new NamedCell("c", new Cell.Int(1)),
                        new NamedCell("d", new Cell.Str(bytes("e"))))))
        );
    }

    @ParameterizedTest
    @MethodSource("validStatements")
    void parses_valid_statements(String stmt, Object expected) {
        Parser parser = new Parser(stmt);
        assertThat(parser.parseStmt()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidStatements() {
        return Stream.of(
            Arguments.of(
                "", "Empty statement"),
            Arguments.of(
                "foobar something;", "Unknown statement keyword"),
            Arguments.of(
                "insert t values (1);", "Invalid insert statement"),
            Arguments.of(
                "update t value = 1 where id = 1;", "Invalid update statement"),
            Arguments.of(
                "delete t where id = 1;", "Invalid delete statement"),
            Arguments.of(
                """
                create table t (
                    a int64,
                    ,
                    c string,
                    primary key (b, c)
                );
                """, "Invalid create table statement"),
            Arguments.of(
                "select a,b from t where c=1 d='e';", "Invalid select statement")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidStatements")
    void throws_exception_for_invalid_statements(String stmt, String description) {
        Parser parser = new Parser(stmt);

        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseStmt);
    }

    static Stream<Arguments> validSelectStatements() {
        return Stream.of(
            Arguments.of(
                "select a from t where c=1;",
                new StmtSelect(
                    "t",
                    List.of("a"),
                    List.of(new NamedCell("c", new Cell.Int(1))))),
            Arguments.of(
                "select a,b,column from t where c=-21 and dot=\"hello\";",
                new StmtSelect(
                    "t",
                    List.of("a", "b", "column"),
                    List.of(
                        new NamedCell("c", new Cell.Int(-21)),
                        new NamedCell("dot", new Cell.Str(bytes("hello")))))),
            Arguments.of(
                "select a,b,column from t;",
                new StmtSelect(
                    "t",
                    List.of("a", "b", "column"),
                    List.of()))
        );
    }

    @ParameterizedTest
    @MethodSource("validSelectStatements")
    void parses_valid_select_statements(String stmt, StmtSelect expected) {
        Parser parser = new Parser(stmt);
        assertThat(parser.parseSelect()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidSelectStatements() {
        return Stream.of(
            Arguments.of("a from t where c=1;", "Missing 'select' keyword"),
            Arguments.of("select from t where c=1;", "Missing select column"),
            Arguments.of("select a, from t where c=1;", "Trailing comma in columns"),
            Arguments.of("select a,b t where c=1;", "Missing 'from' keyword"),
            Arguments.of("select a,b from where c=1;", "Missing tableName"),
            Arguments.of("select a,b from t where c=1 d=4;", "Missing 'and' keyword in where clause"),
            Arguments.of("select a,b from t where c=1", "Missing ';'")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidSelectStatements")
    void throws_exception_for_invalid_select_statements(
        String stmt, String description)
    {
        Parser parser = new Parser(stmt);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseSelect);
    }

    @ParameterizedTest
    @MethodSource("validCreateTableStatements")
    void parses_valid_create_table_statements(String stmt, StmtCreateTable expected) {

        Parser parser = new Parser(stmt);
        assertThat(parser.parseCreateTable()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("invalidCreateTableStatements")
    void throws_exception_for_invalid_create_table_statements(
        String stmt, String description)
    {

        Parser parser = new Parser(stmt);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseCreateTable);
    }

    static Stream<Arguments> validInsertStatements() {
        return Stream.of(
            Arguments.of(
                "insert into t_asd0f values (   1, 'x', 'y');",
                new StmtInsert("t_asd0f", List.of(
                    new Cell.Int(1), new Cell.Str(bytes("x")), new Cell.Str(bytes("y"))
                ))),
            Arguments.of(
                "INSERT   into  tableXYZ vaLUes (0   ) ;",
                new StmtInsert("tableXYZ", List.of(new Cell.Int(0))))
        );
    }

    @ParameterizedTest
    @MethodSource("validInsertStatements")
    void parses_valid_insert_statements(String stmt, StmtInsert expected) {
        Parser parser = new Parser(stmt);
        assertThat(parser.parseInsert()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidInsertStatements() {
        return Stream.of(
            Arguments.of("into t values (1, 'x', 'y');", "Missing 'insert' keyword"),
            Arguments.of("insert into values (1, 'x', 'y');", "Missing table name"),
            Arguments.of("insert into t values 1, 'x', 'y');", "Missing '(' for values"),
            Arguments.of("insert into t values (1, 'x', y');", "Invalid value")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInsertStatements")
    void throws_exception_for_invalid_insert_statements(String stmt, String description) {
        Parser parser = new Parser(stmt);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseInsert);
    }

    static Stream<Arguments> validUpdateStatements() {
        return Stream.of(
            Arguments.of(
                "update t_asd0f set col1 = 1, col2 = 'x' where id = 10 AND type = 'y';",
                new StmtUpdate(
                    "t_asd0f",
                    List.of(
                        new NamedCell("id", new Cell.Int(10)),
                        new NamedCell("type", new Cell.Str(bytes("y")))
                    ),
                    List.of(
                        new NamedCell("col1", new Cell.Int(1)),
                        new NamedCell("col2", new Cell.Str(bytes("x")))
                    )
                )
            ),
            Arguments.of(
                "UPDATE   tableXYZ SET a=0 WHERE key1='abc';",
                new StmtUpdate(
                    "tableXYZ",
                    List.of(
                        new NamedCell("key1", new Cell.Str(bytes("abc")))
                    ),
                    List.of(
                        new NamedCell("a", new Cell.Int(0))
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("validUpdateStatements")
    void parses_valid_update_statements(String stmt, StmtUpdate expected) {
        Parser parser = new Parser(stmt);
        assertThat(parser.parseUpdate()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidUpdateStatements() {
        return Stream.of(
            Arguments.of(
                "t set col1 = 1 where id = 10;",
                "Missing 'update' keyword"
            ),
            Arguments.of(
                "update set col1 = 1 where id = 10;",
                "Missing table name"
            ),
            Arguments.of(
                "update t col1 = 1 where id = 10;",
                "Missing 'set' keyword"
            ),
            Arguments.of(
                "update t set where id = 10;",
                "Missing assignments in SET clause"
            ),
            Arguments.of(
                "update t set col1 = 1;",
                "Missing WHERE clause"
            ),
            Arguments.of(
                "update t set col1 = where id = 10;",
                "Invalid value in SET clause"
            ),
            Arguments.of(
                "update t set col1 = 1 where id = ;",
                "Invalid value in WHERE clause"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateStatements")
    void throws_exception_for_invalid_update_statements(String stmt, String description) {
        Parser parser = new Parser(stmt);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseUpdate);
    }

    static Stream<Arguments> validDeleteStatements() {
        return Stream.of(
            Arguments.of(
                "delete from t_asd0f where id = 1 and type = 'x';",
                new StmtDelete(
                    "t_asd0f",
                    List.of(
                        new NamedCell("id", new Cell.Int(1)),
                        new NamedCell("type", new Cell.Str(bytes("x")))
                    )
                )
            ),
            Arguments.of(
                "DELETE   FROM   tableXYZ WHERE key1='abc';",
                new StmtDelete(
                    "tableXYZ",
                    List.of(
                        new NamedCell("key1", new Cell.Str(bytes("abc")))
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("validDeleteStatements")
    void parses_valid_delete_statements(String stmt, StmtDelete expected) {
        Parser parser = new Parser(stmt);
        assertThat(parser.parseDelete()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidDeleteStatements() {
        return Stream.of(
            Arguments.of(
                "from t where id = 1;",
                "Missing 'delete' keyword"
            ),
            Arguments.of(
                "delete t where id = 1;",
                "Missing 'from' keyword"
            ),
            Arguments.of(
                "delete from where id = 1;",
                "Missing table name"
            ),
            Arguments.of(
                "delete from t;",
                "Missing WHERE clause"
            ),
            Arguments.of(
                "delete from t where;",
                "Missing conditions"
            ),
            Arguments.of(
                "delete from t where id = ;",
                "Invalid value"
            ),
            Arguments.of(
                "delete from t where = 1;",
                "Missing column name"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDeleteStatements")
    void throws_exception_for_invalid_delete_statements(String stmt, String description) {
        Parser parser = new Parser(stmt);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(parser::parseDelete);
    }

    static Stream<Arguments> validCreateTableStatements() {
        return Stream.of(
            Arguments.of(
                """
                create table t (
                    a int64,
                    b string,
                    c string,
                    primary key (b, c)
                );
                """,
                new StmtCreateTable(
                    "t",
                    List.of(
                        new Column("a", CellType.INT),
                        new Column("b", CellType.STR),
                        new Column("c", CellType.STR)
                    ),
                    List.of("b", "c"))),
            Arguments.of(
                """
                create TABLE table (
                    a int64,
                    primary key (a)
                );
                """,
                new StmtCreateTable(
                    "table",
                    List.of(new Column("a", CellType.INT)),
                    List.of("a")))
        );
    }

    static Stream<Arguments> invalidCreateTableStatements() {
        return Stream.of(
            Arguments.of(
                """
                table t (
                    c string,
                    primary key (c)
                );
                """,
                "No 'create' keyword"),
            Arguments.of(
                """
                create table (
                    c string,
                    primary key (c)
                );
                """,
                "Missing table name"),
            Arguments.of(
                """
                create table t (
                    primary key (c)
                );
                """,
                "Missing column name"),
            Arguments.of(
                """
                create table t (
                    c asdf,
                    primary key (c)
                );
                """,
                "Unknown column type"),
            Arguments.of(
                """
                create table t (
                    c int64,
                    primary key
                );
                """,
                "Missing primary key"),
            Arguments.of(
                """
                create table t (
                    c int64,
                    primary key (b)
                );
                """,
                "Provided primary key column does not exist"),
            Arguments.of(
                """
                create table t (
                    c int64,
                    c string,
                    primary key (c)
                );
                """,
                "Duplicated column name"),
            Arguments.of(
                """
                create table t (
                    c int64,
                    d string,
                    primary key (c, d, c)
                );
                """,
                "Duplicated primary key")
        );
    }
}
