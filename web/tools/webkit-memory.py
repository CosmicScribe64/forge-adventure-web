#!/usr/bin/env python3
"""Memory of Playwright's Linux WebKit (WPE) web process while the game starts and idles at the title screen.

Prints, for one page load of the game in the iPhone 13 profile:
  - the time of each loading stage (window.forgeMark) with the web process RSS at that moment,
  - the RSS and VmHWM when the title screen is up,
  - with --idle N, the RSS once a second for N seconds at the title screen (minimum, median, maximum), which is a
    sawtooth: the JavaScript heap grows until JavaScriptCore collects it (wiki/concepts/memory-budget.md),
  - with --newgame, the same after starting a new game (seed 1): the RSS and VmHWM at the overworld and, with --idle N, the
    minimum, median and maximum of N more seconds there,
  - the anonymous mappings of the process by size, from /proc/<pid>/smaps.
JavaScriptCore options reach the web process through the environment, for example JSC_useJIT=0, JSC_forceRAMSize=1610612736,
or JSC_logGC=basic together with DEBUG=pw:browser (the collector's log, with heap sizes, goes to the driver's stderr).
It has to run where the WebKit process is visible in /proc, that is inside the Playwright container:
  docker run --rm --ipc=host --add-host host.docker.internal:host-gateway -v "$PWD":/work -w /work \\
    -e JSC_logGC=basic -e DEBUG=pw:browser mcr.microsoft.com/playwright/python:v1.55.0-noble \\
    bash -c 'pip install -q playwright==1.55.0 && python web/tools/webkit-memory.py http://host.docker.internal:8097/forge-adventure-web/ --idle 60'
(scripts/serve-site serves the assembled site on 8097.) One run takes about 40 s plus the idle time; the numbers move by a few
hundred MB between runs, so compare minimum, median, maximum and VmHWM of several runs.
"""
import argparse
import collections
import glob
import json
import re
import time

from playwright.sync_api import sync_playwright


def web_pid():
    best, best_rss = None, 0
    for d in glob.glob("/proc/[0-9]*"):
        try:
            if "WPEWebProcess" not in open(d + "/cmdline").read():
                continue
            rss = int(open(d + "/statm").read().split()[1]) * 4096 / 1048576
        except (OSError, ValueError, IndexError):
            continue
        if rss > best_rss:
            best, best_rss = int(d[6:]), rss
    return best, best_rss


def hwm_mb(pid):
    return int(re.search(r"VmHWM:\s+(\d+)", open(f"/proc/{pid}/status").read()).group(1)) / 1024


def idle_series(page, seconds):
    series = []
    for _ in range(seconds):
        page.wait_for_timeout(1000)
        series.append(round(web_pid()[1]))
    ordered = sorted(series)
    return "min %d, median %d, max %d MB; every 5th second: %s" % (ordered[0], ordered[len(ordered) // 2], ordered[-1], series[::5])


def game_state(page):
    try:
        raw = page.evaluate("() => window.forgeTest ? Promise.race([window.forgeTest.cmd('state'), new Promise(r => setTimeout(() => r(null), 5000))]) : null")
        return json.loads(raw).get("scene") if raw else None
    except Exception:
        return None


def anon_summary(pid):
    buckets, size, perm = collections.defaultdict(lambda: [0.0, 0]), 0, ""
    for line in open(f"/proc/{pid}/smaps"):
        m = re.match(r"^([0-9a-f]+)-([0-9a-f]+) (\S+) \S+ \S+ \S+\s*(.*)$", line)
        if m:
            size = (int(m.group(2), 16) - int(m.group(1), 16)) / 1048576
            perm, anon = m.group(3), not m.group(4)
            continue
        if line.startswith("Rss:") and anon:
            key = "<1 MB" if size < 1 else "1-8 MB" if size < 8 else "8-64 MB" if size < 64 else ">=64 MB"
            buckets[key][0] += int(line.split()[1]) / 1024
            buckets[key][1] += 1
    return {k: (round(v[0]), v[1]) for k, v in buckets.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("url", help="the site root, for example http://host.docker.internal:8097/forge-adventure-web/")
    ap.add_argument("--idle", type=int, default=0, help="seconds to sample the RSS at the title screen")
    ap.add_argument("--device", default="iPhone 13")
    ap.add_argument("--newgame", action="store_true", help="then start a new game and measure at the overworld too")
    args = ap.parse_args()
    url = args.url.rstrip("/") + "/index.html?test=1&seed=1"
    with sync_playwright() as p:
        browser = p.webkit.launch()
        page = browser.new_page(**p.devices[args.device])
        t0 = time.time()
        seen = set()

        def on_console(msg):
            if msg.text.startswith("MARK ") and msg.text not in seen:
                seen.add(msg.text)
                print("%5.1fs  rss %4.0f MB  %s" % (time.time() - t0, web_pid()[1], msg.text[5:]))

        def log_marks(route):
            response = route.fetch()
            body = response.text().replace("window.forgeMark = name => {", "window.forgeMark = name => { console.log('MARK ' + name);", 1)
            route.fulfill(response=response, body=body)

        page.on("console", on_console)
        page.route("**/index.html*", log_marks)
        page.goto(url)
        while time.time() - t0 < 240:
            page.wait_for_timeout(500)
            try:
                raw = page.evaluate("() => window.forgeTest && !document.getElementById('loading') ? Promise.race([window.forgeTest.cmd('state'),"
                                    " new Promise(r => setTimeout(() => r(null), 5000))]) : null")
                if raw and json.loads(raw).get("scene") == "StartScene":
                    break
            except Exception:
                pass
        pid, rss = web_pid()
        print("title screen at %.0f s: rss %.0f MB, VmHWM %.0f MB" % (time.time() - t0, rss, hwm_mb(pid)))
        if args.idle:
            print("title idle %d s: %s" % (args.idle, idle_series(page, args.idle)))
        if args.newgame:
            page.evaluate("() => window.forgeTest.cmd('click New Game')")
            page.wait_for_timeout(5000)
            page.evaluate("() => window.forgeTest.cmd('click start')")
            t1 = time.time()
            while time.time() - t1 < 400 and game_state(page) not in ("TileMapScene", "GameScene"):
                page.wait_for_timeout(1000)
            page.wait_for_timeout(10000)
            pid, rss = web_pid()
            print("overworld at %.0f s: scene %s, rss %.0f MB, VmHWM %.0f MB" % (time.time() - t0, game_state(page), rss, hwm_mb(pid)))
            if args.idle:
                print("overworld idle %d s: %s" % (args.idle, idle_series(page, args.idle)))
            print("VmHWM at the end: %.0f MB" % hwm_mb(web_pid()[0]))
        print("anonymous mappings (rss MB, count) by size:", anon_summary(pid))
        browser.close()


if __name__ == "__main__":
    main()
