package com.github.aaronbittel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Log implements AutoCloseable {
    private final String filename;
    private RandomAccessFile file = null;

    public Log(String filename) {
        this.filename = filename;
    }

    public void open() throws FileNotFoundException {
        if (file != null) return;
        file = new RandomAccessFile(filename, "rwd");
    }

    @Override
    public void close() throws IOException {
        if (file == null) return;
        file.close();
        file = null;
    }

    public void write(Entry entry) throws IOException {
        file.write(entry.encode());
    }

    public Entry read() throws IOException {
        return Entry.decode(file);
    }
}
