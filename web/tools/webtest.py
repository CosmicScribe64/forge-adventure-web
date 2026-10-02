"""Drive the web build in headless Chromium: capture the console and take screenshots.

Run it through scripts/webtest, for example:
  scripts/webtest --wait 60 --shot out/boot.png
  scripts/webtest --wait 60 --steps 'click 640 400; wait 5; shot out/menu.png; key Escape'

Steps (separated by ';'):
  wait <seconds>           let the game run
  click <x> <y>            click at page coordinates (viewport is --width x --height)
  key <name>               press a key (Playwright key names, e.g. Enter, Escape, ArrowUp)
  hold <name> <seconds>    hold a key down (movement keys need this; a press is too short)
  type <text>              type text
  shot <path>              screenshot to <path> (relative to the repo root)
  resize <w> <h>           change the viewport size (the window being resized)
  reload                   reload the page (same browser profile, so IndexedDB saves survive)
  js <expression>          evaluate JavaScript in the page and print the (JSON) result
  api <command>            the game's test harness (forgeweb.test.WebTest): state, moveto X Y,
                           goto NAME, click TEXT, duel, ok, cancel, play CARD, player NAME, ...
  until <text> <seconds>   wait until a console line contains <text> (fails after <seconds>)
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
--latency <ms> and --mbps <n> emulate a hosted page (round-trip delay, bandwidth) with Chrome's
network emulation.
--interactive <file>: after the steps, keep the page open and run each new line appended to
<file> as a step (same syntax; "quit" ends). Each command's output goes to stdout, so run it
in the background and append commands, e.g. echo 'hold ArrowRight 0.5; shot out/a.png' >> out/cmd.txt

The console log is written to --log (default out/console.log), line by line.

Watchdog: every --heartbeat seconds (default 15) a "[hb]" line reports the time since the last
console line, and the memory (RSS) and CPU of Chromium's renderer and GPU processes. These are
read from /proc, so it works even when the tab is hung. A tab crash is reported as "[tab crashed]"
and ends the run (exit code 4). After --max-time seconds (default 900) the run is abandoned (exit
code 3).
"""
import argparse
import os
import shlex
import sys
import threading
import time

from playwright.sync_api import sync_playwright

ROOT = "/work"


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
            if "chrom" not in cmd:
                continue
            kind = "renderer" if "--type=renderer" in cmd else "gpu" if "--type=gpu-process" in cmd else None
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
    ap.add_argument("--wait", type=float, default=0, help="seconds to wait before the steps")
    ap.add_argument("--shot", help="screenshot after the initial wait")
    ap.add_argument("--steps", default="")
    ap.add_argument("--log", default="out/console.log")
    ap.add_argument("--heartbeat", type=float, default=15, help="seconds between [hb] lines (0 = off)")
    ap.add_argument("--interactive", help="file to read further steps from, line by line")
    ap.add_argument("--heap-sampling", action="store_true",
                    help="record live allocations from page load (for the heap step)")
    ap.add_argument("--max-time", type=float, default=900, help="abandon the run after this many seconds")
    ap.add_argument("--latency", type=float, default=0,
                    help="add this many ms to every request (Chrome network emulation, sync XHR too): "
                         "what a hosted page feels like, where the local server answers at once")
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
        browser = p.chromium.launch(args=[
            "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist",
            "--enable-webgl", "--disable-dev-shm-usage",
        ])
        page = browser.new_page(viewport={"width": args.width, "height": args.height},
                                device_scale_factor=args.scale, is_mobile=args.mobile, has_touch=args.mobile)
        page.on("console", on_console)
        page.on("crash", lambda *_: crashed.append(True))
        if args.latency or args.mbps:
            net = page.context.new_cdp_session(page)
            net.send("Network.enable")
            net.send("Network.emulateNetworkConditions", {
                "offline": False, "latency": args.latency,
                "downloadThroughput": args.mbps * 125000 if args.mbps else -1, "uploadThroughput": -1})
        dbg = None
        if "stacks" in args.steps or "exceptions" in args.steps or args.interactive:
            # Must be attached before load: once the page is busy it can't be enabled.
            dbg = page.context.new_cdp_session(page)
            dbg.send("Debugger.enable")
            paused_events = []
            dbg.on("Debugger.paused", lambda e: paused_events.append(e))
        heap_cdp = None
        if args.heap_sampling:
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
                    page.mouse.click(float(parts[1]), float(parts[2]))
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
                elif cmd == "until":
                    needle, limit = parts[1], float(parts[2])
                    seen = len(lines)
                    end = time.time() + limit
                    found = False
                    while time.time() < end and not found:
                        pump(1)
                        found = any(needle in l for l in lines)
                    print(f"until '{needle}': {'found' if found else 'NOT FOUND'} after {time.time() - (end - limit):.0f}s")
                    if not found:
                        sys.exit(2)
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
                        browser.close()
                        log.close()
                        return
                    print(f"> {line}", flush=True)
                    try:
                        run_steps(line)
                    except SystemExit as e:
                        print(f"step failed: {e}", flush=True)
                    print("ok", flush=True)

        browser.close()
    log.close()
    print(f"console: {len(lines)} lines -> {args.log}")


if __name__ == "__main__":
    main()
