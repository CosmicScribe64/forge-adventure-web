// Page-side self-test for real devices (the iOS Simulator's Mobile Safari and the Android emulator's Chrome),
// injected into index.html by web/tools/device-run.py, which also takes the screenshots and memory readings
// the probe asks for. It does what scripts/e2e-boot and e2e-newgame do through webtest.py, with the same game
// test API (window.forgeTest.cmd): boot to the title screen, display-check, audio-check, a new game to the
// overworld by touch taps, then a town visit, the VS screen and a duel for screenshots.
// Everything is reported to the server (POST /__log, /__shot, /__mem, /__done). Needs ?test=1 in the URL.
(() => {
  const CFG = window.__deviceProbe || {};  // {allowed: [regex source], cap: 2, device: name}
  const allowed = (CFG.allowed || []).map(s => new RegExp(s));
  const cap = CFG.cap || 2;
  const checks = [], errors = [], lines = [], t0 = Date.now();
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const post = (path, body) => fetch(path, { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body) }).then(r => r.text()).catch(() => "");
  const log = m => { const s = "[" + ((Date.now() - t0) / 1000).toFixed(0) + "s] " + m; post("/__log", s); };

  setInterval(() => post("/__hb", ""), 20000);
  log("probe injected, readyState " + document.readyState);

  // Console capture, from before the game starts. Errors that are not on the known-harmless list fail the run.
  const note = (kind, msg) => {
    msg = String(msg);
    if (kind === "error" && !allowed.some(re => re.test(msg))) errors.push(msg.slice(0, 300));
  };
  for (const k of ["log", "info", "warn", "error"]) {
    const orig = console[k].bind(console);
    console[k] = (...a) => {
      let m; try { m = a.map(String).join(" "); } catch (e) { m = "?"; }
      if (k !== "log" || lines.length < 5000) lines.push(m);
      note(k, m);
      orig(...a);
    };
  }
  window.addEventListener("error", e => note("error", e.message ? "uncaught " + e.message : "resource failed: " + ((e.target && (e.target.src || e.target.href)) || "?")), true);
  window.addEventListener("unhandledrejection", e => note("error", "unhandled rejection: " + (e.reason && e.reason.message || e.reason)));

  const check = (name, ok, detail) => { checks.push({ name, ok: !!ok, detail: detail === undefined ? "" : String(detail) }); log((ok ? "PASS " : "FAIL ") + name + (detail ? ": " + detail : "")); };
  const cmd = async c => {
    // a command the game doesn't answer within 60 s (a device under load) is an error, not an endless wait
    const r = await Promise.race([window.forgeTest.cmd(c), sleep(60000).then(() => { throw new Error("no answer to '" + c + "' in 60 s"); })]); try { return JSON.parse(r); } catch (e) { return { raw: r }; } };
  const until = async (what, secs, f) => {
    const end = Date.now() + secs * 1000;
    while (Date.now() < end) {
      try { const v = await f(); if (v) return v; } catch (e) {}
      const fatal = errors.find(e => /Fatal Error/.test(e));
      if (fatal) throw new Error("the game died while waiting for " + what + ": " + fatal.slice(0, 200));
      await sleep(1000);
    }
    throw new Error("timeout waiting for " + what + " after " + secs + "s");
  };
  const untilLine = (needle, secs) => until("console line " + needle, secs, () => lines.some(l => l.includes(needle)));
  const untilScene = (pred, secs, what) => until(what, secs, async () => {
    if (!window.forgeTest || document.getElementById("loading")) return false;
    const s = await Promise.race([cmd("state"), sleep(5000).then(() => null)]);
    return s && pred(s.scene) ? s : false;
  });
  // The game's own picture, read from its canvas right after it drew a frame (the drawing buffer is only readable then)
  // and sent to the server as canvas-NAME.png. It is the game without the browser's bars, and it exists even where the
  // device's screenshot can't capture a WebGL surface (the Android emulator's comes out black). Returns the PNG's size.
  const canvasShot = name => new Promise(res => requestAnimationFrame(() => requestAnimationFrame(() => {
    try {
      const url = document.getElementById("canvas").toDataURL("image/png");
      post("/__canvas?name=" + name, url.slice(url.indexOf(",") + 1)).then(() => res(url.length));
    } catch (e) { res("error " + e.message); }
  })));
  const shot = async name => {
    await sleep(500);
    const n = await canvasShot(name);
    log("shot " + name + ", canvas png " + n + " chars");
    await post("/__shot?name=" + name, "");
  };

  const mem = async name => { log("mem " + name); await post("/__mem?name=" + name, ""); };

  // A finger: touchstart and touchend on the canvas where the game says the button is (the game reads touch events).
  const tap = async text => {
    const w = await cmd("where " + text);
    if (w.error) throw new Error("tap " + text + ": " + w.error);
    const c = document.getElementById("canvas"), k = c.clientWidth / w.w, x = w.x * k, y = w.y * k;
    const t = new Touch({ identifier: 1, target: c, clientX: x, clientY: y, pageX: x, pageY: y });
    const ev = (type, on) => c.dispatchEvent(new TouchEvent(type, { touches: on ? [t] : [], targetTouches: on ? [t] : [], changedTouches: [t], bubbles: true, cancelable: true }));
    ev("touchstart", true); await sleep(80); ev("touchend", false);
    log("tap " + text + " at " + Math.round(x) + "," + Math.round(y));
  };

  // The same assertions as webtest's display-check, on the visible viewport.
  const displayCheck = async label => {
    const c = document.getElementById("canvas"), r = c.getBoundingClientRect(), vv = window.visualViewport;
    const at = (x, y) => { const e = document.elementFromPoint(x, y); return e ? e.id || e.tagName : null; };
    const g = await cmd("display");
    const vw = vv && vv.scale <= 1.01 ? vv.width : innerWidth, vh = vv && vv.scale <= 1.01 ? vv.height : innerHeight;
    const dpr = window.devicePixelRatio, ratio = Math.max(1, Math.min(dpr, cap));
    const ex = [Math.round(vw * ratio), Math.round(vh * ratio)];
    const problems = [];
    if (Math.abs(r.width - vw) > 1 || Math.abs(r.height - vh) > 1 || Math.abs(r.left) > 0.5 || Math.abs(r.top) > 0.5) problems.push("canvas css box " + [r.left, r.top, r.width, r.height].map(Math.round) + " is not the visible viewport " + vw + "x" + vh);
    if (Math.abs(c.width - ex[0]) > 1 || Math.abs(c.height - ex[1]) > 1) problems.push("backing store " + c.width + "x" + c.height + " is not css x ratio " + ratio + ": " + ex);
    if (g.w !== Math.round(vw) || g.h !== Math.round(vh) || g.bw !== c.width || g.bh !== c.height) problems.push("the game sees " + JSON.stringify(g));
    if (scrollX !== 0 || scrollY !== 0 || document.documentElement.scrollWidth > innerWidth || document.documentElement.scrollHeight > innerHeight) problems.push("the page is scrolled or larger than the window: " + [scrollX, scrollY, document.documentElement.scrollWidth, document.documentElement.scrollHeight] + " in " + innerWidth + "x" + innerHeight);
    const corners = [at(1, 1), at(innerWidth - 2, 1), at(1, innerHeight - 2), at(innerWidth - 2, innerHeight - 2)];
    if (corners.some(x => x !== "canvas")) problems.push("something covers the canvas at the corners: " + corners);
    check("display " + label, !problems.length, problems.length ? problems.join("; ") : "css " + Math.round(r.width) + "x" + Math.round(r.height) + " backing " + c.width + "x" + c.height + " ratio " + ratio + " dpr " + dpr + " inner " + innerWidth + "x" + innerHeight + " screen " + screen.width + "x" + screen.height);
  };
  // Can a script scroll the page? (webtest's display-check does the same; real Safari moves the page even when the
  // document is no larger than the window, which shifts the canvas under the bars.) Scrolls back afterwards.
  const scrollCheck = async label => {
    window.scrollTo(0, 100); await sleep(400);
    const y = scrollY, top = document.getElementById("canvas").getBoundingClientRect().top;
    window.scrollTo(0, 0); await sleep(400);
    check("page does not scroll " + label, y === 0, "scrollTo(0,100) gives scrollY " + y + ", canvas top " + Math.round(top));
  };
  const audioCheck = label => {
    const n = window.Howler ? Howler._howls.filter(h => h.playing()).length : 0;
    check("audio off " + label, n === 0, n + " of " + (window.Howler ? Howler._howls.length : 0) + " Howl objects playing");
  };
  const errorCheck = label => check("no console errors " + label, !errors.length, errors.slice(0, 5).join(" | "));
  const step = async (name, f) => { try { await f(); } catch (e) { check(name, false, e.message || e); throw e; } };

  const run = async () => {
    log("probe on " + navigator.userAgent + " dpr " + devicePixelRatio + " inner " + innerWidth + "x" + innerHeight + " screen " + screen.width + "x" + screen.height);
    try {
      await step("boot to title", async () => {
        const s = await untilScene(sc => sc === "StartScene", CFG.bootSeconds || 600, "the title screen");
        check("boot to title", true, "after " + Math.round((Date.now() - t0) / 1000) + "s");
      });
      await sleep(4000);
      await shot("title"); await mem("title");
      // The same two requests the game makes for card data and images (it logged HTTP code -1 for the images in iOS Safari).
      for (const [what, url] of [["api", "https://api.scryfall.com/cards/named?exact=Lightning+Bolt"], ["image", "https://api.scryfall.com/cards/gk2/105/en?format=image&version=normal"]]) {
        try { const r = await fetch(url); check("scryfall " + what + " fetch", r.ok, r.status + " " + r.headers.get("content-type") + (what === "image" ? " " + (await r.blob()).size + " bytes" : "")); }
        catch (e) { check("scryfall " + what + " fetch", false, e.name + ": " + e.message); }
      }
      await displayCheck("title"); await scrollCheck("title"); audioCheck("title"); errorCheck("title");
      await step("new game screen", async () => { await tap("New Game"); await untilLine("ui/new_game", 90); await sleep(3000); });
      await shot("create"); audioCheck("create");
      await step("new game to overworld", async () => {
        await tap("start");
        // (the world generator logs from a worker, which this page can't hear; the scene change says it is done)
        await untilScene(sc => ["TileMapScene", "GameScene"].includes(sc), 1200, "the overworld");
        await sleep(8000);
        // The intro: dismiss the dialogs the way scripts/play does, then the overworld is free.
        for (const c of ["dismiss", "click Where am I", "dismiss", "interact Adept Black Wizard"]) { await cmd(c); await sleep(1500); }
        await cmd("dismiss"); await sleep(1000);
        await cmd("click Why am I here?"); await sleep(1000); await cmd("dismiss"); await cmd("click done"); await sleep(2000);
        await cmd("click done"); await sleep(3000); await cmd("dismiss"); await sleep(1500);
      });
      await shot("overworld"); await mem("overworld");
      await displayCheck("overworld"); await scrollCheck("overworld"); audioCheck("overworld"); errorCheck("overworld");
      // The rest only needs to get somewhere to look at; a failure is reported but the results so far stand.
      try {
        await cmd("goto portal"); await sleep(8000); await cmd("dismiss"); await sleep(2000);
        await cmd("console spawn enemy \"Clay Golem\""); await sleep(1500);
        // Walk to the golem; a dialog can interrupt the walk ("interrupted by dialog"), so dismiss it and go again.
        let d = null, vsShots = 0;
        for (let i = 0; i < 8 && !d; i++) {
          await cmd("dismiss"); await sleep(500);
          let r = await cmd("goto Clay Golem"); log("goto: " + JSON.stringify(r).slice(0, 100));
          if (r.error) { await cmd("console spawn enemy \"Clay Golem\""); await sleep(1000); r = await cmd("goto Clay Golem"); log("goto again: " + JSON.stringify(r).slice(0, 100)); }
          for (let j = 0; j < 12 && !d; j++) {
            await sleep(1500);
            const x = await cmd("duel");
            const st = await Promise.race([cmd("state"), sleep(4000).then(() => ({}))]);
            if (j % 4 === 0) log("scene " + st.scene);
            if (x && x.turn !== undefined) d = x;
            else if (st.scene && !["TileMapScene", "GameScene"].includes(st.scene)) { d = { turn: "-", phase: "scene " + st.scene }; }
            else if (vsShots < 2 && j === 2) { await shot("vs-" + "ab"[vsShots++]); }
          }
        }
        if (!d) throw new Error("no duel after 8 walks to the golem");
        await displayCheck("vs");
        if (d.turn === "-") { try { d = await until("the duel game", 60, async () => { const x = await cmd("duel"); return x && x.turn !== undefined ? x : false; }); } catch (e) { log("no duel game yet: " + e.message); } }
        await sleep(4000); await shot("duel"); await mem("duel");
        check("duel reached", true, "turn " + d.turn + " phase " + d.phase);
        await displayCheck("duel"); errorCheck("duel");
      } catch (e) {
        let st = ""; try { st = JSON.stringify(await cmd("state")).slice(0, 300); } catch (x) {}
        await shot("vs-duel-failed").catch(() => {});
        check("vs screen and duel", false, (e.message || e) + " | state " + st);
      }
    } catch (e) {
      log("stopped: " + (e.message || e));
      await shot("last").catch(() => {});
    }
    await post("/__done", { checks, errors, seconds: Math.round((Date.now() - t0) / 1000), ua: navigator.userAgent });
  };
  // Start once the page has loaded, so forgeTest exists when the first command goes out.
  if (document.readyState === "complete") run(); else window.addEventListener("load", () => run());
})();
