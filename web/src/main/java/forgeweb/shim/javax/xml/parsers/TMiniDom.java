package forgeweb.shim.javax.xml.parsers;

import forgeweb.shim.org.w3c.dom.TAttr;
import forgeweb.shim.org.w3c.dom.TDocument;
import forgeweb.shim.org.w3c.dom.TElement;
import forgeweb.shim.org.w3c.dom.TNamedNodeMap;
import forgeweb.shim.org.w3c.dom.TNode;
import forgeweb.shim.org.w3c.dom.TNodeList;
import forgeweb.shim.org.w3c.dom.TText;

import java.util.ArrayList;
import java.util.List;

/**
 * A small DOM covering the subset of org.w3c.dom that Forge uses
 * (preferences, item-manager config, achievements, quest data).
 */
public final class TMiniDom {
    private TMiniDom() {
    }

    public static TDocument newDocument() {
        return new Doc();
    }

    static final class ListImpl implements TNodeList, TNamedNodeMap {
        final List<? extends TNode> nodes;
        final Elem owner; // set for an element's attribute map

        ListImpl(List<? extends TNode> nodes) {
            this(nodes, null);
        }

        ListImpl(List<? extends TNode> nodes, Elem owner) {
            this.nodes = nodes;
            this.owner = owner;
        }

        @Override
        public TNode setNamedItem(TNode node) {
            if (owner == null) throw new UnsupportedOperationException();
            return owner.setAttributeNode((TAttr) node);
        }

        @Override
        public TNode removeNamedItem(String name) {
            if (owner == null) throw new UnsupportedOperationException();
            TAttr a = owner.getAttributeNode(name);
            owner.removeAttribute(name);
            return a;
        }

        @Override public TNode item(int index) { return index >= 0 && index < nodes.size() ? nodes.get(index) : null; }
        @Override public int getLength() { return nodes.size(); }

        @Override
        public TNode getNamedItem(String name) {
            for (TNode n : nodes) {
                if (n.getNodeName().equals(name)) {
                    return n;
                }
            }
            return null;
        }
    }

    abstract static class NodeImpl implements TNode {
        Doc owner;
        NodeImpl parent;
        final List<NodeImpl> children = new ArrayList<>();

        @Override public String getNodeValue() { return null; }
        @Override public void setNodeValue(String value) { }
        @Override public TNode getParentNode() { return parent; }
        @Override public TNodeList getChildNodes() { return new ListImpl(new ArrayList<>(children)); }
        @Override public TNode getFirstChild() { return children.isEmpty() ? null : children.get(0); }
        @Override public TNode getLastChild() { return children.isEmpty() ? null : children.get(children.size() - 1); }
        @Override public boolean hasChildNodes() { return !children.isEmpty(); }
        @Override public TNamedNodeMap getAttributes() { return null; }
        @Override public TDocument getOwnerDocument() { return owner; }
        @Override public void normalize() { }

        @Override
        public TNode getNextSibling() {
            if (parent == null) return null;
            int i = parent.children.indexOf(this);
            return i + 1 < parent.children.size() ? parent.children.get(i + 1) : null;
        }

        @Override
        public TNode getPreviousSibling() {
            if (parent == null) return null;
            int i = parent.children.indexOf(this);
            return i > 0 ? parent.children.get(i - 1) : null;
        }

        @Override
        public TNode appendChild(TNode child) {
            NodeImpl c = (NodeImpl) child;
            if (c.parent != null) {
                c.parent.children.remove(c);
            }
            c.parent = this;
            children.add(c);
            return child;
        }

        @Override
        public TNode removeChild(TNode child) {
            NodeImpl c = (NodeImpl) child;
            if (children.remove(c)) {
                c.parent = null;
            }
            return child;
        }

        @Override
        public String getTextContent() {
            StringBuilder sb = new StringBuilder();
            for (NodeImpl c : children) {
                if (c instanceof Txt || c instanceof Elem) {
                    sb.append(c.getTextContent());
                }
            }
            return sb.toString();
        }

        @Override
        public void setTextContent(String text) {
            for (NodeImpl c : children) {
                c.parent = null;
            }
            children.clear();
            if (text != null && !text.isEmpty()) {
                appendChild(owner.createTextNode(text));
            }
        }

        void collectElements(String name, List<TNode> out) {
            for (NodeImpl c : children) {
                if (c instanceof Elem) {
                    if ("*".equals(name) || c.getNodeName().equals(name)) {
                        out.add(c);
                    }
                    c.collectElements(name, out);
                }
            }
        }
    }

    static final class Doc extends NodeImpl implements TDocument {
        Doc() {
            owner = this;
        }

        @Override public String getNodeName() { return "#document"; }
        @Override public short getNodeType() { return DOCUMENT_NODE; }

        @Override
        public TElement getDocumentElement() {
            for (NodeImpl c : children) {
                if (c instanceof Elem) return (Elem) c;
            }
            return null;
        }

        @Override
        public TElement createElement(String tagName) {
            Elem e = new Elem(tagName);
            e.owner = this;
            return e;
        }

        @Override
        public TText createTextNode(String data) {
            Txt t = new Txt(data);
            t.owner = this;
            return t;
        }

        @Override
        public TAttr createAttribute(String name) {
            AttrImpl a = new AttrImpl(name, "");
            a.owner = this;
            return a;
        }

        @Override
        public TNodeList getElementsByTagName(String name) {
            List<TNode> out = new ArrayList<>();
            collectElements(name, out);
            return new ListImpl(out);
        }
    }

    static final class Elem extends NodeImpl implements TElement {
        final String tag;
        final List<AttrImpl> attrs = new ArrayList<>();

        Elem(String tag) {
            this.tag = tag;
        }

        @Override public String getNodeName() { return tag; }
        @Override public String getTagName() { return tag; }
        @Override public short getNodeType() { return ELEMENT_NODE; }
        @Override public TNamedNodeMap getAttributes() { return new ListImpl(new ArrayList<>(attrs), this); }

        private AttrImpl find(String name) {
            for (AttrImpl a : attrs) {
                if (a.name.equals(name)) return a;
            }
            return null;
        }

        @Override
        public String getAttribute(String name) {
            AttrImpl a = find(name);
            return a == null ? "" : a.value;
        }

        @Override
        public void setAttribute(String name, String value) {
            AttrImpl a = find(name);
            if (a == null) {
                a = new AttrImpl(name, value);
                a.owner = owner;
                attrs.add(a);
            } else {
                a.value = value;
            }
        }

        @Override public boolean hasAttribute(String name) { return find(name) != null; }

        @Override
        public void removeAttribute(String name) {
            AttrImpl a = find(name);
            if (a != null) attrs.remove(a);
        }

        @Override public TAttr getAttributeNode(String name) { return find(name); }

        @Override
        public TAttr setAttributeNode(TAttr attr) {
            AttrImpl old = find(attr.getName());
            if (old != null) attrs.remove(old);
            attrs.add((AttrImpl) attr);
            return old;
        }

        @Override
        public TNodeList getElementsByTagName(String name) {
            List<TNode> out = new ArrayList<>();
            collectElements(name, out);
            return new ListImpl(out);
        }
    }

    static final class Txt extends NodeImpl implements TText {
        String data;

        Txt(String data) {
            this.data = data;
        }

        @Override public String getNodeName() { return "#text"; }
        @Override public short getNodeType() { return TEXT_NODE; }
        @Override public String getNodeValue() { return data; }
        @Override public void setNodeValue(String value) { data = value; }
        @Override public String getTextContent() { return data; }
        @Override public void setTextContent(String text) { data = text; }
        @Override public String getData() { return data; }
    }

    static final class AttrImpl extends NodeImpl implements TAttr {
        final String name;
        String value;

        AttrImpl(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override public String getNodeName() { return name; }
        @Override public short getNodeType() { return ATTRIBUTE_NODE; }
        @Override public String getNodeValue() { return value; }
        @Override public void setNodeValue(String v) { value = v; }
        @Override public String getTextContent() { return value; }
        @Override public void setTextContent(String text) { value = text; }
        @Override public String getName() { return name; }
        @Override public String getValue() { return value; }
        @Override public void setValue(String v) { value = v; }
    }
}
