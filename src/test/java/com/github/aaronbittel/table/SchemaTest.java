package com.github.aaronbittel.table;

import static com.github.aaronbittel.BytesUtility.bytes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.aaronbittel.Cell;

class SchemaTest {

    Schema schema = new Schema(
        "link",
        List.of(
            new Column("time", CellType.INT),
            new Column("src", CellType.STR),
            new Column("dst", CellType.STR)
        ),
        List.of(1, 2)
    );

    Row sourceRow = new Row(
        List.of(
            new Cell.Int(123),
            new Cell.Str(bytes("a")),
            new Cell.Str(bytes("b"))
        )
    );

    @Test
    void encode_row_key_returns_expected_byte_array() {
        byte[] expectedEncodedKey = {
            'l', 'i', 'n', 'k', 0, 1, 0, 0, 0, 'a', 1, 0, 0, 0, 'b'
        };
        assertThat(sourceRow.encodeKey(schema)).isEqualTo(expectedEncodedKey);
    }

    @Test
    void encode_row_val_returns_expected_byte_array() {
        byte[] expectedEncodedVal = { 123, 0, 0, 0, 0, 0, 0, 0 };
        assertThat(sourceRow.encodeVal(schema)).isEqualTo(expectedEncodedVal);
    }

    @Test
    void row_decode_key_and_value_produces_expected_row() {
        byte[] encodedKey = { 'l', 'i', 'n', 'k', 0, 1, 0, 0, 0, 'a', 1, 0, 0, 0, 'b' };
        byte[] encodedVal = { 123, 0, 0, 0, 0, 0, 0, 0 };

        Row row = schema.newRow();
        row.decodeKey(schema, encodedKey);
        row.decodeVal(schema, encodedVal);
        assertThat(row).isEqualTo(sourceRow);
    }
}
