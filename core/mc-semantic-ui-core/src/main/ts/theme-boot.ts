/*
 * Puts the remembered theme on before the first paint.
 *
 *   <script src="/sui/theme-boot.js" data-default-theme="amethyst"></script>
 *
 * A classic, blocking script in <head> on purpose — no import, no export, so
 * the compiler emits it as a script and not as a module. A module is deferred
 * and runs after the page is drawn, and a theme applied that late flashes the
 * previous one first.
 *
 * Order of precedence: `?theme=` in the address (and it is remembered, so a
 * link can hand someone a look), then the choice in localStorage, then the
 * script tag's `data-default-theme`, then none. It deliberately knows no theme
 * names: whatever it finds becomes a class, theme.ts owns the list of what is
 * real, and a stale or invented name matches no stylesheet and renders as the
 * framework default. The storage key is theme.ts's STORAGE_KEY.
 */
(function () {
    const STORAGE_KEY = "sui-theme";
    const script = document.currentScript as HTMLScriptElement | null;
    const fallback = script?.dataset.defaultTheme ?? "default";

    let wanted: string | null = null;
    try {
        wanted = new URL(window.location.href).searchParams.get("theme");
    } catch {
        // An address URL cannot parse is not worth failing the page over.
    }
    if (wanted) {
        try { localStorage.setItem(STORAGE_KEY, wanted); } catch { /* private mode */ }
    }

    let active = wanted;
    if (!active) {
        try { active = localStorage.getItem(STORAGE_KEY); } catch { active = null; }
    }
    if (!active) active = fallback;

    // The name becomes a class; anything that could not be one is ignored.
    if (/^[a-z][a-z0-9-]*$/.test(active) && active !== "default") {
        document.documentElement.classList.add("sui-theme-" + active);
    }
})();
