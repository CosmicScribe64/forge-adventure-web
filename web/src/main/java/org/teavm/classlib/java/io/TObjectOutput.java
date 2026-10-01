package org.teavm.classlib.java.io;

import java.io.DataOutput;
import java.io.IOException;

public interface TObjectOutput extends DataOutput, AutoCloseable {
    void writeObject(Object obj) throws IOException;
    void write(int b) throws IOException;
    void write(byte[] b) throws IOException;
    void write(byte[] b, int off, int len) throws IOException;
    void flush() throws IOException;
    @Override void close() throws IOException;
}
