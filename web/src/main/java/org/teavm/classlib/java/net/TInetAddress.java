package org.teavm.classlib.java.net;

import java.net.UnknownHostException;

/** No DNS in the browser. Exists so Gson's InetAddress type adapter links. */
public class TInetAddress implements java.io.Serializable {
    private final String host;

    TInetAddress(String host) {
        this.host = host;
    }

    public static TInetAddress getByName(String host) throws UnknownHostException {
        throw new UnknownHostException("Name lookup is not available on web: " + host);
    }

    public static TInetAddress[] getAllByName(String host) throws UnknownHostException {
        throw new UnknownHostException("Name lookup is not available on web: " + host);
    }

    public static TInetAddress getLocalHost() throws UnknownHostException {
        return new TInetAddress("localhost");
    }

    public String getHostAddress() { return host; }
    public String getHostName() { return host; }
    public boolean isLoopbackAddress() { return "localhost".equals(host); }

    @Override
    public String toString() {
        return host;
    }
}
