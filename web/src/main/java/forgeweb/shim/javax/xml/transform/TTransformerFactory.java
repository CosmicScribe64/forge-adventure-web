package forgeweb.shim.javax.xml.transform;

public class TTransformerFactory {
    public static TTransformerFactory newInstance() { return new TTransformerFactory(); }
    public TTransformer newTransformer() throws TTransformerConfigurationException { return new TTransformer(); }
    public void setAttribute(String name, Object value) { }
}
