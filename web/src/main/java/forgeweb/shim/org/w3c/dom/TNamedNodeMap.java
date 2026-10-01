package forgeweb.shim.org.w3c.dom;

public interface TNamedNodeMap {
    TNode getNamedItem(String name);
    TNode setNamedItem(TNode node);
    TNode removeNamedItem(String name);
    TNode item(int index);
    int getLength();
}
