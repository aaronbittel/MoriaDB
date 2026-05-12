package com.github.aaronbittel;

import static com.github.aaronbittel.TestBytes.b;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class CellTest {

    @Test
    void int_cell_roundtrip_returns_the_same_object() {
        Cell.Int intCell = new Cell.Int(-2);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        intCell.encode(baos);

        byte[] expected = new byte[]{
            (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        assertThat(expected).isEqualTo(baos.toByteArray());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Cell.Int decodedInt = Cell.Int.decode(bais);
        assertThat(decodedInt).isEqualTo(intCell);
    }

    @Test
    void str_cell_roundtrip_returns_the_same_object() {
        Cell.Str strCell = new Cell.Str(b("asdf"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        strCell.encode(baos);

        byte[] expected = new byte[]{ 4, 0, 0, 0, 'a', 's', 'd', 'f' };
        assertThat(expected).isEqualTo(baos.toByteArray());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Cell.Str decodedstr = Cell.Str.decode(bais);
        assertThat(decodedstr).isEqualTo(strCell);
    }
}
