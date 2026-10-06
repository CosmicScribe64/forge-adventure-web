#!/usr/bin/env python3
"""Run the page-side device self-test (web/tools/device-probe.js) on a phone that is already booted: the iOS
Simulator's Mobile Safari or the Android emulator's Chrome. Used by .github/workflows/devices.yml.

  device-run.py --platform ios|android|none --out DIR (--site DIR | --upstream URL) [--udid U] [--timeout S]

It serves the game on 127.0.0.1:PORT (--site: a built site folder, compressed like GitHub Pages; --upstream:
a proxy to a running site, such as the live one), puts the probe into index.html, opens the page on the device and
waits for the probe's verdict. The probe asks for screenshots (/__shot) and memory readings (/__mem), which this
script takes from the host: `xcrun simctl io screenshot` and the simulator's WebContent process (ps, footprint),
or `adb exec-out screencap` and `dumpsys meminfo com.android.chrome`. Writes OUT/summary.json, OUT/probe.log and
OUT/*.png; exit code 0 only if every check passed. Why not WebDriver: safaridriver drives desktop Safari and
real iPhones, not the Simulator (that takes Appium), and the page already has a test API, so the probe runs in it.
"""
import argparse, ast, http.server, json, mimetypes, os, re, subprocess, sys, threading, time, urllib.error, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PORT_DEFAULT = 8123
A = None            # parsed arguments
RESULT = {}         # filled by /__done
MEM = {}            # name -> reading
DONE = threading.Event()
LAST = [time.time()]  # when the probe last called in
SEEN = threading.Event()   # the probe has called in at least once
LOCK = threading.Lock()


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True, text=True, **kw)


def log(line):
    with LOCK, open(os.path.join(A.out, "probe.log"), "a") as f:
        f.write(line + "\n")
    print(line, flush=True)


def allowed_errors():
    """The known-harmless console errors, from web/tools/webtest.py (its module needs Playwright, so read the literal)."""
    tree = ast.parse(open(os.path.join(HERE, "webtest.py")).read())
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(getattr(t, "id", "") == "ALLOWED_ERRORS" for t in node.targets):
            return [p for p, _why in ast.literal_eval(node.value)]
    raise SystemExit("ALLOWED_ERRORS not found in webtest.py")


def injection():
    cfg = {"allowed": allowed_errors(), "cap": A.cap, "device": A.platform}
    probe = open(os.path.join(HERE, "device-probe.js")).read()
    return ("<script>window.__deviceProbe=%s;\n%s</script>" % (json.dumps(cfg), probe)).encode()


def inject(html):
    return html.replace(b"<head>", b"<head>" + injection(), 1)


# ---- host side: screenshots and memory ----
def ios_webcontent():
    """(pid, rss MB, footprint MB or None, command) for the simulator's WebContent processes, largest first."""
    out = sh("ps -axo pid=,rss=,command=").stdout
    procs = []
    for line in out.splitlines():
        if "com.apple.WebKit.WebContent" in line or "WebContent.xpc" in line or "/WebContent" in line:
            if "CoreSimulator" not in line and "Simulator" not in line:
                continue
            pid, rss, cmd = line.split(None, 2)
            fp = None
            r = sh(["footprint", "-p", pid])
            m = re.search(r"Footprint:\s*([\d.]+)\s*(KB|MB|GB)", r.stdout)
            if m:
                fp = float(m.group(1)) * {"KB": 1 / 1024, "MB": 1, "GB": 1024}[m.group(2)]
            procs.append({"pid": int(pid), "rss_mb": round(int(rss) / 1024), "footprint_mb": round(fp) if fp else None, "cmd": cmd[-90:]})
    return sorted(procs, key=lambda p: -(p["footprint_mb"] or p["rss_mb"]))


def android_chrome():
    """Chrome's processes with their PSS in MB from dumpsys meminfo, the renderer (sandboxed process) first."""
    r = sh(["adb", "shell", "dumpsys", "meminfo"])
    out = r.stdout
    procs = []
    in_pss = False
    for line in out.splitlines():
        if line.startswith("Total PSS by process"):
            in_pss = True
            continue
        if in_pss:
            m = re.match(r"\s*([\d,]+)K: (\S+) \(pid (\d+)", line)
            if m and "chrome" in m.group(2):
                procs.append({"pss_mb": round(int(m.group(1).replace(",", "")) / 1024), "name": m.group(2), "pid": int(m.group(3))})
            elif not m and line.strip() == "":
                break
    ps = sh("adb shell 'ps -A -o PID,RSS,NAME | grep -i chrome'").stdout
    chrome = "dumpsys rc %s, %d bytes, stderr %s\n" % (r.returncode, len(out), r.stderr[:200]) + "\n".join(l for l in out.splitlines() if "chrome" in l or "Total PSS" in l) + "\nps (pid, RSS KB, name):\n" + ps
    if not procs:  # no PSS list: fall back to the RSS of the processes
        for l in ps.splitlines():
            m = re.match(r"\s*(\d+)\s+(\d+)\s+(\S+)", l)
            if m:
                procs.append({"pss_mb": round(int(m.group(2)) / 1024), "name": m.group(3), "pid": int(m.group(1)), "rss": True})
    return sorted(procs, key=lambda p: ("sandboxed" not in p["name"], -p["pss_mb"])), chrome


def take_shot(name):
    path = os.path.join(A.out, name + ".png")
    if A.platform == "ios":
        r = sh(["xcrun", "simctl", "io", A.udid, "screenshot", path])
    elif A.platform == "android":
        with open(path, "wb") as f:
            r = subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)
    else:
        r = None
    return r


def take_mem(name):
    if A.platform == "ios":
        procs = ios_webcontent()
        MEM[name] = {"webcontent": procs[:3], "mb": (procs[0]["footprint_mb"] or procs[0]["rss_mb"]) if procs else None,
                     "basis": "footprint" if procs and procs[0]["footprint_mb"] else "rss"}
        if not procs:
            log("mem: no WebContent process found; processes: " + sh("ps -axo pid=,rss=,command= | grep -i webkit | cut -c1-200").stdout[:600])
    elif A.platform == "android":
        procs, raw = android_chrome()
        renderer = [p for p in procs if "sandboxed" in p["name"]]
        MEM[name] = {"processes": procs[:6], "mb": renderer[0]["pss_mb"] if renderer else None, "basis": "PSS of the renderer",
                     "chrome_total_mb": sum(p["pss_mb"] for p in procs)}
        with open(os.path.join(A.out, "meminfo-%s.txt" % name), "w") as f:
            f.write(raw)
    else:
        MEM[name] = {"mb": None}
    log("mem %s: %s" % (name, json.dumps(MEM[name])[:400]))


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, *a):
        pass

    def reply(self, code, body=b"", ctype="text/plain", headers=()):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in headers:
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(n).decode("utf-8", "replace") if n else ""
        path, _, q = self.path.partition("?")
        name = re.sub(r"[^\w-]", "", q.partition("name=")[2]) or "x"
        SEEN.set()
        LAST[0] = time.time()
        if path == "/__log":
            log(body)
        elif path == "/__shot":
            r = take_shot(name)
            if r is not None and getattr(r, "returncode", 0) != 0:
                log("shot %s failed: %s" % (name, (getattr(r, "stderr", "") or "")[:200]))
        elif path == "/__canvas":
            import base64
            with open(os.path.join(A.out, "canvas-%s.png" % name), "wb") as f:
                f.write(base64.b64decode(body))
        elif path == "/__mem":
            take_mem(name)
        elif path == "/__done":
            RESULT.update(json.loads(body))
            DONE.set()
        self.reply(200, b"ok")

    def do_GET(self):
        path, _, query = self.path.partition("?")
        if A.upstream:
            return self.proxy(path, query)
        return self.local(path)

    def local(self, path):
        # the same gzip rules as serve-compressed.py (what GitHub Pages does to .js, and to the .gz files)
        root = os.path.abspath(A.site)
        full = os.path.normpath(os.path.join(root, urllib.parse.unquote(path).lstrip("/") or "index.html"))
        if os.path.isdir(full):
            full = os.path.join(full, "index.html")
        if not full.startswith(root) or not os.path.isfile(full):
            return self.reply(404, b"not found")
        data = open(full, "rb").read()
        ctype = mimetypes.guess_type(full)[0] or "application/octet-stream"
        headers = [("Cache-Control", "no-store")]
        if full.endswith("index.html"):
            data = inject(data)
        elif "gzip" in self.headers.get("Accept-Encoding", "") and full.endswith((".js", ".gz")):
            import gzip
            if full.endswith(".js"):
                data = gzip.compress(data, 6)
            ctype = "application/javascript" if full.endswith(".js") else "application/octet-stream"
            headers.append(("Content-Encoding", "gzip"))
        rng = re.match(r"bytes=(\d+)-(\d*)", self.headers.get("Range", ""))
        if rng and "Content-Encoding" not in dict(headers):
            a = int(rng.group(1)); b = int(rng.group(2)) if rng.group(2) else len(data) - 1
            headers.append(("Content-Range", "bytes %d-%d/%d" % (a, b, len(data))))
            return self.reply(206, data[a:b + 1], ctype, headers)
        self.reply(200, data, ctype, headers + [("Accept-Ranges", "bytes")])

    def proxy(self, path, query=""):
        url = A.upstream.rstrip("/") + (path if path != "/" else "/index.html") + (("?" + query) if query else "")
        req = urllib.request.Request(url, headers={k: v for k, v in self.headers.items() if k.lower() in ("range", "accept", "user-agent")})
        try:
            r = urllib.request.urlopen(req, timeout=120)
        except urllib.error.HTTPError as e:
            r = e
        except Exception as e:
            return self.reply(502, str(e).encode())
        data = r.read()
        code = r.status if hasattr(r, "status") else r.code
        if path in ("/", "/index.html"):
            data = inject(data)
        keep = [(k, v) for k, v in r.headers.items() if k.lower() in ("content-encoding", "content-range", "accept-ranges")]
        self.reply(code, data, r.headers.get("Content-Type", "application/octet-stream"), keep + [("Cache-Control", "no-store")])


class Server(http.server.ThreadingHTTPServer):
    daemon_threads = True


def main():
    global A
    p = argparse.ArgumentParser()
    p.add_argument("--platform", choices=["ios", "android", "none"], required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--site")
    p.add_argument("--upstream")
    p.add_argument("--udid", default="booted")
    p.add_argument("--port", type=int, default=PORT_DEFAULT)
    p.add_argument("--cap", type=float, default=2.0, help="the page's FORGE_MAX_PIXEL_RATIO")
    p.add_argument("--timeout", type=int, default=1500)
    p.add_argument("--query", default="", help="extra query parameters for the page URL")
    p.add_argument("--launch", help="command that opens the page ({url}); default per platform")
    A = p.parse_args()
    if bool(A.site) == bool(A.upstream):
        sys.exit("give --site or --upstream")
    os.makedirs(A.out, exist_ok=True)
    srv = Server(("127.0.0.1", A.port), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    url = "http://localhost:%d/index.html?test=1&seed=1%s" % (A.port, ("&" + A.query) if A.query else "")
    start = time.time()

    def launch():
        if A.launch:
            r = sh(A.launch.format(url=url))
        elif A.platform == "ios":
            r = sh(["xcrun", "simctl", "openurl", A.udid, url])
        elif A.platform == "android":
            sh(["adb", "shell", "am", "force-stop", "com.android.chrome"])
            r = sh(["adb", "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "'" + url + "'", "com.android.chrome"])
        else:
            r = None
        if r is not None:
            log("launch: rc %s %s" % (r.returncode, (r.stdout + r.stderr).strip()[:300]))

    # The browser sometimes starts without loading the page (a first-run screen, a stuck tab): if the probe has not
    # called in after 4 minutes, take a picture and open the page again, up to twice.
    for attempt in range(3):
        launch()
        if SEEN.wait(240):
            break
        take_shot("not-loaded-%d" % attempt)
        log("the page did not call in within 240 s (attempt %d)" % (attempt + 1))
    # While the probe runs it logs every few seconds; if it goes quiet for 5 minutes the browser has died (an Android tab
    # killed, a crash): open the page again, up to twice, and the probe starts over.
    relaunches = 0
    while not DONE.wait(30):
        if time.time() - start > A.timeout:
            break
        if time.time() - LAST[0] > 300 and relaunches < 2:
            relaunches += 1
            take_shot("died-%d" % relaunches)
            log("no word from the page for 5 minutes; opening it again (%d)" % relaunches)
            LAST[0] = time.time()
            launch()
    checks = RESULT.get("checks", [])
    ok = DONE.is_set() and bool(checks) and all(c["ok"] for c in checks) and not RESULT.get("errors")
    summary = {"platform": A.platform, "site": A.upstream or "built site", "ok": ok, "finished": DONE.is_set(), "seconds": round(time.time() - start),
               "checks": checks, "console_errors": RESULT.get("errors", []), "warnings": RESULT.get("warnings", [])[:10], "warning_count": len(RESULT.get("warnings", [])), "memory": MEM, "ua": RESULT.get("ua")}
    with open(os.path.join(A.out, "summary.json"), "w") as f:
        json.dump(summary, f, indent=1)
    for c in checks:
        print(("PASS " if c["ok"] else "FAIL ") + c["name"] + (": " + c["detail"] if c["detail"] else ""))
    print("memory:", {k: v.get("mb") for k, v in MEM.items()})
    md = os.environ.get("GITHUB_STEP_SUMMARY")
    if md:
        with open(md, "a") as f:
            f.write("### Device run: %s, %s\n\n%s in %d s. Memory (MB, %s): %s\n\n| check | result |\n|---|---|\n" % (
                A.platform, summary["site"], "PASS" if ok else "FAIL", summary["seconds"],
                next((m.get("basis") for m in MEM.values() if m.get("basis")), "n/a"), ", ".join("%s %s" % (k, v.get("mb")) for k, v in MEM.items())))
            for c in checks:
                f.write("| %s | %s %s |\n" % (c["name"], "pass" if c["ok"] else "**FAIL**", c["detail"][:150].replace("|", "/")))
    if not DONE.is_set():
        take_shot("timeout")
        print("FAIL: the probe did not report within %d s" % A.timeout)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
