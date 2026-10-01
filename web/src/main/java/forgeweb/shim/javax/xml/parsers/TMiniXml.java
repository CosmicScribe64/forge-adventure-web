package forgeweb.shim.javax.xml.parsers;

import forgeweb.shim.org.w3c.dom.TDocument;
import forgeweb.shim.org.w3c.dom.TElement;
import forgeweb.shim.org.w3c.dom.TNamedNodeMap;
import forgeweb.shim.org.w3c.dom.TNode;
import forgeweb.shim.org.w3c.dom.TNodeList;

import java.io.IOException;

/** Parses XML text into a {@link TMiniDom} document and serialises it back. */
public final class TMiniXml {
    private final String s;
    private int pos;

    private TMiniXml(String s) {
        this.s = s;
    }

    public static TDocument parse(String xml) throws IOException {
        TDocument doc = TMiniDom.newDocument();
        TMiniXml p = new TMiniXml(xml);
        p.parseContent(doc, doc);
        if (doc.getDocumentElement() == null) {
            throw new IOException("XML has no root element");
        }
        return doc;
    }

    private IOException error(String msg) {
        return new IOException(msg + " at offset " + pos);
    }

    private boolean startsWith(String prefix) {
        return s.startsWith(prefix, pos);
    }

    private void skipPast(String end) throws IOException {
        int i = s.indexOf(end, pos);
        if (i < 0) throw error("Unterminated construct, expected " + end);
        pos = i + end.length();
    }

    /** Parses children until the end of input or a closing tag (left unconsumed). */
    private void parseContent(TDocument doc, TNode parent) throws IOException {
        StringBuilder text = new StringBuilder();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c != '<') {
                int next = s.indexOf('<', pos);
                if (next < 0) next = s.length();
                text.append(decode(s.substring(pos, next)));
                pos = next;
                continue;
            }
            if (startsWith("<!--")) {
                skipPast("-->");
            } else if (startsWith("<![CDATA[")) {
                int end = s.indexOf("]]>", pos);
                if (end < 0) throw error("Unterminated CDATA");
                text.append(s, pos + 9, end);
                pos = end + 3;
            } else if (startsWith("<?")) {
                skipPast("?>");
            } else if (startsWith("<!")) {
                skipPast(">");
            } else if (startsWith("</")) {
                break;
            } else {
                flushText(doc, parent, text);
                parent.appendChild(parseElement(doc));
            }
        }
        flushText(doc, parent, text);
    }

    private static void flushText(TDocument doc, TNode parent, StringBuilder text) {
        if (text.length() == 0) return;
        // Whitespace between elements is dropped, as Forge never reads it.
        if (parent != doc && text.toString().trim().length() > 0) {
            parent.appendChild(doc.createTextNode(text.toString()));
        }
        text.setLength(0);
    }

    private TElement parseElement(TDocument doc) throws IOException {
        pos++; // '<'
        String name = readName();
        TElement el = doc.createElement(name);
        while (true) {
            skipWs();
            if (pos >= s.length()) throw error("Unterminated tag <" + name);
            char c = s.charAt(pos);
            if (c == '/') {
                if (!startsWith("/>")) throw error("Expected />");
                pos += 2;
                return el;
            }
            if (c == '>') {
                pos++;
                break;
            }
            String attr = readName();
            skipWs();
            if (pos >= s.length() || s.charAt(pos) != '=') throw error("Expected = after attribute " + attr);
            pos++;
            skipWs();
            char q = s.charAt(pos);
            if (q != '"' && q != '\'') throw error("Expected quoted attribute value");
            int end = s.indexOf(q, pos + 1);
            if (end < 0) throw error("Unterminated attribute value");
            el.setAttribute(attr, decode(s.substring(pos + 1, end)));
            pos = end + 1;
        }
        parseContent(doc, el);
        if (!startsWith("</")) throw error("Missing </" + name + ">");
        pos += 2;
        String close = readName();
        if (!close.equals(name)) throw error("Mismatched </" + close + "> for <" + name + ">");
        skipWs();
        if (pos >= s.length() || s.charAt(pos) != '>') throw error("Expected >");
        pos++;
        return el;
    }

    private String readName() throws IOException {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') break;
            pos++;
        }
        if (pos == start) throw error("Expected a name");
        return s.substring(start, pos);
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    static String decode(String raw) {
        if (raw.indexOf('&') < 0) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            int semi;
            if (c == '&' && (semi = raw.indexOf(';', i)) > i) {
                String ent = raw.substring(i + 1, semi);
                String rep = null;
                switch (ent) {
                    case "lt": rep = "<"; break;
                    case "gt": rep = ">"; break;
                    case "amp": rep = "&"; break;
                    case "quot": rep = "\""; break;
                    case "apos": rep = "'"; break;
                    default:
                        try {
                            if (ent.startsWith("#x")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
                            else if (ent.startsWith("#")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(1))));
                        } catch (NumberFormatException ignored) {
                            // leave as-is
                        }
                }
                if (rep != null) {
                    sb.append(rep);
                    i = semi;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String escape(String v, boolean attr) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append(attr ? "&quot;" : "\""); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Serialises a node (a document or an element subtree). */
    public static String write(TNode node, boolean indent, int indentAmount, boolean omitDeclaration) {
        StringBuilder sb = new StringBuilder();
        if (!omitDeclaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
            if (indent) sb.append('\n');
        }
        if (node instanceof TDocument) {
            TElement root = ((TDocument) node).getDocumentElement();
            if (root != null) writeNode(sb, root, indent, indentAmount, 0);
        } else {
            writeNode(sb, node, indent, indentAmount, 0);
        }
        return sb.toString();
    }

    private static void writeNode(StringBuilder sb, TNode node, boolean indent, int amount, int depth) {
        if (node.getNodeType() == TNode.TEXT_NODE) {
            sb.append(escape(node.getNodeValue(), false));
            return;
        }
        if (node.getNodeType() != TNode.ELEMENT_NODE) return;
        if (indent) pad(sb, amount * depth);
        TElement el = (TElement) node;
        sb.append('<').append(el.getTagName());
        TNamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            TNode a = attrs.item(i);
            sb.append(' ').append(a.getNodeName()).append("=\"").append(escape(a.getNodeValue(), true)).append('"');
        }
        TNodeList kids = el.getChildNodes();
        if (kids.getLength() == 0) {
            sb.append("/>");
            if (indent) sb.append('\n');
            return;
        }
        sb.append('>');
        boolean onlyText = true;
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i).getNodeType() != TNode.TEXT_NODE) onlyText = false;
        }
        if (onlyText) {
            for (int i = 0; i < kids.getLength(); i++) writeNode(sb, kids.item(i), false, amount, 0);
        } else {
            if (indent) sb.append('\n');
            for (int i = 0; i < kids.getLength(); i++) writeNode(sb, kids.item(i), indent, amount, depth + 1);
            if (indent) pad(sb, amount * depth);
        }
        sb.append("</").append(el.getTagName()).append('>');
        if (indent) sb.append('\n');
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append(' ');
    }
}
