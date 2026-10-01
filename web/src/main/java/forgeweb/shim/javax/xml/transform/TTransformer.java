package forgeweb.shim.javax.xml.transform;

import forgeweb.shim.javax.xml.transform.dom.TDOMSource;
import forgeweb.shim.javax.xml.transform.stream.TStreamResult;
import forgeweb.shim.javax.xml.parsers.TMiniXml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Serialises a TMiniDom tree; only DOMSource to StreamResult is supported. */
public class TTransformer {
    private final Map<String, String> props = new HashMap<>();

    public void setOutputProperty(String name, String value) {
        props.put(name, value);
    }

    public String getOutputProperty(String name) {
        return props.get(name);
    }

    public void transform(TSource source, TResult result) throws TTransformerException {
        if (!(source instanceof TDOMSource) || !(result instanceof TStreamResult)) {
            throw new TTransformerException("Only DOMSource -> StreamResult is supported on web");
        }
        boolean indent = "yes".equals(props.get(TOutputKeys.INDENT));
        boolean omitDecl = "yes".equals(props.get(TOutputKeys.OMIT_XML_DECLARATION));
        int amount = 0;
        String amt = props.get("{http://xml.apache.org/xslt}indent-amount");
        if (amt != null) {
            try {
                amount = Integer.parseInt(amt);
            } catch (NumberFormatException ignored) {
                // keep 0
            }
        }
        String xml = TMiniXml.write(((TDOMSource) source).getNode(), indent, amount, omitDecl);
        TStreamResult r = (TStreamResult) result;
        try {
            if (r.getWriter() != null) {
                r.getWriter().write(xml);
                r.getWriter().flush();
            } else if (r.getOutputStream() != null) {
                r.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
                r.getOutputStream().flush();
            } else {
                try (OutputStream out = new FileOutputStream(r.getFile())) {
                    out.write(xml.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new TTransformerException(e);
        }
    }
}
