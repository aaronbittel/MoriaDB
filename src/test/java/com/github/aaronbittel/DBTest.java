package com.github.aaronbittel;

import static com.github.aaronbittel.TestBytes.b;
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

import com.github.aaronbittel.table.CellType;
import com.github.aaronbittel.table.Column;
import com.github.aaronbittel.table.Row;
import com.github.aaronbittel.table.Schema;

class DBTest {

    static String TEST_DB = ".test.db";

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
            new Cell.Str(b("a")),
            new Cell.Str(b("b"))
        )
    );

    Row emptyRow = new Row(Arrays.asList(null, null, null));

    Row out;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(Path.of(TEST_DB));
        db = new DB(new KVStore(new Log(TEST_DB)));
        db.open();

        out = new Row(
            Arrays.asList(
                null,
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
            )
        );
    }

    @AfterEach
    void teardown() throws IOException {
        Files.deleteIfExists(Path.of(TEST_DB));
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
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
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
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
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
                null,
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
            )
        );

        assertThat(db.delete(schema, key)).isTrue();
        assertThat(db.select(schema, out)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidPKRows")
    void select_throws_illegal_argument_exception_when_primary_key_is_missing(
        Row row,
        String expectedMessage
    ) {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.select(schema, row))
            .withMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("invalidPKRows")
    void delete_throws_illegal_argument_exception_when_primary_key_is_missing(
        Row row,
        String expectedMessage
    ) {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.delete(schema, row))
            .withMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("invalidInputRows")
    void insert_throws_illegal_argument_exception_when_row_is_not_complete(
        Row row,
        String expectedMessage
    ) {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> db.insert(schema, row))
            .withMessage(expectedMessage);
    }

    private static Stream<Arguments> invalidPKRows() {
        Row emptyRow = new Row(Arrays.asList(null, null, null));
        Row missingPrimaryKey = new Row(Arrays.asList(
                null,
                new Cell.Str(b("a")),
                null
        ));
        Row wrongPrimaryKey = new Row(Arrays.asList(
                null,
                new Cell.Int(123),
                new Cell.Str(b("b"))
        ));
        return Stream.of(
            Arguments.of(
                emptyRow,
                "Expected schema type 'STR' for column 'src', but got 'null'"
            ),
            Arguments.of(
                missingPrimaryKey,
                "Expected schema type 'STR' for column 'dst', but got 'null'"
            ),
            Arguments.of(
                wrongPrimaryKey,
                "Expected schema type 'STR' for column 'src', but got 'INT'"
            )
        );
    }

    private static Stream<Arguments> invalidInputRows() {
        Row emptyRow = new Row(Arrays.asList(null, null, null));
        Row missingPrimaryKey = new Row(Arrays.asList(
                new Cell.Int(123),
                new Cell.Str(b("a")),
                null
        ));
        Row missingValue = new Row(Arrays.asList(
                null,
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
        ));
        Row wrongValue = new Row(Arrays.asList(
                new Cell.Str(b("wrong")),
                new Cell.Str(b("a")),
                new Cell.Str(b("b"))
        ));
        Row wrongPrimaryKey = new Row(Arrays.asList(
                null,
                new Cell.Int(123),
                new Cell.Str(b("b"))
        ));
        return Stream.of(
                Arguments.of(
                        emptyRow,
                        "Expected schema type 'STR' for column 'src', but got 'null'"
                ),
                Arguments.of(
                        missingPrimaryKey,
                        "Expected schema type 'STR' for column 'dst', but got 'null'"
                ),
                Arguments.of(
                        wrongPrimaryKey,
                        "Expected schema type 'STR' for column 'src', but got 'INT'"
                ),
                Arguments.of(
                        missingValue,
                        "Expected schema type 'INT' for column 'time', but got 'null'"
                ),
                Arguments.of(
                        wrongValue,
                        "Expected schema type 'INT' for column 'time', but got 'STR'"
                )
        );
    }
}
