package forgeweb.shim.javax.xml.transform.stream;

import forgeweb.shim.javax.xml.transform.TResult;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;

public class TStreamResult implements TResult {
    private File file;
    private Writer writer;
    private OutputStream stream;

    public TStreamResult(File file) { this.file = file; }
    public TStreamResult(Writer writer) { this.writer = writer; }
    public TStreamResult(OutputStream stream) { this.stream = stream; }
    public TStreamResult(String systemId) { this.file = new File(systemId); }

    public File getFile() { return file; }
    public Writer getWriter() { return writer; }
    public OutputStream getOutputStream() { return stream; }
}
