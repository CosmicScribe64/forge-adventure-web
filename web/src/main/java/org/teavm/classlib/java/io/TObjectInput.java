package org.teavm.classlib.java.io;

import java.io.DataInput;
import java.io.IOException;

public interface TObjectInput extends DataInput, AutoCloseable {
    Object readObject() throws ClassNotFoundException, IOException;
    int read() throws IOException;
    int read(byte[] b) throws IOException;
    int read(byte[] b, int off, int len) throws IOException;
    long skip(long n) throws IOException;
    int available() throws IOException;
    @Override void close() throws IOException;
}
