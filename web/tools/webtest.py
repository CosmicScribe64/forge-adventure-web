"""Drive the web build in headless Chromium: capture the console and take screenshots.

Run it through scripts/webtest, for example:
  scripts/webtest --wait 60 --shot out/boot.png
  scripts/webtest --wait 60 --steps 'click 640 400; wait 5; shot out/menu.png; key Escape'

Steps (separated by ';'):
  wait <seconds>           let the game run
  click <x> <y>            click at page coordinates (viewport is --width x --height)
  tap <button text>        real input on a button: asks the game where the button is (api where) and taps it with
                           the touchscreen in a touch context, or clicks it with the mouse otherwise. Unlike
                           `api click`, which calls the stage directly, this goes through the browser's input path
  wheel <x> <y> <dy> [<n>] scroll the mouse wheel n times (default 1) by dy pixels, with the pointer at x y
  key <name>               press a key (Playwright key names, e.g. Enter, Escape, ArrowUp)
  hold <name> <seconds>    hold a key down (movement keys need this; a press is too short)
  type <text>              type text
  shot <path>              screenshot to <path> (relative to the repo root)
  resize <w> <h>           change the viewport size (the window being resized)
  reload                   reload the page (same browser profile, so IndexedDB saves survive)
  js <expression>          evaluate JavaScript in the page and print the (JSON) result
  api <command>            the game's test harness (forgeweb.test.WebTest): state, moveto X Y,
                           goto NAME, click TEXT, addcards N, fsstats, duel, ok, cancel, play CARD, player NAME, ...
  until <text> <seconds>   wait until a console line contains <text> (fails after <seconds>)
  expect <js expression>   fail the run (exit code 1) unless the expression is truthy in the page
  until-new <text> <seconds>
                           like until, but only console lines logged after this step starts count
  until-state <cond> <seconds>
                           once the loading screen is gone, poll the harness's `api state` once a
                           second until the JavaScript
                           condition is truthy (the parsed state is the variable `state`, for
                           example: state.scene == 'StartScene'); exit code 1 after <seconds>
  no-errors                fail the run (exit code 1) if the page has logged an unexpected error
  measure <name>           garbage-collect, then record renderer RSS, JS heap and (with
                           --init-script web/tools/glhook.js) live WebGL texture memory, all in MB,
                           under <name> and print them (pix is the wasm pixmap heap, window.forgePixmaps.heapMB)
  assert-max <name>.<field> <limit>
                           fail the run unless a value recorded by `measure` is at most <limit>
                           (fields: rss, heap, tex, gpu, pix)
  assert-growth <name>.<field> <name2>.<field> <margin>
                           fail the run unless the second measurement is at most <margin> above the first
  no-repeat-downloads [<min MB>]
                           fail the run if a file of <min MB> (default 1) or more was requested
                           more than once with the same URL and Range header
  snapshot <path>          write a V8 heap snapshot (.heapsnapshot, open in DevTools or parse it)
  heap <path>              garbage-collect, then write the JS heap totals and (with --heap-sampling)
                           which functions allocated the memory still alive, to <path>
  profile <seconds> <path> CPU-profile the page; writes the top functions (self time) to <path>
  stacks <n> <every> <path> pause the page <n> times, <every> seconds apart, and write each call
                           stack to <path> (works even while the page's main thread never yields)
  exceptions <n> <path> [<match> [<seconds>]]
                           pause on every thrown exception, dump the first <n> (message and call
                           stack) to <path>, then stop pausing. With <match>, only exceptions
                           whose message, or a function name in whose stack, contains <match>
                           count, and the others are resumed silently. Gives up after <seconds>
                           (default 30) without one.
--browser chromium|webkit|firefox picks the engine (default chromium). --device "iPhone 13" applies one
of Playwright's device descriptors (viewport, scale, user agent, mobile, touch), and --phone is a
Chromium phone of 390x844 at scale 3 with a mobile user agent. In a touch context `click` taps with the
touchscreen (touchstart, touchend), as a finger does. Only Chromium has the DevTools protocol, so `heap`,
`snapshot`, `profile`, `stacks`, `exceptions`, --latency and --mbps need it. `measure` works everywhere but
reports what the engine offers: renderer RSS from /proc (WPEWebProcess for the WebKit that Playwright ships on Linux), the JS heap only in
Chromium (WebKit has no performance.memory), and the page's own counters (textures, pixmap heap, wasm
memory). `assert-max` on a value the engine can't report prints "skipped" instead of failing.
--latency <ms> and --mbps <n> emulate a hosted page (round-trip delay, bandwidth) with Chrome's
network emulation.
--interactive <file>: after the steps, keep the page open and run each new line appended to
<file> as a step (same syntax; "quit" ends). Each command's output goes to stdout, so run it
in the background and append commands, e.g. echo 'hold ArrowRight 0.5; shot out/a.png' >> out/cmd.txt

Assertions: `expect`, `until-state` and `no-errors` print "FAIL: ..." and end the run with exit
code 1 (a plain `until` that times out still exits with 2). Every console "error" line and every
uncaught page error is collected; those matching ALLOWED_ERRORS below (known harmless lines) are
ignored. With --strict the run also fails at the end if any other error was logged, so a scenario
needs no explicit `no-errors`. Without --strict errors are only listed, so existing uses are unchanged.

The console log is written to --log (default out/console.log), line by line.

Watchdog: every --heartbeat seconds (default 15) a "[hb]" line reports the time since the last
console line, and the memory (RSS) and CPU of Chromium's renderer and GPU processes. These are
read from /proc, so it works even when the tab is hung. A tab crash is reported as "[tab crashed]"
and ends the run (exit code 4). After --max-time seconds (default 900) the run is abandoned (exit
code 3).
"""
import argparse
import json
import os
import re
import shlex
import sys
import threading
import time

from playwright.sync_api import sync_playwright

ROOT = "/work"

# Console errors (and page errors) that a healthy boot logs. Anything else fails a --strict run.
# Each entry is (regular expression searched in the message, why it is harmless). Keep this short:
# every line here is a line a real bug could hide behind.
ALLOWED_ERRORS = [
    (r"^Failed to load: fallback_skin/(title_bg_lq|title_bg_lq_portrait|transition)\.png!\. Creating dummy texture\.",
     "Forge's Assets loads the fallback skin before the real skin; its two images are read through "
     "a path that is not there yet. The real skin loads right after (ui/title_bg.png ... Found!)."),
    (r"^The card .* was not assigned to any set\. Adding it to UNKNOWN set",
     "Forge logs this with System.err for card scripts whose edition file doesn't list them (the "
     "A- Alchemy rebalances and a few others); it is the same on desktop."),
    (r"^Upcoming set .* dated in the future\. All `upcoming` cards",
     "Forge's edition data contains sets released after the date the build was made (Star Trek, "
     "2026-11-13); the line goes away once that date passes."),
    (r"^\[suppressed\] java\.io\.IOException \(while throwing java\.io\.IOException\)",
     "forgeweb.compat.JdkCompat reports an exception added with addSuppressed while another was "
     "being thrown, from an optional file that Forge probes and handles (the same boot also logs "
     "'Error reading matrix data: FileNotFoundException' at info level)."),
]


# What a page can say about its own memory in any engine. performance.memory is Chromium only and
# measureUserAgentSpecificMemory needs cross-origin isolation, so most engines return little; the wasm
# memories are found the way the `heap` step finds them.
MEMORY_JS = """async () => {
    const o = {wasm: 0};
    if (performance.memory) o.heap = performance.memory.usedJSHeapSize / 1048576;
    for (const k of Object.keys(window)) {
        try {
            const v = window[k];
            const buf = v && v.HEAP8 ? v.HEAP8.buffer : (v instanceof WebAssembly.Memory ? v.buffer : null);
            if (buf) o.wasm += buf.byteLength / 1048576;
        } catch (e) {}
    }
    o.wasm = Math.round(o.wasm);
    if (performance.measureUserAgentSpecificMemory && self.crossOriginIsolated) {
        try { o.uam = (await performance.measureUserAgentSpecificMemory()).bytes / 1048576; } catch (e) {}
    }
    return o;
}"""


def allowed_index(text):
    """Index of the ALLOWED_ERRORS entry that matches the message, or None."""
    for i, (rx, _) in enumerate(ALLOWED_ERRORS):
        if re.search(rx, text):
            return i
    return None


def write_profile(prof, path):
    nodes = {n["id"]: n for n in prof["nodes"]}
    parent = {}
    for n in prof["nodes"]:
        for c in n.get("children", []):
            parent[c] = n["id"]
    self_time = {}
    for sample in prof.get("samples", []):
        f = nodes[sample]["callFrame"]
        key = f"{f['functionName'] or '(anonymous)'}  {f['url'].rsplit('/', 1)[-1]}:{f['lineNumber']}"
        self_time[key] = self_time.get(key, 0) + 1
    total = sum(self_time.values()) or 1
    hottest = sorted(self_time.items(), key=lambda kv: -kv[1])[:40]
    # Inclusive time: samples whose stack contains the function (counted once per sample).
    inclusive = {}
    for sample in prof.get("samples", []):
        seen = set()
        nid = sample
        while nid in nodes:
            name = nodes[nid]["callFrame"]["functionName"] or "(anonymous)"
            if name not in seen:
                seen.add(name)
                inclusive[name] = inclusive.get(name, 0) + 1
            nid = parent.get(nid)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        for key, count in hottest:
            out.write(f"{100.0 * count / total:5.1f}%  {key}\n")
        out.write("\n# inclusive (function and everything it calls)\n")
        skip = {"(root)", "(anonymous)", "(program)", "(idle)", "(garbage collector)"}
        for name, count in sorted(inclusive.items(), key=lambda kv: -kv[1])[:70]:
            if name not in skip:
                out.write(f"{100.0 * count / total:5.1f}%  {name}\n")
        out.write("\n# stacks of the 5 hottest sample nodes\n")
        by_node = {}
        for sample in prof.get("samples", []):
            by_node[sample] = by_node.get(sample, 0) + 1
        for node_id, count in sorted(by_node.items(), key=lambda kv: -kv[1])[:5]:
            out.write(f"\n{100.0 * count / total:5.1f}%\n")
            nid = node_id
            depth = 0
            while nid in nodes and depth < 30:
                f = nodes[nid]["callFrame"]
                out.write(f"    {f['functionName'] or '(anonymous)'}\n")
                nid = parent.get(nid)
                depth += 1


def chromium_stats():
    """Returns {'renderer': (rss_mb, cpu_seconds), 'gpu': ...} from /proc, for the biggest process of each type."""
    out = {}
    hz = os.sysconf("SC_CLK_TCK")
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open(f"/proc/{pid}/cmdline", "rb") as f:
                cmd = f.read().decode(errors="replace")
            if "chrom" in cmd:
                kind = "renderer" if "--type=renderer" in cmd else "gpu" if "--type=gpu-process" in cmd else None
            elif "WebKitWebProcess" in cmd or "WPEWebProcess" in cmd:
                kind = "renderer"
            elif "WebKitGPUProcess" in cmd:
                kind = "gpu"
            elif "firefox" in cmd or "plugin-container" in cmd:
                kind = "renderer" if "tab" in cmd else None
            else:
                continue
            if kind is None:
                continue
            with open(f"/proc/{pid}/stat") as f:
                fields = f.read().rsplit(")", 1)[1].split()
            cpu = (int(fields[11]) + int(fields[12])) / hz
            rss = int(fields[21]) * os.sysconf("SC_PAGE_SIZE") / (1024 * 1024)
            if kind not in out or rss > out[kind][0]:
                out[kind] = (rss, cpu)
        except (OSError, IndexError, ValueError):
            continue
    return out


def write_heap(prof, totals, path):
    """Live memory by allocating function (self) and by call-stack prefix, from a sampling profile."""
    self_size = {}
    stack_size = {}

    def walk(node, stack):
        f = node["callFrame"]
        name = f["functionName"] or "(anonymous)"
        stack = stack + [name]
        self_size[name] = self_size.get(name, 0) + node["selfSize"]
        if node["selfSize"]:
            # The innermost few frames usually say what the memory is for.
            key = " < ".join(reversed(stack[-6:]))
            stack_size[key] = stack_size.get(key, 0) + node["selfSize"]
        for c in node.get("children", []):
            walk(c, stack)

    walk(prof["head"], [])
    total = sum(self_size.values()) or 1
    mb = lambda b: b / (1024 * 1024)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        for k, v in totals.items():
            out.write(f"{k}: {v}\n")
        out.write(f"sampled live total: {mb(total):.1f} MB\n\n# by allocating function\n")
        for name, size in sorted(self_size.items(), key=lambda kv: -kv[1])[:60]:
            out.write(f"{mb(size):8.1f} MB  {100.0 * size / total:5.1f}%  {name}\n")
        out.write("\n# by call stack (innermost first)\n")
        for key, size in sorted(stack_size.items(), key=lambda kv: -kv[1])[:60]:
            out.write(f"{mb(size):8.1f} MB  {key}\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://host.docker.internal:8090/index.html?test=1")
    ap.add_argument("--width", type=int, default=1280)
    ap.add_argument("--height", type=int, default=720)
    ap.add_argument("--scale", type=float, default=1, help="device pixel ratio (phones are 2-3)")
    ap.add_argument("--mobile", action="store_true", help="emulate a phone: mobile viewport and touch")
    ap.add_argument("--browser", default="chromium", choices=["chromium", "webkit", "firefox"],
                    help="browser engine (the Playwright image has all three)")
    ap.add_argument("--device", help='a Playwright device descriptor, for example "iPhone 13"')
    ap.add_argument("--phone", action="store_true",
                    help="Chromium phone: 390x844 at scale 3, mobile, touch, a mobile user agent")
    ap.add_argument("--wait", type=float, default=0, help="seconds to wait before the steps")
    ap.add_argument("--shot", help="screenshot after the initial wait")
    ap.add_argument("--init-script", help="JavaScript file to run in every page before its own scripts (hooks)")
    ap.add_argument("--steps", default="")
    ap.add_argument("--load-state", help="browser storage (IndexedDB, localStorage) file to start with, as written by --save-state")
    ap.add_argument("--save-state", help="write the browser storage (IndexedDB, localStorage) to this file when the run ends")
    ap.add_argument("--log", default="out/console.log")
    ap.add_argument("--heartbeat", type=float, default=15, help="seconds between [hb] lines (0 = off)")
    ap.add_argument("--interactive", help="file to read further steps from, line by line")
    ap.add_argument("--heap-sampling", action="store_true",
                    help="record live allocations from page load (for the heap step)")
    ap.add_argument("--max-time", type=float, default=900, help="abandon the run after this many seconds")
    ap.add_argument("--latency", type=float, default=0,
                    help="add this many ms to every request (Chrome network emulation, sync XHR too): "
                         "what a hosted page feels like, where the local server answers at once")
    ap.add_argument("--strict", action="store_true",
                    help="exit with code 1 at the end if the page logged an error not in ALLOWED_ERRORS")
    ap.add_argument("--mbps", type=float, default=0, help="limit the download speed (megabits/s)")
    args = ap.parse_args()

    log_path = os.path.join(ROOT, args.log)
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    log = open(log_path, "w", encoding="utf-8")
    lines = []
    start = time.time()
    last_console = [start]
    log_lock = threading.Lock()
    crashed = []
    errors = []  # unexpected console errors and page errors, as log lines
    allowed_seen = {}
    measured = {}  # `measure` results by name

    def fail(message):
        emit(f"[{time.time() - start:7.1f}s] [FAIL] {message}", echo=True)
        print(f"FAIL: {message}", flush=True)
        for e in errors[:20]:
            print(f"  error: {e[:300]}", flush=True)
        log.close()
        os._exit(1)

    def emit(text, echo=False):
        with log_lock:
            log.write(text + "\n")
            log.flush()
        if echo:
            print(text, flush=True)

    def on_console(msg):
        text = f"[{time.time() - start:7.1f}s] [{msg.type}] {msg.text}"
        lines.append(text)
        last_console[0] = time.time()
        emit(text)
        if msg.type in ("error", "pageerror"):
            index = allowed_index(msg.text)
            if index is None:
                errors.append(text)
            else:
                allowed_seen[index] = allowed_seen.get(index, 0) + 1

    def watchdog():
        prev = {}
        prev_t = time.time()
        next_hb = time.time() + args.heartbeat if args.heartbeat > 0 else float("inf")
        while True:
            time.sleep(1)
            now = time.time()
            if now - start > args.max_time:
                emit(f"[{now - start:7.1f}s] [watchdog] max time {args.max_time:.0f}s reached; "
                     f"last console line {now - last_console[0]:.0f}s ago: {lines[-1] if lines else '(none)'}", echo=True)
                os._exit(3)
            if now >= next_hb:
                stats = chromium_stats()
                parts = []
                for kind in ("renderer", "gpu"):
                    if kind in stats:
                        rss, cpu = stats[kind]
                        pct = 100 * (cpu - prev[kind]) / (now - prev_t) if kind in prev else 0
                        parts.append(f"{kind} {rss:.0f} MB {pct:.0f}% cpu")
                prev = {k: v[1] for k, v in stats.items()}
                prev_t = now
                emit(f"[{now - start:7.1f}s] [hb] last console {now - last_console[0]:.0f}s ago; "
                     + ("; ".join(parts) or "no renderer process"), echo=True)
                next_hb = now + args.heartbeat

    threading.Thread(target=watchdog, daemon=True).start()

    def pump(seconds):
        """Wait while still delivering console events (the page may block its main thread)."""
        end = time.time() + seconds
        while time.time() < end:
            if crashed:
                emit(f"[{time.time() - start:7.1f}s] [tab crashed] giving up", echo=True)
                os._exit(4)
            try:
                page.wait_for_timeout(min(500, max(1, (end - time.time()) * 1000)))
            except Exception:
                time.sleep(0.5)

    def shot(path):
        full = os.path.join(ROOT, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        try:
            page.screenshot(path=full, timeout=60000)
            print(f"screenshot: {path}")
        except Exception as e:
            print(f"screenshot failed ({path}): {e}")

    with sync_playwright() as p:
        chromium = args.browser == "chromium"
        if chromium:
            browser = p.chromium.launch(args=[
                "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist",
                "--enable-webgl", "--disable-dev-shm-usage",
            ])
        else:
            browser = getattr(p, args.browser).launch()
        if args.device:
            ctx = dict(p.devices[args.device])
        elif args.phone:
            ctx = dict(viewport={"width": 390, "height": 844}, device_scale_factor=3, is_mobile=True, has_touch=True,
                       user_agent="Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                                  "Chrome/140.0.0.0 Mobile Safari/537.36")
        else:
            ctx = dict(viewport={"width": args.width, "height": args.height},
                       device_scale_factor=args.scale, is_mobile=args.mobile, has_touch=args.mobile)
        touch = bool(ctx.get("has_touch"))
        print(f"browser: {args.browser} {browser.version}; context: "
              f"{ {k: v for k, v in ctx.items() if k != 'user_agent'} }; user agent: {ctx.get('user_agent', '(default)')}", flush=True)
        page = browser.new_page(storage_state=args.load_state or None, **ctx)
        if args.init_script:
            page.add_init_script(path=args.init_script)
        def save_state():
            if args.save_state:
                os.makedirs(os.path.dirname(os.path.abspath(args.save_state)), exist_ok=True)
                page.context.storage_state(path=args.save_state, indexed_db=True)
                print(f"browser storage: {args.save_state}")

        page.on("console", on_console)
        requests = {}  # (url, range header) -> [response or None, ...], in request order

        def on_request_finished(request):
            try:
                response = request.response()
                key = (request.url, request.headers.get("range", ""))
                requests.setdefault(key, []).append(response)
            except Exception:
                pass

        page.on("requestfinished", on_request_finished)
        # A browser's own "Failed to load resource" console line names no URL (WebKit, Chromium for a 404),
        # so log the URL here, as [netfail] lines in the log (not counted as errors).
        page.on("response", lambda r: emit(f"[{time.time() - start:7.1f}s] [netfail] {r.status} {r.url}")
                if r.status >= 400 else None)
        page.on("requestfailed", lambda r: emit(f"[{time.time() - start:7.1f}s] [netfail] {r.failure} {r.url}"))
        page.on("crash", lambda *_: crashed.append(True))
        if (args.latency or args.mbps) and chromium:
            net = page.context.new_cdp_session(page)
            net.send("Network.enable")
            net.send("Network.emulateNetworkConditions", {
                "offline": False, "latency": args.latency,
                "downloadThroughput": args.mbps * 125000 if args.mbps else -1, "uploadThroughput": -1})
        dbg = None
        if chromium and ("stacks" in args.steps or "exceptions" in args.steps or args.interactive):
            # Must be attached before load: once the page is busy it can't be enabled.
            dbg = page.context.new_cdp_session(page)
            dbg.send("Debugger.enable")
            paused_events = []
            dbg.on("Debugger.paused", lambda e: paused_events.append(e))
        heap_cdp = None
        if args.heap_sampling and chromium:
            heap_cdp = page.context.new_cdp_session(page)
            heap_cdp.send("HeapProfiler.enable")
            heap_cdp.send("HeapProfiler.startSampling", {"samplingInterval": 32768})
        page.on("pageerror", lambda e: on_console(type("M", (), {"type": "pageerror", "text": str(e)})()))
        page.goto(args.url, wait_until="commit", timeout=120000)
        pump(args.wait)
        if args.shot:
            shot(args.shot)

        def run_steps(text):
            for step in [x.strip() for x in text.split(";") if x.strip()]:
                parts = ["js"] if step.startswith("js ") else ["api"] if step.startswith("api ") else shlex.split(step)
                cmd = parts[0]
                print(f"step: {step}")
                if cmd == "wait":
                    pump(float(parts[1]))
                elif cmd == "click":
                    if touch:
                        page.touchscreen.tap(float(parts[1]), float(parts[2]))
                    else:
                        page.mouse.click(float(parts[1]), float(parts[2]))
                elif cmd == "tap":
                    text = step[len("tap"):].strip()
                    where = json.loads(page.evaluate("t => window.forgeTest.cmd('where ' + t)", text))
                    if "error" in where:
                        fail(f"tap {text}: {where['error']}")
                    # The game reports canvas pixels; the canvas may be scaled to the page by CSS.
                    k = page.evaluate("() => document.getElementById('canvas').clientWidth") / where["w"]
                    x, y = where["x"] * k, where["y"] * k
                    if touch:
                        page.touchscreen.tap(x, y)
                    else:
                        page.mouse.click(x, y)
                    print(f"tap {text}: ({x:.0f}, {y:.0f})", flush=True)
                elif cmd == "wheel":
                    page.mouse.move(float(parts[1]), float(parts[2]))
                    for _ in range(int(parts[4]) if len(parts) > 4 else 1):
                        page.mouse.wheel(0, float(parts[3]))
                        pump(0.05)
                elif cmd == "key":
                    page.keyboard.press(parts[1])
                elif cmd == "hold":
                    page.keyboard.down(parts[1])
                    pump(float(parts[2]))
                    page.keyboard.up(parts[1])
                elif cmd == "type":
                    page.keyboard.type(" ".join(parts[1:]))
                elif cmd == "shot":
                    shot(parts[1])
                elif cmd == "api":
                    arg = step[len("api"):].strip()
                    try:
                        print("api:", page.evaluate("c => window.forgeTest ? window.forgeTest.cmd(c) : '{\"error\":\"no harness\"}'", arg), flush=True)
                    except Exception as e:
                        print("api failed:", e, flush=True)
                elif cmd == "js":
                    expr = step[len("js"):].strip()
                    try:
                        print("js:", page.evaluate(expr), flush=True)
                    except Exception as e:
                        print("js failed:", e, flush=True)
                elif cmd == "reload":
                    lines.clear()
                    page.reload(wait_until="commit", timeout=120000)
                elif cmd == "resize":
                    page.set_viewport_size({"width": int(parts[1]), "height": int(parts[2])})
                elif cmd == "heap":
                    cdp = heap_cdp or page.context.new_cdp_session(page)
                    cdp.send("HeapProfiler.collectGarbage")
                    usage = cdp.send("Runtime.getHeapUsage")
                    stats = chromium_stats()
                    wasm = page.evaluate("""() => {
                        const out = {};
                        for (const k of Object.keys(window)) {
                            try {
                                const v = window[k];
                                const buf = v && v.HEAP8 ? v.HEAP8.buffer : (v instanceof WebAssembly.Memory ? v.buffer : null);
                                if (buf) out[k] = Math.round(buf.byteLength / 1048576);
                            } catch (e) {}
                        }
                        return out;
                    }""")
                    totals = {
                        "wasm memories MB": wasm,
                        "pixmaps": page.evaluate("() => window.forgePixmaps || null"),
                        "backing stores MB": round(usage.get("backingStorageSize", 0) / 1048576, 1),
                        "embedder heap MB": round(usage.get("embedderHeapUsedSize", 0) / 1048576, 1),
                        "js heap used MB": round(usage["usedSize"] / 1048576, 1),
                        "js heap total MB": round(usage["totalSize"] / 1048576, 1),
                        "renderer rss MB": round(stats.get("renderer", (0, 0))[0]),
                        "gpu rss MB": round(stats.get("gpu", (0, 0))[0]),
                    }
                    prof = cdp.send("HeapProfiler.getSamplingProfile")["profile"] if heap_cdp else {"head": {"callFrame": {"functionName": "(no sampling)"}, "selfSize": 0}}
                    large = page.evaluate("""() => {
                        const m = window.forgeLargePixmaps;
                        if (!m) return [];
                        return [...m.values()].sort((a, b) => b.mb - a.mb).slice(0, 40).map(p =>
                            `${p.mb} MB ${p.w}x${p.h}\\n` + (p.stack || '').split('\\n').slice(3, 24).map(l => '      ' + l.trim()).join('\\n'));
                    }""")
                    write_heap(prof, totals, os.path.join(ROOT, parts[1]))
                    with open(os.path.join(ROOT, parts[1]), "a") as f:
                        f.write("\n# largest live pixmaps (allocation stacks)\n")
                        for entry in large:
                            f.write(entry + "\n")
                    print(f"heap: {parts[1]} {totals}")
                elif cmd == "snapshot":
                    # snapshot PATH: a V8 heap snapshot (.heapsnapshot), including ArrayBuffer backing stores.
                    cdp = page.context.new_cdp_session(page)
                    cdp.send("HeapProfiler.enable")
                    cdp.send("HeapProfiler.collectGarbage")
                    snap = open(os.path.join(ROOT, parts[1]), "w")
                    cdp.on("HeapProfiler.addHeapSnapshotChunk", lambda e: snap.write(e["chunk"]))
                    cdp.send("HeapProfiler.takeHeapSnapshot", {"reportProgress": False})
                    snap.close()
                    print(f"snapshot: {parts[1]}")
                elif cmd == "profile":
                    seconds, out_path = float(parts[1]), parts[2]
                    cdp = page.context.new_cdp_session(page)
                    cdp.send("Profiler.enable")
                    cdp.send("Profiler.setSamplingInterval", {"interval": 1000})
                    cdp.send("Profiler.start")
                    pump(seconds)
                    prof = cdp.send("Profiler.stop")["profile"]
                    write_profile(prof, os.path.join(ROOT, out_path))
                    print(f"profile: {out_path}")
                elif cmd == "stacks":
                    n, every, out_path = int(parts[1]), float(parts[2]), parts[3]
                    full = os.path.join(ROOT, out_path)
                    os.makedirs(os.path.dirname(full), exist_ok=True)
                    with open(full, "w") as out:
                        for i in range(n):
                            pump(every)
                            paused_events.clear()
                            dbg.send("Debugger.pause")
                            t_end = time.time() + 20
                            while not paused_events and time.time() < t_end:
                                page.wait_for_timeout(50)  # delivers CDP events (time.sleep would not)
                            if not paused_events:
                                out.write(f"--- sample {i}: no pause event\n")
                                continue
                            frames = paused_events[-1]["callFrames"]
                            out.write(f"--- sample {i} at {time.time() - start:.0f}s ({len(frames)} frames)\n")
                            for f in frames[:40]:
                                out.write(f"    {f['functionName'] or '(anonymous)'}:{f['location']['lineNumber']}\n")
                            dbg.send("Debugger.resume")
                            out.flush()
                    print(f"stacks: {out_path}")
                elif cmd == "exceptions":
                    count, out_path = int(parts[1]), parts[2]
                    match = parts[3] if len(parts) > 3 else None
                    patience = float(parts[4]) if len(parts) > 4 else 30
                    full = os.path.join(ROOT, out_path)
                    os.makedirs(os.path.dirname(full), exist_ok=True)
                    with open(full, "w") as out:
                        dbg.send("Debugger.setPauseOnExceptions", {"state": "all"})
                        skipped = 0
                        for i in range(count):
                            ev = None
                            t_end = time.time() + patience
                            while ev is None and time.time() < t_end:
                                paused_events.clear()
                                while not paused_events and time.time() < t_end:
                                    page.wait_for_timeout(50)
                                if not paused_events:
                                    break
                                cand = paused_events[-1]
                                desc = (cand.get("data") or {}).get("description", "")
                                if match and match not in desc and not any(
                                        match in (f["functionName"] or "") for f in cand["callFrames"]):
                                    skipped += 1
                                    dbg.send("Debugger.resume")
                                    continue
                                ev = cand
                            if ev is None:
                                out.write(f"--- exception {i}: no event ({skipped} skipped)\n")
                                break
                            data = ev.get("data") or {}
                            out.write(f"--- exception {i} at {time.time() - start:.1f}s\n")
                            out.write(f"    {data.get('description', '')}\n")
                            for f in ev["callFrames"][:30]:
                                out.write(f"    {f['functionName'] or '(anonymous)'}:{f['location']['lineNumber']}\n")
                            out.flush()
                            dbg.send("Debugger.resume")
                        dbg.send("Debugger.setPauseOnExceptions", {"state": "none"})
                    print(f"exceptions: {out_path}")
                elif cmd in ("until", "until-new"):
                    # until-new only looks at console lines logged after the step starts (for a
                    # line that an earlier part of the run has already produced once).
                    needle, limit = parts[1], float(parts[2])
                    seen = len(lines) if cmd == "until-new" else 0
                    end = time.time() + limit
                    found = False
                    while time.time() < end and not found:
                        pump(1)
                        found = any(needle in l for l in lines[seen:])
                    print(f"until '{needle}': {'found' if found else 'NOT FOUND'} after {time.time() - (end - limit):.0f}s")
                    if not found:
                        sys.exit(2)
                elif cmd == "display-check":
                    # The canvas matches the visible viewport and the pixel ratio (cap = the page's
                    # FORGE_MAX_PIXEL_RATIO, 2 unless given), the game's own size agrees, and the page can't scroll.
                    cap = float(parts[1]) if len(parts) > 1 else 2.0
                    page.evaluate("() => window.scrollTo(0, 100)")
                    d = page.evaluate("""() => {
                        const c = document.getElementById('canvas'), r = c.getBoundingClientRect(), vv = window.visualViewport;
                        const at = (x, y) => { const e = document.elementFromPoint(x, y); return e ? e.id || e.tagName : null; };
                        return { dpr: window.devicePixelRatio, inner: [innerWidth, innerHeight],
                          vv: vv ? [vv.width, vv.height, vv.scale] : null, rect: [r.left, r.top, r.width, r.height],
                          backing: [c.width, c.height], scroll: [scrollX, scrollY],
                          doc: [document.documentElement.scrollWidth, document.documentElement.scrollHeight],
                          corners: [at(1, 1), at(innerWidth - 2, 1), at(1, innerHeight - 2), at(innerWidth - 2, innerHeight - 2)] };
                    }""")
                    g = json.loads(page.evaluate("() => window.forgeTest.cmd('display')"))
                    vw, vh = (d["vv"][0], d["vv"][1]) if d["vv"] and d["vv"][2] <= 1.01 else d["inner"]
                    ratio = max(1.0, min(d["dpr"], cap))
                    ex = (round(vw * ratio), round(vh * ratio))
                    problems = []
                    if abs(d["rect"][2] - vw) > 1 or abs(d["rect"][3] - vh) > 1 or abs(d["rect"][0]) > 0.5 or abs(d["rect"][1]) > 0.5:
                        problems.append(f"canvas css box {d['rect']} is not the visible viewport {vw}x{vh}")
                    if tuple(d["backing"]) != ex:
                        problems.append(f"backing store {d['backing']} is not css x ratio {ratio}: {ex}")
                    if (g["w"], g["h"]) != (round(vw), round(vh)) or (g["bw"], g["bh"]) != tuple(d["backing"]):
                        problems.append(f"the game sees {g}, the page {d}")
                    if d["scroll"] != [0, 0] or d["doc"][0] > d["inner"][0] or d["doc"][1] > d["inner"][1]:
                        problems.append(f"the page scrolls: {d['scroll']} {d['doc']} in {d['inner']}")
                    if any(x != "canvas" for x in d["corners"]):
                        problems.append(f"something covers the canvas at the corners: {d['corners']}")
                    print(f"display: css {d['rect'][2]:.0f}x{d['rect'][3]:.0f} backing {d['backing'][0]}x{d['backing'][1]} ratio {ratio} dpr {d['dpr']} game {g['w']}x{g['h']} (buffer {g['bw']}x{g['bh']})", flush=True)
                    if problems:
                        fail("display-check: " + "; ".join(problems))
                elif cmd == "expect":
                    expr = step[len("expect"):].strip()
                    try:
                        value = page.evaluate(expr)
                    except Exception as e:
                        fail(f"expect {expr}: evaluation failed: {str(e)[:200]}")
                    if not value:
                        fail(f"expect {expr}: got {json.dumps(value)}")
                    print(f"expect ok: {expr}", flush=True)
                elif cmd == "until-state":
                    cond, limit = step[len("until-state"):].strip().rsplit(None, 1)
                    end = time.time() + float(limit)
                    state, ok = None, False
                    while time.time() < end and not ok:
                        pump(1)
                        try:
                            # The harness answers between frames; give up on one poll after 5 s.
                            # Not before the loading screen is gone (the game has drawn a frame): a command
                            # queued on the UI thread while Forge is still starting up can kill it.
                            raw = page.evaluate("""() => window.forgeTest && !document.getElementById('loading') ? Promise.race([window.forgeTest.cmd('state'),
                                new Promise(r => setTimeout(() => r(null), 5000))]) : null""")
                            state = json.loads(raw) if raw else None
                            ok = bool(state) and bool(page.evaluate(
                                "([c, state]) => !!(new Function('state', 'return (' + c + ')'))(state)", [cond, state]))
                        except Exception:
                            ok = False
                    if not ok:
                        fail(f"until-state {cond}: not reached in {limit}s; last state: {json.dumps(state)[:300]}")
                    print(f"until-state '{cond}': reached after {time.time() - (end - float(limit)):.0f}s", flush=True)
                elif cmd == "measure":
                    usage = None
                    if chromium:
                        cdp = heap_cdp or page.context.new_cdp_session(page)
                        cdp.send("HeapProfiler.collectGarbage")
                        usage = cdp.send("Runtime.getHeapUsage")
                    stats = chromium_stats()
                    gl = page.evaluate("() => window.__gl ? window.__gl.summary(0) : null")
                    pm = page.evaluate("() => window.forgePixmaps || null")
                    js = page.evaluate(MEMORY_JS)
                    heap = round(usage["usedSize"] / 1048576, 1) if usage else (
                        round(js["heap"], 1) if js.get("heap") is not None else None)
                    measured[parts[1]] = {
                        "pix": pm["heapMB"] if pm else None,
                        "pixlive": pm["live"] if pm else None,
                        "rss": round(stats["renderer"][0]) if "renderer" in stats else None,
                        "gpu": round(stats["gpu"][0]) if "gpu" in stats else None,
                        "heap": heap,
                        "tex": round(gl["texMB"], 1) if gl else None,
                        "wasm": js.get("wasm"),
                        "uam": round(js["uam"], 1) if js.get("uam") is not None else None,
                    }
                    m = measured[parts[1]]
                    print(f"measure {parts[1]} ({args.browser}): renderer rss {m['rss']} MB, js heap {m['heap']} MB, "
                          f"texture {m['tex']} MB, gpu rss {m['gpu']} MB, wasm pixmap heap {m['pix']} MB "
                          f"({m['pixlive']} live), wasm memories {m['wasm']} MB, "
                          f"measureUserAgentSpecificMemory {m['uam']} MB", flush=True)
                elif cmd == "assert-max":
                    name, field = parts[1].split(".")
                    limit = float(parts[2])
                    value = measured.get(name, {}).get(field)
                    if value is None and not chromium and name in measured:
                        print(f"assert-max skipped: {parts[1]} is not measurable in {args.browser}", flush=True)
                        continue
                    if value is None:
                        fail(f"assert-max {parts[1]}: no such measurement (run `measure {name}` first, "
                             "and give --init-script web/tools/glhook.js for tex)")
                    if value > limit:
                        fail(f"assert-max {parts[1]}: {value} MB is above the limit {limit:g} MB")
                    print(f"assert-max ok: {parts[1]} {value} <= {limit:g} MB", flush=True)
                elif cmd == "assert-growth":
                    a_name, field = parts[1].split(".")
                    b_name, field2 = parts[2].split(".")
                    margin = float(parts[3])
                    a = measured.get(a_name, {}).get(field)
                    b = measured.get(b_name, {}).get(field2)
                    if (a is None or b is None) and not chromium and a_name in measured and b_name in measured:
                        print(f"assert-growth skipped: {parts[1]} or {parts[2]} is not measurable in {args.browser}", flush=True)
                        continue
                    if a is None or b is None:
                        fail(f"assert-growth {parts[1]} {parts[2]}: missing measurement")
                    elif b - a > margin:
                        fail(f"assert-growth: {parts[2]} is {b - a:g} above {parts[1]} (limit {margin:g})")
                    else:
                        print(f"assert-growth ok: {parts[2]} - {parts[1]} = {b - a:g} (limit {margin:g})", flush=True)
                elif cmd == "no-repeat-downloads":
                    min_bytes = float(parts[1] if len(parts) > 1 else 1) * 1048576
                    repeated = []
                    for (url, rng), responses in requests.items():
                        if len(responses) < 2 or url.startswith(("data:", "blob:")):
                            continue
                        size = 0
                        for response in responses:
                            try:
                                size = max(size, len(response.body()))  # decoded size
                            except Exception:
                                size = max(size, int((response.headers.get("content-length") or 0)))
                        if size >= min_bytes:
                            repeated.append(f"{url.rsplit('/', 1)[-1][:80]} {rng} x{len(responses)} ({size / 1048576:.1f} MB)")
                    if repeated:
                        fail("downloaded more than once: " + "; ".join(repeated))
                    print(f"no-repeat-downloads ok ({len(requests)} distinct requests)", flush=True)
                elif cmd == "no-errors":
                    if errors:
                        fail(f"{len(errors)} unexpected console/page error(s)")
                    print("no-errors ok", flush=True)
                else:
                    sys.exit(f"unknown step: {step}")

        run_steps(args.steps)

        if args.interactive:
            cmd_path = os.path.join(ROOT, args.interactive)
            open(cmd_path, "a").close()
            done_lines = sum(1 for _ in open(cmd_path))
            print(f"interactive: append steps to {args.interactive}", flush=True)
            while True:
                pump(0.5)
                with open(cmd_path) as f:
                    lines_now = f.read().splitlines()
                for line in lines_now[done_lines:]:
                    done_lines += 1
                    if line.strip() == "quit":
                        save_state()
                        browser.close()
                        log.close()
                        return
                    print(f"> {line}", flush=True)
                    try:
                        run_steps(line)
                    except SystemExit as e:
                        print(f"step failed: {e}", flush=True)
                    print("ok", flush=True)

        save_state()
        browser.close()
    log.close()
    print(f"console: {len(lines)} lines -> {args.log}")
    if errors:
        print(f"unexpected errors: {len(errors)}")
        for e in errors[:20]:
            print(f"  {e[:300]}")
    if allowed_seen:
        print("allowed errors seen: " + ", ".join(f"{n}x {ALLOWED_ERRORS[i][0][:40]}" for i, n in sorted(allowed_seen.items())))
    if args.strict and errors:
        print("FAIL: unexpected console/page errors (--strict)")
        sys.exit(1)


if __name__ == "__main__":
    main()
