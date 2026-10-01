package forgeweb.shim.org.w3c.dom;

public interface TAttr extends TNode {
    String getName();
    String getValue();
    void setValue(String value);
}
