package com.github.aaronbittel;

import static com.github.aaronbittel.BytesUtility.bytes;
import static com.github.aaronbittel.table.CellType.INT;
import static com.github.aaronbittel.table.CellType.STR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.aaronbittel.parser.NamedCell;
import com.github.aaronbittel.parser.Parser;
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

class DBTest {

    static String testDB = ".test.db";

    KVStore kv;
    DB db;

    Schema schema = createSchema(
        "link",
        List.of(col("time", INT), col("src", STR), col("dst", STR)),
        List.of(1, 2)
    );

    Row row = createRow(intCell(123), strCell("a"), strCell("b"));

    Row out;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(Path.of(testDB));
        kv = new KVStore(new Log(testDB));
        db = new DB(kv);
        db.open();

        out = createRow(nullCell(), strCell("a"), strCell("b"));
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

        Row updatedRow = createRow(intCell(456), strCell("a"), strCell("b"));
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

        Row updatedRow = createRow(intCell(456), strCell("a"), strCell("b"));

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

        Row key = createRow(nullCell(), strCell("a"), strCell("b"));

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
        Row emptyRow = createRow(nullCell(), nullCell(), nullCell());
        Row missingPrimaryKey = createRow(nullCell(), strCell("a"), nullCell());
        Row wrongPrimaryKey = createRow(nullCell(), intCell(123), strCell("b"));
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
        Row emptyRow = createRow(nullCell(), nullCell(), nullCell());
        Row missingPrimaryKey = createRow(intCell(123), strCell("a"), nullCell());
        Row missingValue = createRow(nullCell(), strCell("a"), strCell("b"));
        Row wrongValue = createRow(strCell("wrong"), strCell("a"), strCell("b"));
        Row wrongPrimaryKey = createRow(nullCell(), intCell(123), strCell("b"));

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
                createTable(
                    "link",
                    List.of(col("time", INT), col("src", STR), col("dst", STR)),
                    List.of("src", "dst")),
                createSchema(
                    "link",
                    List.of(col("time", INT), col("src", STR), col("dst", STR)),
                    List.of(1, 2))
            ),
            Arguments.of(
                createTable(
                    "link",
                    List.of(col("time", INT), col("src", STR), col("dst", STR)),
                    List.of("dst", "src")),
                createSchema(
                    "link",
                    List.of(col("time", INT), col("src", STR), col("dst", STR)),
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
                createTable("t", List.of(col("a", INT), col("a", STR)), List.of("a")),
                "Duplicate column"
            ),
            Arguments.of(
                createTable("t", List.of(col("a", INT)), List.of("a", "a")),
                "Duplicate primary key"
            ),
            Arguments.of(
                createTable("t", List.of(col("a", INT)), List.of("b")),
                "Missing primary key"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreateTableStatements")
    void invalid_create_table_stmt_causes_exception_to_be_thrown(
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
        StmtCreateTable stmt = createTable(
            "link",
            List.of(col("time", INT), col("src", STR), col("dst", STR)),
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
            col("time", INT),
            col("src", STR),
            col("dst", STR));
        List<String> primaryKeys = List.of("src", "dst");

        StmtCreateTable stmt1 = createTable("link1", columns, primaryKeys);
        db.execStmt(stmt1);
        assertThat(db.getSchema("link1")).isPresent();

        StmtCreateTable stmt2 = createTable("link2", columns, primaryKeys);
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
        List<Row> expectedSelectRows = List.of(createRow(intCell(123)));
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
        expectedSelectRows = List.of(createRow(intCell(456)));
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

    @Nested
    class WithSingleExistingRow {

        static String dbNameNested = "test-table";

        @BeforeEach
        void setupInitialData() throws IOException {
            StmtCreateTable createStmt = createTable(
                dbNameNested,
                List.of(col("id", INT), col("first_name", STR), col("last_name", STR)),
                List.of("id"));
            db.execStmt(createStmt);

            StmtInsert insertStmt = new StmtInsert(
                dbNameNested,
                List.of(intCell(1), strCell("Bob"), strCell("Smith")));
            db.execStmt(insertStmt);
        }

        static Stream<Arguments> validSelectStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell invalidId = namedCell("id", intCell(999));
            return Stream.of(
                Arguments.of(
                    createSelect(dbNameNested, List.of("id", "first_name"), List.of(validId)),
                    List.of(createRow(intCell(1), strCell("Bob")))),
                Arguments.of(
                    createSelect(dbNameNested, List.of("first_name"), List.of(validId)),
                    List.of(createRow(strCell("Bob")))),
                Arguments.of(
                    createSelect(dbNameNested, List.of("first_name", "id"), List.of(validId)),
                    List.of(createRow(strCell("Bob"), intCell(1)))),
                Arguments.of(
                    createSelect(
                        dbNameNested,
                        List.of("id", "first_name", "first_name", "id"),
                        List.of(validId)
                    ),
                    List.of(createRow(
                        intCell(1), strCell("Bob"), strCell("Bob"), intCell(1)))),
                Arguments.of(
                    createSelect(dbNameNested, List.of("id"), List.of(invalidId)),
                    List.of())
            );
        }

        @ParameterizedTest
        @MethodSource("validSelectStatements")
        void select_returns_expected_rows(
            StmtSelect select, List<Row> expectedRows) throws IOException
        {
            SQLResult result = db.execStmt(select);
            assertThat(result.updated()).isEqualTo(0);
            assertThat(result.headers()).isEqualTo(select.columns());

            assertThat(result.values()).isEqualTo(expectedRows);
        }

        static Stream<Arguments> invalidSelectStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell invalidIdType = namedCell("id", strCell("1"));
            NamedCell firstNameBob = namedCell("first_name", strCell("Bob"));
            return Stream.of(
                Arguments.of(
                    createSelect("unknownTable", List.of("id"), List.of(validId)),
                    "Unknown table"
                ),
                Arguments.of(
                    createSelect(dbNameNested, List.of("id", "unknown"), List.of(validId)),
                    "unknown column"
                ),
                Arguments.of(
                    createSelect(dbNameNested, List.of("id"), List.of(firstNameBob)),
                    "Missing primary key in where clause"
                ),
                Arguments.of(
                    createSelect(dbNameNested, List.of("id"), List.of(invalidIdType)),
                    "Wrong type for primary key"
                ),
                Arguments.of(
                    createSelect(dbNameNested, List.of("id"), List.of()),
                    "Missing primary key"
                )
            );
        }

        @ParameterizedTest
        @MethodSource("invalidSelectStatements")
        void invalid_select_throws_illegal_argument_exception(
            StmtSelect stmt, String description)
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                .as(description)
                .isThrownBy(() -> db.execStmt(stmt));
        }

        static Stream<Arguments> validUpdateStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell invalidId = namedCell("id", intCell(999));
            NamedCell newFirstNameAlice = namedCell("first_name", strCell("Alice"));
            NamedCell newLastNameAndor = namedCell("last_name", strCell("Andor"));
            return Stream.of(
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    1),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newLastNameAndor, newFirstNameAlice)),
                    1),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(invalidId),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    0)
            );
        }

        @ParameterizedTest
        @MethodSource("validUpdateStatements")
        void update_applies_changes_and_returns_affected_rows(
            StmtUpdate update, int expectedUpdated) throws IOException
        {
            SQLResult result = db.execStmt(update);
            assertThat(result.updated()).isEqualTo(expectedUpdated);
            assertThat(result.headers()).isEmpty();
            assertThat(result.values()).isEmpty();
        }

        static Stream<Arguments> invalidUpdateStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell newFirstNameAlice = namedCell("first_name", strCell("Alice"));
            NamedCell newLastNameAndor = namedCell("last_name", strCell("Andor"));
            NamedCell wrongLastNameType = namedCell("last_name", intCell(4));
            NamedCell wrongPkType = namedCell("id", strCell("one"));
            return Stream.of(
                Arguments.of(
                    createUpdate(
                        "unknown-table",
                        List.of(validId),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    "Unknown table"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    "Missing primary key"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newFirstNameAlice)),
                    "Missing column"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newFirstNameAlice, wrongLastNameType)),
                    "Wrong column type"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newFirstNameAlice, newFirstNameAlice)),
                    "Duplicate set column + missing column"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId),
                        List.of(newFirstNameAlice, newFirstNameAlice, newLastNameAndor)),
                    "Duplicate set column"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(validId, validId),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    "Duplicate key in where clause"),
                Arguments.of(
                    createUpdate(
                        dbNameNested,
                        List.of(wrongPkType),
                        List.of(newFirstNameAlice, newLastNameAndor)),
                    "Wrong primary key type")
            );
        }

        @ParameterizedTest
        @MethodSource("invalidUpdateStatements")
        void invalid_update_throws_illegal_argument_exception(
            StmtUpdate update, String description)
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                .as(description)
                .isThrownBy(() -> db.execStmt(update));
        }

        static Stream<Arguments> validInsertStatements() {
            return Stream.of(
                Arguments.of(
                    createInsert(
                        dbNameNested,
                        List.of(intCell(2), strCell("Alice"), strCell("Andor")))),
                Arguments.of(
                    createInsert(
                        dbNameNested,
                        List.of(intCell(2), strCell("Alice"), strCell(""))))
            );
        }

        @ParameterizedTest
        @MethodSource("validInsertStatements")
        void insert_adds_row_and_returns_affected_rows(
            StmtInsert insert) throws IOException
        {
            SQLResult result = db.execStmt(insert);
            assertThat(result.updated()).isEqualTo(1);
            assertThat(result.headers()).isEmpty();
            assertThat(result.values()).isEmpty();
        }

        static Stream<Arguments> invalidInsertStatements() {
            return Stream.of(
                Arguments.of(
                    createInsert(
                        "Unknown table",
                        List.of(intCell(2), strCell("Alice"), strCell("Andor"))),
                    "Unknown table"),
                Arguments.of(
                    createInsert(dbNameNested, List.of(strCell("Alice"), strCell("Andor"))),
                    "Missing column (pk)"),
                Arguments.of(
                    createInsert(dbNameNested, List.of(intCell(2), strCell("Andor"))),
                    "Missing column"),
                Arguments.of(
                    createInsert(
                        dbNameNested,
                        List.of(strCell("id"), strCell("Alice"), strCell("Andor"))),
                    "Wrong column type (pk)"),
                Arguments.of(
                    createInsert(
                        dbNameNested,
                        List.of(intCell(2), strCell("Alice"), intCell(-1))),
                    "Wrong column type")
            );
        }

        @ParameterizedTest
        @MethodSource("invalidInsertStatements")
        void invalid_insert_throws_illegal_argument_exception(
            StmtInsert insert, String description)
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                .as(description)
                .isThrownBy(() -> db.execStmt(insert));
        }

        @Test
        void insert_existing_primary_key_throws_exception() throws IOException {
            StmtInsert insert = createInsert(
                dbNameNested,
                List.of(intCell(999), strCell("Alice"), strCell("Andor")));

            db.execStmt(insert);
            assertThatExceptionOfType(IllegalArgumentException.class)
                .as("Key already exists")
                .isThrownBy(() -> db.execStmt(insert));
        }

        static Stream<Arguments> validDeleteStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell invalidId = namedCell("id", intCell(999));
            return Stream.of(
                Arguments.of(createDelete(dbNameNested, List.of(validId)), 1),
                Arguments.of(createDelete(dbNameNested, List.of(invalidId)), 0)
            );
        }

        @ParameterizedTest
        @MethodSource("validDeleteStatements")
        void delete_removes_row_and_returns_affected_rows(
            StmtDelete delete, int expectedUpdated) throws IOException
        {
            SQLResult result = db.execStmt(delete);
            assertThat(result.updated()).isEqualTo(expectedUpdated);
            assertThat(result.headers()).isEmpty();
            assertThat(result.values()).isEmpty();
        }

        static Stream<Arguments> invalidDeleteStatements() {
            NamedCell validId = namedCell("id", intCell(1));
            NamedCell wrongName = namedCell("wrong name", intCell(1));
            NamedCell wrongType = namedCell("id", strCell("wrong type"));
            return Stream.of(
                Arguments.of(
                    createDelete("Unknown table", List.of(validId)), "Unknown Table"),
                Arguments.of(
                    createDelete(dbNameNested, List.of(wrongName)), "Wrong key name"),
                Arguments.of(
                    createDelete(dbNameNested, List.of(wrongType)), "Wrong key type"),
                Arguments.of(
                    createDelete(dbNameNested, List.of()), "Missing key"),
                Arguments.of(
                    createDelete(dbNameNested, List.of(validId, validId)), "Too many keys")
            );
        }

        @ParameterizedTest
        @MethodSource("invalidDeleteStatements")
        void invalid_delete_throws_illegal_argument_exception(
            StmtDelete delete, String description)
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                .as(description)
                .isThrownBy(() -> db.execStmt(delete));
        }

        static Stream<Arguments> missingPrimaryKeyStatements() {
            String dbName = "ALL KEYS";
            NamedCell num1 = namedCell("num1", intCell(1));
            return Stream.of(
                Arguments.of(
                    dbName,
                    createSelect(dbName, List.of("num1", "num2"), List.of(num1))),
                Arguments.of(
                    dbName,
                    createUpdate(
                        dbName,
                        List.of(num1),
                        List.of(
                            namedCell("num1", intCell(10)),
                            namedCell("num2", intCell(10)),
                            namedCell("str1", strCell("a")),
                            namedCell("str2", strCell("b"))))),
                Arguments.of(
                    dbName,
                    createInsert(
                        dbName,
                        List.of(intCell(2), strCell("a"), strCell("b"))))
            );
        }

        @ParameterizedTest
        @MethodSource("missingPrimaryKeyStatements")
        void primary_key_missing_throws_exception(
            String dbName, Stmt stmt) throws IOException
        {
            StmtCreateTable create = createTable(
                dbName,
                List.of(
                    col("num1", INT),
                    col("num2", INT),
                    col("str1", STR),
                    col("str2", STR)),
                List.of("num1", "num2"));

            db.execStmt(create);

            assertThatExceptionOfType(IllegalArgumentException.class)
                .as("all primary keys must be provided")
                .isThrownBy(() -> db.execStmt(stmt));
        }
    }

    private static Cell.Int intCell(long value) {
        return new Cell.Int(value);
    }

    private static Cell.Str strCell(String data) {
        return new Cell.Str(bytes(data));
    }

    private static Cell.Null nullCell() {
        return new Cell.Null();
    }

    private static Row createRow(Cell... cells) {
        return new Row(cells);
    }

    private static Schema createSchema(
        String tableName,
        List<Column> columns,
        List<Integer> primaryKeys)
    {
        return new Schema(tableName, columns, primaryKeys);
    }

    private static Column col(String name, CellType type) {
        return new Column(name, type);
    }

    private static NamedCell namedCell(String column, Cell value) {
        return new NamedCell(column, value);
    }

    private static StmtCreateTable createTable(
        String name,
        List<Column> columns,
        List<String> pk)
    {
        return new StmtCreateTable(name, columns, pk);
    }

    private static StmtSelect createSelect(
        String name,
        List<String> columns,
        List<NamedCell> keys)
    {
        return new StmtSelect(name, columns, keys);
    }

    private static StmtUpdate createUpdate(
        String name,
        List<NamedCell> keys,
        List<NamedCell> values)
    {
        return new StmtUpdate(name, keys, values);
    }

    private static StmtInsert createInsert(String name, List<Cell> cells) {
        return new StmtInsert(name, cells);
    }

    private static StmtDelete createDelete(String name, List<NamedCell> keys) {
        return new StmtDelete(name, keys);
    }
}
