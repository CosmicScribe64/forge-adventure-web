// Init script for web/tools/webtest.py (--init-script): counts the live WebGL memory (textures with
// their mip levels, buffers, renderbuffers, the canvas back buffers) by wrapping the WebGL calls.
// window.__gl.summary(n) returns {texMB, texN, bufMB, rbMB, backbufferMB, topTex, groups, hist, ...}
// (topTex is the n biggest textures with the stack that uploaded them). Used by the `measure` step.
(() => {
  const T = new Map(), B = new Map(), R = new Map(); // object -> info
  const st = { ctxs: 0 };
  const bind = { tex: {}, buf: {}, rb: null, unit: 33984 };
  const isGL2 = (gl) => typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext;
  const stack = () => (new Error().stack || '').split('\n').slice(5, 22).map(l => l.trim().replace(/^at /, '').replace(/ \(.*$/, '').slice(0, 60)).filter(l => !/glTexImage|WebGL|Texture_load|__init|\$gl/.test(l)).slice(0, 7).join(' < ');
  function bpp(ifmt, fmt, type) {
    const sized = { 0x8229: 1, 0x822B: 2, 0x8058: 4, 0x8C43: 4, 0x8C41: 4, 0x8051: 3, 0x881A: 8, 0x8814: 16, 0x8D62: 2, 0x8056: 2, 0x8057: 2, 0x8D48: 1,
      0x81A5: 2, 0x81A6: 3, 0x81A7: 4, 0x88F0: 4, 0x8CAC: 4, 0x8DAD: 5, 0x8059: 4, 0x8C3A: 4, 0x8F97: 1, 0x8236: 4, 0x822F: 4 };
    if (sized[ifmt]) return ifmt === 0x8051 ? 4 : sized[ifmt]; // RGB8 padded to 4 by GPUs
    if (type === 0x8363 || type === 0x8033 || type === 0x8034) return 2;
    if (type === 0x1406) return ({ 0x1908: 16, 0x1907: 12, 0x1909: 4, 0x190A: 4, 0x1906: 4 })[fmt] || 16;
    if (type === 0x8D61 || type === 0x140B) return ({ 0x1908: 8, 0x1907: 6 })[fmt] || 8;
    if (ifmt === 0x1909 || ifmt === 0x1906) return 1;
    if (ifmt === 0x190A) return 2;
    return 4; // RGBA, RGB (padded)
  }
  function srcSize(s) {
    if (!s) return [0, 0];
    return [s.videoWidth || s.naturalWidth || s.displayWidth || s.width || 0, s.videoHeight || s.naturalHeight || s.displayHeight || s.height || 0];
  }
  function levelBytes(w, h, b) { return w * h * b; }
  function texOf(gl, target) {
    const key = (target >= 0x8515 && target <= 0x851A) ? 0x8513 : target;
    return bind.tex[gl.__id + ':' + bind.unit + ':' + key];
  }
  function setLevel(t, face, level, w, h, b) {
    if (!t) return;
    t.levels[face + ':' + level] = { w, h, bytes: levelBytes(w, h, b) };
    if (level === 0 && face === 0) { t.w = w; t.h = h; t.b = b; }
    if (!t.stack) { t.stack = stack(); t.t = Math.round(performance.now()); }
  }
  function texBytes(t) { let s = 0; for (const k in t.levels) s += t.levels[k].bytes; return s; }
  function hook(proto) {
    const o = {};
    const wrap = (name, f) => { o[name] = proto[name]; if (!o[name]) return; proto[name] = function () { try { f.apply(this, arguments); } catch (e) {} return o[name].apply(this, arguments); }; };
    const wrapRet = (name, f) => { o[name] = proto[name]; if (!o[name]) return; proto[name] = function () { const r = o[name].apply(this, arguments); try { f.call(this, r, arguments); } catch (e) {} return r; }; };
    wrapRet('createTexture', function (r) { if (!this.__id) this.__id = ++st.ctxs; T.set(r, { levels: {}, ctx: this.__id, mips: false, fbo: false }); });
    wrap('deleteTexture', function (t) { T.delete(t); });
    wrap('activeTexture', function (u) { bind.unit = u; });
    wrap('bindTexture', function (target, t) { if (!this.__id) this.__id = ++st.ctxs; bind.tex[this.__id + ':' + bind.unit + ':' + target] = t ? T.get(t) : null; });
    wrap('texImage2D', function (target, level, ifmt) {
      const a = arguments; let w, h, fmt, type;
      if (a.length >= 8) { w = a[3]; h = a[4]; fmt = a[6]; type = a[7]; }
      else { [w, h] = srcSize(a[5]); fmt = a[3]; type = a[4]; }
      const face = (target >= 0x8515 && target <= 0x851A) ? target - 0x8515 : 0;
      setLevel(texOf(this, target), face, level, w, h, bpp(ifmt, fmt, type));
    });
    wrap('texStorage2D', function (target, levels, ifmt, w, h) {
      const t = texOf(this, target); const b = bpp(ifmt, 0, 0);
      for (let l = 0; l < levels; l++) setLevel(t, 0, l, Math.max(1, w >> l), Math.max(1, h >> l), b);
    });
    wrap('generateMipmap', function (target) {
      const t = texOf(this, target); if (!t || !t.w) return; t.mips = true;
      let w = t.w, h = t.h, l = 0; while (w > 1 || h > 1) { w = Math.max(1, w >> 1); h = Math.max(1, h >> 1); l++; setLevel(t, 0, l, w, h, t.b); }
    });
    wrap('framebufferTexture2D', function (target, att, tt, tex) { const t = T.get(tex); if (t) t.fbo = true; });
    wrapRet('createBuffer', function (r) { B.set(r, { bytes: 0, stack: null }); });
    wrap('deleteBuffer', function (b) { B.delete(b); });
    wrap('bindBuffer', function (target, b) { bind.buf[this.__id + ':' + target] = b ? B.get(b) : null; });
    wrap('bufferData', function (target, data) {
      const b = bind.buf[this.__id + ':' + target]; if (!b) return;
      b.bytes = typeof data === 'number' ? data : (data ? data.byteLength : 0);
      b.target = target; if (!b.stack) b.stack = stack();
    });
    wrapRet('createRenderbuffer', function (r) { R.set(r, { bytes: 0 }); });
    wrap('deleteRenderbuffer', function (r) { R.delete(r); });
    wrap('bindRenderbuffer', function (t, r) { bind.rb = r ? R.get(r) : null; });
    const rbs = function (t, ifmt, w, h) { if (bind.rb) { bind.rb.w = w; bind.rb.h = h; bind.rb.bytes = w * h * ({ 0x8D48: 1, 0x81A5: 2, 0x81A6: 3, 0x81A7: 4, 0x84F9: 4, 0x8D62: 2, 0x8056: 2, 0x8D44: 2, 0x8D46: 2 }[ifmt] || 4); bind.rb.fmt = ifmt; bind.rb.stack = stack(); } };
    wrap('renderbufferStorage', rbs);
    if (proto.renderbufferStorageMultisample) wrap('renderbufferStorageMultisample', function (t, s, ifmt, w, h) { rbs(t, ifmt, w, h); if (bind.rb) bind.rb.bytes *= s; });
    wrapRet('getContext_', function () {});
  }
  hook(WebGLRenderingContext.prototype);
  if (typeof WebGL2RenderingContext !== 'undefined') hook(WebGL2RenderingContext.prototype);
  const ctxs = [];
  const oc = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (type, attrs) { const c = oc.apply(this, arguments); if (c && /webgl/.test(type) && !ctxs.some(x => x.c === c)) ctxs.push({ c, canvas: this, attrs: c.getContextAttributes && c.getContextAttributes() }); return c; };
  const MB = (n) => Math.round(n / 1048.576) / 1000;
  window.__gl = {
    summary(n) {
      let tb = 0, tn = 0, bb = 0, bn = 0, rb = 0, rn = 0, fbo = 0, mipX = 0;
      const tex = [];
      for (const [k, t] of T) { const b = texBytes(t); if (!b) continue; tb += b; tn++; if (t.fbo) fbo += b; tex.push({ w: t.w, h: t.h, mb: MB(b), levels: Object.keys(t.levels).length, mips: t.mips, fbo: t.fbo, bpp: t.b, t: t.t, stack: t.stack }); }
      const buf = [];
      for (const [k, b] of B) { if (!b.bytes) continue; bb += b.bytes; bn++; buf.push({ mb: MB(b.bytes), target: b.target, stack: b.stack }); }
      for (const [k, r] of R) { if (!r.bytes) continue; rb += r.bytes; rn++; }
      let back = 0; const backs = [];
      for (const x of ctxs) { const a = x.attrs || {}; const px = x.canvas.width * x.canvas.height; const b = px * 4 * 2 + (a.depth ? px * 4 : 0); back += b; backs.push({ w: x.canvas.width, h: x.canvas.height, dpr: devicePixelRatio, depth: !!a.depth, stencil: !!a.stencil, aa: !!a.antialias, mb: MB(b) }); }
      const grp = {};
      for (const t of tex) { const fr = (t.stack || '').split(' < '); const key = (fr[0] === 'cbgal_TextureLoader_loadSync' ? 'AssetManager TextureLoader' : fr[0]) + ' | ' + (fr[0] === 'cbgal_TextureLoader_loadSync' ? '' : (fr[1] || '')); const g = grp[key] = grp[key] || { n: 0, mb: 0, sizes: {} }; g.n++; g.mb += t.mb; const sk = t.w + 'x' + t.h; g.sizes[sk] = (g.sizes[sk] || 0) + 1; }
      tex.sort((a, b) => b.mb - a.mb); buf.sort((a, b) => b.mb - a.mb);
      // size histogram
      const hist = {};
      for (const t of tex) { const k = t.w + 'x' + t.h; hist[k] = hist[k] || { n: 0, mb: 0 }; hist[k].n++; hist[k].mb = Math.round((hist[k].mb + t.mb) * 100) / 100; }
      return { texMB: MB(tb), texN: tn, texFboMB: MB(fbo), bufMB: MB(bb), bufN: bn, rbMB: MB(rb), rbN: rn, backbufferMB: MB(back), backs, topTex: tex.slice(0, n || 12), topBuf: buf.slice(0, 5), groups: Object.entries(grp).map(([k, g]) => [k, g.n, Math.round(g.mb * 10) / 10, Object.entries(g.sizes).sort((a, b) => b[1] - a[1]).slice(0, 4).map(x => x.join('x')).join(' ')]).sort((a, b) => b[2] - a[2]).slice(0, 14), hist: Object.entries(hist).sort((a, b) => b[1].mb - a[1].mb).slice(0, 15) };
    }
  };
})();
