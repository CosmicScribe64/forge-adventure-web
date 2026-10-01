package forgeweb.shim.org.w3c.dom;

/** Stands in for org.w3c.dom.Node (see forgeweb.ForgeWebSubstitutionPolicy). */
public interface TNode {
    short ELEMENT_NODE = 1;
    short ATTRIBUTE_NODE = 2;
    short TEXT_NODE = 3;
    short DOCUMENT_NODE = 9;

    String getNodeName();
    String getNodeValue();
    void setNodeValue(String value);
    short getNodeType();
    String getTextContent();
    void setTextContent(String text);
    TNode getParentNode();
    TNodeList getChildNodes();
    TNode getFirstChild();
    TNode getLastChild();
    TNode getNextSibling();
    TNode getPreviousSibling();
    boolean hasChildNodes();
    TNode appendChild(TNode child);
    TNode removeChild(TNode child);
    TNamedNodeMap getAttributes();
    TDocument getOwnerDocument();
    void normalize();
}
