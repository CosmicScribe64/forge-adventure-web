package org.teavm.classlib.java.net;

import java.net.SocketAddress;

/** The browser chooses proxies; this only exists so download code links. */
public class TProxy {
    public enum Type { DIRECT, HTTP, SOCKS }

    public static final TProxy NO_PROXY = new TProxy();

    private final Type type;
    private final SocketAddress address;

    private TProxy() {
        type = Type.DIRECT;
        address = null;
    }

    public TProxy(Type type, SocketAddress address) {
        this.type = type;
        this.address = address;
    }

    public Type type() { return type; }
    public SocketAddress address() { return address; }

    @Override
    public String toString() {
        return type == Type.DIRECT ? "DIRECT" : type + " @ " + address;
    }
}
