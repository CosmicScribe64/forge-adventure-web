"""Static server for the web build (scripts/serve-web). Its caching headers match how the page
loads. Files requested with ?v=<content hash> (app.js, wfc-worker.js) never change, so they may be
cached for good. Everything else is revalidated on every load, so a rebuild is always seen."""
import http.server
import sys


class Handler(http.server.SimpleHTTPRequestHandler):
    # Keep-alive: the page makes hundreds of requests, and a new connection for each through
    # Docker's port forwarding sometimes stalled one for a minute (headless test runs).
    protocol_version = "HTTP/1.1"

    def end_headers(self):
        if "?v=" in self.path:
            self.send_header("Cache-Control", "public, max-age=31536000, immutable")
        else:
            self.send_header("Cache-Control", "no-cache")
        super().end_headers()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    http.server.ThreadingHTTPServer(("", port), Handler).serve_forever()
