package forgeweb.shim.org.w3c.dom;

public interface TElement extends TNode {
    String getTagName();
    String getAttribute(String name);
    void setAttribute(String name, String value);
    boolean hasAttribute(String name);
    void removeAttribute(String name);
    TAttr getAttributeNode(String name);
    TAttr setAttributeNode(TAttr attr);
    TNodeList getElementsByTagName(String name);
}
