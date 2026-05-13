package com.github.aaronbittel;

import static com.github.aaronbittel.BytesUtility.bytes;
import static com.github.aaronbittel.BytesUtility.bytesKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class EntryTest {

    // |  crc32  | key size | val size | deleted | key data | val data |
    // | 4 bytes | 4 bytes  | 4 bytes  | 1 byte  |   ...    |   ...    |

    @Test
    void encode_returns_expected_byte_array() {
        Entry entry = new Entry(bytesKey("k1"), bytes("value1"), false);

        byte[] expected = {
            (byte) 0x55, (byte) 0x61, (byte) 0x14, (byte) 0x2D,
            2, 0, 0, 0,
            6, 0, 0, 0,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };
        assertThat(entry.encode()).isEqualTo(expected);
    }

    @Test
    void decode_returns_expected_entry() throws IOException {
        byte[] data = {
            (byte) 0x55, (byte) 0x61, (byte) 0x14, (byte) 0x2D,
            2, 0, 0, 0,
            6, 0, 0, 0,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        Entry entry = Entry.decode(in);

        Entry expected = new Entry(bytesKey("k1"), bytes("value1"), false);

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_stops_reading_at_entry_boundary() throws IOException {
        byte[] data = {
            (byte) 0x55, (byte) 0x61, (byte) 0x14, (byte) 0x2D,
            2, 0, 0, 0,
            6, 0, 0, 0,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1',
            0, 5, 6
        };
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        Entry entry = Entry.decode(in);

        Entry expected = new Entry(bytesKey("k1"), bytes("value1"), false);

        assertThat(entry).isEqualTo(expected);
    }

    @Test
    void decode_from_chunked_stream_returns_expected_entry() throws IOException {
        byte[] data = {
            (byte) 0x55, (byte) 0x61, (byte) 0x14, (byte) 0x2D,
            2, 0, 0, 0,
            6, 0, 0, 0,
            0,
            'k', '1',
            'v', 'a', 'l', 'u', 'e', '1'
        };

        try (DataInputStream in = new DataInputStream(new ChunkedInputStream(data, 3))) {
            Entry entry = Entry.decode(in);
            Entry expected = new Entry(bytesKey("k1"), bytes("value1"), false);
            assertThat(entry).isEqualTo(expected);
        }
    }
}
