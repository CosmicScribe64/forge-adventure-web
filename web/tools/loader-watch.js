// Init script for web/tools/webtest.py (--init-script): records the highest width the loading
// screen's bar (#fill in web/html/index.html) has had, as window.__fillMax in percent. The loading
// screen is removed from the page shortly after the game draws its first frame, and the main
// thread is often blocked for seconds, so polling could miss the last value; a MutationObserver
// sees every change.
(() => {
    window.__fillMax = 0;
    new MutationObserver(records => {
        for (const r of records) {
            if (r.target.id === "fill") {
                const width = parseFloat(r.target.style.width) || 0;
                if (width > window.__fillMax) window.__fillMax = width;
            }
        }
    }).observe(document, { subtree: true, attributes: true, attributeFilter: ["style"] });
})();
