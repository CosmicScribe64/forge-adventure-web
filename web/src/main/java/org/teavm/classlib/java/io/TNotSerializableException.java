package org.teavm.classlib.java.io;

import java.io.ObjectStreamException;

/** java.io.NotSerializableException, missing from TeaVM's classlib (see TObjectOutputStream). */
public class TNotSerializableException extends ObjectStreamException {
    public TNotSerializableException() {
    }

    public TNotSerializableException(String message) {
        super(message);
    }
}
