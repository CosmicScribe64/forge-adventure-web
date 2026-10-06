// Counts BigInt allocations (TeaVM's long arithmetic) per frame and samples the call stacks of every 20th one.
// Give it to webtest: scripts/webtest --init-script web/tools/bigint-hook.js --steps "...; js __bigReset(); wait 10; js __bigSnap(); js JSON.stringify(__stk)"
// __bigSnap() gives the frame count and the calls of BigInt.asIntN, BigInt.asUintN and BigInt(); __stk maps a stack
// (innermost frame first, as line:column in app.js) to its sample count. web/tools/bigint-decode.py turns the stacks into
// Java source lines with the source map (a TEAVM_SOURCE_MAP=true build). The hooks slow the game down a lot; compare counts per frame.
(() => {
  Error.stackTraceLimit = 12;
  const C = window.__big = { asIntN: 0, asUintN: 0, ctor: 0 }, S = window.__stk = {};
  window.__stkOn = false; let n = 0, frames = 0;
  const strip = l => l.replace(/^\s*at /, '').replace(/https?:\/\/[^ )]*\/app\.js[^:]*:/, '').replace(/^\S+ \(/, '').replace(/\)$/, '');
  function samp() { n++; if (window.__stkOn && n % 20 === 0) { const s = new Error().stack.split('\n').slice(3, 10).map(strip).join('|'); S[s] = (S[s] || 0) + 1; } }
  const a = BigInt.asIntN, b = BigInt.asUintN;
  BigInt.asIntN = function (x, y) { C.asIntN++; samp(); return a(x, y); };
  BigInt.asUintN = function (x, y) { C.asUintN++; samp(); return b(x, y); };
  const O = window.BigInt;
  window.BigInt = new Proxy(O, { apply(t, th, args) { C.ctor++; samp(); return t(...args); } });
  const raf = window.requestAnimationFrame;
  window.requestAnimationFrame = function (cb) { return raf.call(window, t => { frames++; cb(t); }); };
  window.__bigReset = () => { C.asIntN = C.asUintN = C.ctor = 0; frames = 0; n = 0; for (const k in S) delete S[k]; window.__stkOn = true; return 'ok'; };
  window.__bigSnap = () => JSON.stringify({ frames, ...C });
})();
