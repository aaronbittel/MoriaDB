package com.github.aaronbittel;

import static com.github.aaronbittel.BytesUtility.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.aaronbittel.parser.Parser;
import com.github.aaronbittel.parser.StmtCreateTable;
import com.github.aaronbittel.table.CellType;
import com.github.aaronbittel.table.Column;
import com.github.aaronbittel.table.Row;
import com.github.aaronbittel.table.Schema;

class DBTest {

    static String testDB = ".test.db";

    KVStore kv;
    DB db;

    Schema schema = new Schema(
        "link",
        List.of(
            new Column("time", CellType.INT),
            new Column("src", CellType.STR),
            new Column("dst", CellType.STR)
        ),
        List.of(1, 2)
    );

    Row row = new Row(
        List.of(
            new Cell.Int(123),
            new Cell.Str(bytes("a")),
            new Cell.Str(bytes("b"))
        )
    );

    Row out;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(Path.of(testDB));
        kv = new KVStore(new Log(testDB));
        db = new DB(kv);
        db.open();

        out = new Row(
            Arrays.asList(
                new Cell.Null(),
                new Cell.Str(bytes("a")),
                new Cell.Str(bytes("b"))
            )
        );
    }

    @AfterEach
    void teardown() throws IOException {
        Files.deleteIfExists(Path.of(testDB));
        db.close();
    }

    @Test
    void select_nonexistent_row_returns_false() {
        assertThat(db.select(schema, row)).isFalse();
    }

    @Test
    void select_existent_row_returns_true() throws IOException {
        assertThat(db.insert(schema, row)).isTrue();

        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(row);
    }

    @Test
    void upsert_inserts_if_not_exists_and_returns_true() throws IOException {
        assertThat(db.upsert(schema, row)).isTrue();

        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(row);
    }

    @Test
    void upsert_updates_value_if_exists_and_returns_true() throws IOException {
        assertThat(db.upsert(schema, row)).isTrue();

        Row updatedRow = new Row(
            List.of(
                new Cell.Int(456),
                new Cell.Str(bytes("a")),
                new Cell.Str(bytes("b"))
            )
        );
        assertThat(db.upsert(schema, updatedRow)).isTrue();

        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(updatedRow);
    }

    @Test
    void upsert_returns_false_if_value_is_the_same() throws IOException {
        assertThat(db.upsert(schema, row)).isTrue();
        assertThat(db.upsert(schema, row)).isFalse();

        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(row);
    }

    @Test
    void update_returns_false_if_nonexistent() throws IOException {
        assertThat(db.update(schema, row)).isFalse();
    }

    @Test
    void update_updates_existing_value_and_returns_true() throws IOException {
        assertThat(db.insert(schema, row)).isTrue();

        Row updatedRow = new Row(
            List.of(
                new Cell.Int(456),
                new Cell.Str(bytes("a")),
                new Cell.Str(bytes("b"))
            )
        );

        assertThat(db.update(schema, updatedRow)).isTrue();
        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(updatedRow);
    }

    @Test
    void delete_returns_false_if_key_does_not_exist() throws IOException {
        assertThat(db.delete(schema, out)).isFalse();
    }

    @Test
    void delete_deletes_value_that_exists_and_returns_true() throws IOException {
        assertThat(db.insert(schema, row)).isTrue();
        assertThat(db.select(schema, out)).isTrue();
        assertThat(out).isEqualTo(row);

        Row key = new Row(
            Arrays.asList(
                new Cell.Null(),
                new Cell.Str(bytes("a")),
                new Cell.Str(bytes("b"))
            )
        );

        assertThat(db.delete(schema, key)).isTrue();
        assertThat(db.select(schema, out)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidPKRows")
    void select_throws_illegal_argument_exception_when_primary_key_is_missing(
        Row row,
        String expectedMessage)
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.select(schema, row))
            .withMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("invalidPKRows")
    void delete_throws_illegal_argument_exception_when_primary_key_is_missing(
        Row row,
        String expectedMessage)
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.delete(schema, row))
            .withMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("invalidInputRows")
    void insert_throws_illegal_argument_exception_when_row_is_not_complete(
        Row row,
        String expectedMessage)
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.insert(schema, row))
            .withMessage(expectedMessage);
    }

    private static Stream<Arguments> invalidPKRows() {
        Row emptyRow = new Row(Arrays.asList(
            new Cell.Null(),
            new Cell.Null(),
            new Cell.Null()));
        Row missingPrimaryKey = new Row(Arrays.asList(
                new Cell.Null(),
                new Cell.Str(bytes("a")),
                new Cell.Null()));
        Row wrongPrimaryKey = new Row(Arrays.asList(
                new Cell.Null(),
                new Cell.Int(123),
                new Cell.Str(bytes("b"))));
        return Stream.of(
            Arguments.of(
                emptyRow,
                "Expected schema type 'STR' for column 'src', but got 'NULL'"
            ),
            Arguments.of(
                missingPrimaryKey,
                "Expected schema type 'STR' for column 'dst', but got 'NULL'"
            ),
            Arguments.of(
                wrongPrimaryKey,
                "Expected schema type 'STR' for column 'src', but got 'INT'"
            ));
    }

    private static Stream<Arguments> invalidInputRows() {
        Row emptyRow = new Row(Arrays.asList(
            new Cell.Null(),
            new Cell.Null(),
            new Cell.Null()));

        Row missingPrimaryKey = new Row(Arrays.asList(
            new Cell.Int(123),
            new Cell.Str(bytes("a")),
            new Cell.Null()));

        Row missingValue = new Row(Arrays.asList(
            new Cell.Null(),
            new Cell.Str(bytes("a")),
            new Cell.Str(bytes("b"))));

        Row wrongValue = new Row(Arrays.asList(
            new Cell.Str(bytes("wrong")),
            new Cell.Str(bytes("a")),
            new Cell.Str(bytes("b"))));

        Row wrongPrimaryKey = new Row(Arrays.asList(
            new Cell.Null(),
            new Cell.Int(123),
            new Cell.Str(bytes("b"))));

        return Stream.of(
            Arguments.of(
                emptyRow,
                "Expected schema type 'STR' for column 'src', but got 'NULL'"
            ),
            Arguments.of(
                missingPrimaryKey,
                "Expected schema type 'STR' for column 'dst', but got 'NULL'"
            ),
            Arguments.of(
                wrongPrimaryKey,
                "Expected schema type 'STR' for column 'src', but got 'INT'"
            ),
            Arguments.of(
                missingValue,
                "Expected schema type 'INT' for column 'time', but got 'NULL'"
            ),
            Arguments.of(
                wrongValue,
                "Expected schema type 'INT' for column 'time', but got 'STR'"
            )
        );
    }

    static Stream<Arguments> validCreateTableStatements() {
        return Stream.of(
            Arguments.of(
                new StmtCreateTable(
                    "link",
                    List.of(
                        new Column("time", CellType.INT),
                        new Column("src", CellType.STR),
                        new Column("dst", CellType.STR)),
                    List.of("src", "dst")),
                new Schema(
                    "link",
                    List.of(
                        new Column("time", CellType.INT),
                        new Column("src", CellType.STR),
                        new Column("dst", CellType.STR)),
                    List.of(1, 2))
            ),
            Arguments.of(
                new StmtCreateTable(
                    "link",
                    List.of(
                        new Column("time", CellType.INT),
                        new Column("src", CellType.STR),
                        new Column("dst", CellType.STR)),
                    List.of("dst", "src")),
                new Schema(
                    "link",
                    List.of(
                        new Column("time", CellType.INT),
                        new Column("src", CellType.STR),
                        new Column("dst", CellType.STR)),
                    List.of(1, 2))
            )
        );
    }

    @ParameterizedTest
    @MethodSource("validCreateTableStatements")
    void creates_table_and_persists_schema(StmtCreateTable stmt, Schema expected)
        throws Exception
    {
        db.execStmt(stmt);
        assertThat(db.getSchema(stmt.tableName())).hasValue(expected);
    }

    static Stream<Arguments> invalidCreateTableStatements() {
        return Stream.of(
            Arguments.of(
                new StmtCreateTable(
                    "t",
                    List.of(
                        new Column("a", CellType.INT),
                        new Column("a", CellType.STR)
                    ),
                    List.of("a")
                ), "Duplicate column"
            ),
            Arguments.of(
                new StmtCreateTable(
                    "t",
                    List.of(new Column("a", CellType.INT)),
                    List.of("a", "a")
                ), "Duplicate primary key"
            ),
            Arguments.of(
                new StmtCreateTable(
                    "t",
                    List.of(new Column("a", CellType.INT)),
                    List.of("b")
                ), "Missing primary key"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreateTableStatements")
    void invalid_create_table_stmt_causes_excetion_to_be_thrown(
        StmtCreateTable stmt, String description)
    {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .as(description)
            .isThrownBy(() -> db.execStmt(stmt));
    }

    @Test
    void create_table_with_existing_name_throws_exception()
        throws IOException
    {
        StmtCreateTable stmt = new StmtCreateTable(
            "link",
            List.of(
                new Column("time", CellType.INT),
                new Column("src", CellType.STR),
                new Column("dst", CellType.STR)),
            List.of("src", "dst")
        );

        db.execStmt(stmt);

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.execStmt(stmt));
    }

    @Test
    void create_tables_with_identical_schema_but_different_names_succeed()
        throws IOException
    {
        List<Column> columns = List.of(
            new Column("time", CellType.INT),
            new Column("src", CellType.STR),
            new Column("dst", CellType.STR));
        List<String> primaryKeys = List.of("src", "dst");

        StmtCreateTable stmt1 = new StmtCreateTable("link1", columns, primaryKeys);
        db.execStmt(stmt1);
        assertThat(db.getSchema("link1")).isPresent();

        StmtCreateTable stmt2 = new StmtCreateTable("link2", columns, primaryKeys);
        db.execStmt(stmt2);
        assertThat(db.getSchema("link2")).isPresent();
    }

    @Test
    void creates_table_and_performs_crud_operations_with_persistence()
        throws IOException
    {
        String createStmt =
            """
            create table link (
                time int64,
                src string,
                dst string,
                primary key (src, dst)
            );
            """;
        db.execStmt(new Parser(createStmt).parseStmt());

        String insertStmt = "insert into link values (123, 'bob', 'alice');";
        SQLResult insertResult = db.execStmt(new Parser(insertStmt).parseStmt());
        assertThat(insertResult.updated()).isEqualTo(1);

        String selectStmt = "select time from link where dst = 'alice' and src = 'bob';";
        SQLResult selectResult = db.execStmt(new Parser(selectStmt).parseStmt());
        List<Row> expectedSelectRows = List.of(new Row(List.of(new Cell.Int(123))));
        assertThat(selectResult.values()).isEqualTo(expectedSelectRows);

        String updateStmt =
            """
            update link set time = 456
            where dst = 'alice' and src = 'bob';
            """;
        SQLResult updateResult = db.execStmt(new Parser(updateStmt).parseStmt());
        assertThat(updateResult.updated()).isEqualTo(1);

        selectStmt = "select time from link where dst = 'alice' and src = 'bob';";
        selectResult = db.execStmt(new Parser(selectStmt).parseStmt());
        expectedSelectRows = List.of(new Row(List.of(new Cell.Int(456))));
        assertThat(selectResult.values()).isEqualTo(expectedSelectRows);

        db.close();
        db.open();

        String deleteStmt = "delete from link where src = 'bob' and dst = 'alice';";
        SQLResult deleteResult = db.execStmt(new Parser(deleteStmt).parseStmt());
        assertThat(deleteResult.updated()).isEqualTo(1);

        selectStmt = "select time from link where dst = 'alice' and src = 'bob';";
        selectResult = db.execStmt(new Parser(selectStmt).parseStmt());
        assertThat(selectResult.values()).isEmpty();
    }
}
