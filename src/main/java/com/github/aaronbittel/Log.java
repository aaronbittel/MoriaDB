package com.github.aaronbittel;

import static java.nio.file.StandardOpenOption.APPEND;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Log {
    private final String filename;
    private OutputStream out;
    private InputStream in;

    public Log(String filename) {
        this.filename = filename;
    }

    public void open() throws IOException {
        Path filepath = Path.of(filename);
        if (!Files.exists(filepath)) {
            Files.createFile(filepath);
        }
        in = Files.newInputStream(filepath);
        out = Files.newOutputStream(filepath, APPEND);
    }

    public void close() throws IOException {
        in.close();
        out.close();
    }

    public void write(Entry entry) throws IOException {
        out.write(entry.encode());
    }

    public Entry read() throws IOException {
        return Entry.decode(in);
    }
}
