package forgeweb.shim.javax.xml.parsers;

import forgeweb.shim.org.w3c.dom.TDocument;
import forgeweb.shim.org.xml.sax.TInputSource;
import forgeweb.shim.org.xml.sax.TSAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class TDocumentBuilder {
    public TDocument newDocument() {
        return TMiniDom.newDocument();
    }

    public TDocument parse(File f) throws TSAXException, IOException {
        try (InputStream in = new FileInputStream(f)) {
            return parse(in);
        }
    }

    public TDocument parse(String uri) throws TSAXException, IOException {
        return parse(new File(uri));
    }

    public TDocument parse(InputStream in) throws TSAXException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return parseText(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    public TDocument parse(TInputSource source) throws TSAXException, IOException {
        if (source.getCharacterStream() != null) {
            Reader r = source.getCharacterStream();
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return parseText(sb.toString());
        }
        if (source.getByteStream() != null) {
            return parse(source.getByteStream());
        }
        return parse(source.getSystemId());
    }

    private static TDocument parseText(String text) throws TSAXException {
        // Strip a UTF-8 byte-order mark if present.
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        try {
            return TMiniXml.parse(text);
        } catch (IOException e) {
            throw new TSAXException(e.getMessage());
        }
    }
}
