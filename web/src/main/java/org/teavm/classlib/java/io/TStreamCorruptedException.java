package org.teavm.classlib.java.io;

import java.io.ObjectStreamException;

/** java.io.StreamCorruptedException, missing from TeaVM's classlib (see TObjectOutputStream). */
public class TStreamCorruptedException extends ObjectStreamException {
    public TStreamCorruptedException() {
    }

    public TStreamCorruptedException(String message) {
        super(message);
    }
}
