package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public interface TExternalizable extends java.io.Serializable {
    void writeExternal(ObjectOutput out) throws IOException;
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
}
