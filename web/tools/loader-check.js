/* Loader check for webtest's js step (no semicolons, since webtest splits steps on them): records
   every "N / M MB" the loading screen shows. Read the result later with
   js JSON.stringify(window.__p): max over 1 means the count passed its total. */
((s) => (window.__p = {max: 0, last: '', n: 0}, new MutationObserver(() => ((m) => m && +m[2] > 0 && (window.__p.n++, window.__p.last = s.textContent, window.__p.max = Math.max(window.__p.max, +m[1] / +m[2])))(/([0-9]+) \/ ([0-9]+) MB/.exec(s.textContent))).observe(s, {childList: true, characterData: true, subtree: true}), 'watching'))(document.getElementById('status'))
