package org.teavm.classlib.java.io;

import java.io.ObjectStreamException;

/** java.io.NotActiveException, missing from TeaVM's classlib (see TObjectOutputStream). */
public class TNotActiveException extends ObjectStreamException {
    public TNotActiveException() {
    }

    public TNotActiveException(String message) {
        super(message);
    }
}
