package forgeweb.shim.org.xml.sax;

import java.io.InputStream;
import java.io.Reader;

public class TInputSource {
    private InputStream byteStream;
    private Reader characterStream;
    private String systemId;

    public TInputSource() { }
    public TInputSource(InputStream in) { byteStream = in; }
    public TInputSource(Reader r) { characterStream = r; }
    public TInputSource(String systemId) { this.systemId = systemId; }

    public InputStream getByteStream() { return byteStream; }
    public void setByteStream(InputStream in) { byteStream = in; }
    public Reader getCharacterStream() { return characterStream; }
    public void setCharacterStream(Reader r) { characterStream = r; }
    public String getSystemId() { return systemId; }
    public void setSystemId(String id) { systemId = id; }
    public void setEncoding(String enc) { }
}
