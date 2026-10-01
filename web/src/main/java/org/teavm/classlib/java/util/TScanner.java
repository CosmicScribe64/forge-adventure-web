package org.teavm.classlib.java.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/** Line- and whitespace-token reading only. */
public final class TScanner implements Closeable {
    private final BufferedReader reader;
    private String pendingLine;
    private String[] tokens = new String[0];
    private int tokenIndex;

    public TScanner(File file) throws FileNotFoundException {
        this(new FileInputStream(file));
    }

    public TScanner(InputStream in) {
        reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public TScanner(String source) {
        reader = new BufferedReader(new StringReader(source));
    }

    private boolean fillLine() {
        if (pendingLine != null) return true;
        try {
            pendingLine = reader.readLine();
        } catch (IOException e) {
            pendingLine = null;
        }
        return pendingLine != null;
    }

    public boolean hasNextLine() {
        return tokenIndex < tokens.length || fillLine();
    }

    public String nextLine() {
        if (tokenIndex < tokens.length) {
            StringBuilder rest = new StringBuilder();
            while (tokenIndex < tokens.length) {
                if (rest.length() > 0) rest.append(' ');
                rest.append(tokens[tokenIndex++]);
            }
            return rest.toString();
        }
        if (!fillLine()) throw new NoSuchElementException("No line found");
        String line = pendingLine;
        pendingLine = null;
        return line;
    }

    public boolean hasNext() {
        while (tokenIndex >= tokens.length) {
            if (!fillLine()) return false;
            String line = pendingLine.trim();
            pendingLine = null;
            tokens = line.isEmpty() ? new String[0] : line.split("\\s+");
            tokenIndex = 0;
        }
        return true;
    }

    public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        return tokens[tokenIndex++];
    }

    public int nextInt() {
        return Integer.parseInt(next());
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
