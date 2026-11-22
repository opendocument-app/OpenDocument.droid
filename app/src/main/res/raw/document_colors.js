(function () {
    // Read authored declarations, not computed colours: night mode changes the latter.
    // Only the rendered page/sheet is available; do not force deferred content to load.
    if (!document.body) return [];
    const colors = new Set();
    function add(value) {
        const match = /^rgb\(\s*(\d+),\s*(\d+),\s*(\d+)\s*\)$/.exec(value);
        if (match && colors.size < 32) {
            colors.add("#" + match.slice(1).map(v => Number(v).toString(16).padStart(2, "0")).join(""));
        } else if (/^#[0-9a-f]{6}$/i.test(value) && colors.size < 32) {
            colors.add(value.toLowerCase());
        }
    }
    function read(style) {
        add(style.color);
        add(style.backgroundColor);
    }
    for (const element of document.body.querySelectorAll("[style]")) {
        read(element.style);
        if (colors.size >= 32) break;
    }
    // Core interns authored inline styles as .c0.c0.c0 etc. Restrict this scan to those
    // rules so search highlights, selection chrome and theme CSS never enter the palette.
    for (const sheet of document.styleSheets) {
        try {
            for (const rule of sheet.cssRules) {
                if (/^\.([a-z0-9]+)\.\1\.\1$/.test(rule.selectorText || "")) read(rule.style);
                if (colors.size >= 32) break;
            }
        } catch (_) {
            // A stylesheet outside the document's origin is not readable.
        }
        if (colors.size >= 32) break;
    }
    return Array.from(colors);
})()
