package forgeweb.shim.org.w3c.dom;

public interface TDocument extends TNode {
    TElement getDocumentElement();
    TElement createElement(String tagName);
    TText createTextNode(String data);
    TAttr createAttribute(String name);
    TNodeList getElementsByTagName(String name);
}
