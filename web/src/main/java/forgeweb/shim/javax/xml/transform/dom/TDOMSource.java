package forgeweb.shim.javax.xml.transform.dom;

import forgeweb.shim.javax.xml.transform.TSource;
import forgeweb.shim.org.w3c.dom.TNode;

public class TDOMSource implements TSource {
    private final TNode node;

    public TDOMSource(TNode node) { this.node = node; }
    public TNode getNode() { return node; }
}
