package forgeweb.shim.javax.xml.parsers;

/** Stands in for javax.xml.parsers.DocumentBuilderFactory; options are accepted and ignored. */
public class TDocumentBuilderFactory {
    public static TDocumentBuilderFactory newInstance() { return new TDocumentBuilderFactory(); }
    public TDocumentBuilder newDocumentBuilder() throws TParserConfigurationException { return new TDocumentBuilder(); }
    public void setNamespaceAware(boolean v) { }
    public void setValidating(boolean v) { }
    public void setIgnoringComments(boolean v) { }
    public void setIgnoringElementContentWhitespace(boolean v) { }
    public void setExpandEntityReferences(boolean v) { }
    public void setXIncludeAware(boolean v) { }
    public void setFeature(String name, boolean v) throws TParserConfigurationException { }
}
