"""Static server that compresses like GitHub Pages, and worse, for testing the page's loader:
.js files are gzipped on the fly with Content-Encoding: gzip, and .gz files are sent with
Content-Encoding: gzip too, so the browser un-gzips them before the page sees them.
Content-Length is always the encoded size. Usage: see wiki/howto/pick-up-work.md."""
import gzip
import http.server
import os
import sys


class Handler(http.server.SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        path = self.translate_path(self.path)
        encode = "gzip" in self.headers.get("Accept-Encoding", "") and os.path.isfile(path) \
            and path.endswith((".js", ".gz"))
        if not encode:
            return super().do_GET()
        data = open(path, "rb").read()
        if path.endswith(".js"):
            data = gzip.compress(data, 6)
        self.send_response(200)
        self.send_header("Content-Type", "application/javascript" if path.endswith(".js") else "application/octet-stream")
        self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("", int(sys.argv[1])), Handler).serve_forever()
