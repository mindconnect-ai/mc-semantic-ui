/**
 * Minimal message catalog for the runtime's OWN chrome strings — the stream
 * status toast, the default error toasts, and whatever client-side copy the
 * bus grows next. Deliberately tiny: a flat key→string map with `{param}`
 * interpolation, no plurals, no formatting — server-rendered content is the
 * host application's business and never passes through here.
 *
 * <p>English is built in and is the per-key fallback: an incomplete bundle
 * degrades gracefully instead of showing blank strings. The current German
 * copy ships as the first registered bundle.
 *
 * <p>The HOST picks the locale, with two levels of convenience:
 * <ul>
 *   <li>Explicit: {@code suiI18n.use("de")} at bootstrap, or
 *       {@code suiI18n.set({...})} / {@code suiI18n.register("fr", {...})}
 *       for own bundles — the i18n sibling of
 *       {@code SuiEventBus.setOnError}.</li>
 *   <li>Automatic: at module load the active locale follows
 *       {@code <html lang="…">} when a bundle for it is registered — so a
 *       server-driven app that sets the page language gets matching chrome
 *       for free.</li>
 * </ul>
 */

const DEFAULTS: Record<string, string> = {
    "error.network.title": "Connection error",
    "error.network.message": "The backend is unreachable. Please check the connection and try again.",
    "error.http.title": "Error",
    "error.http.message": "The request failed (HTTP {status}).",
};

const BUNDLES: Record<string, Record<string, string>> = {
    de: {
        "error.network.title": "Verbindungsfehler",
        "error.network.message": "Das Backend ist nicht erreichbar. Bitte prüfen Sie die Verbindung und versuchen Sie es erneut.",
        "error.http.title": "Fehler",
        "error.http.message": "Die Anfrage ist fehlgeschlagen (HTTP {status}).",
    },
};

let overrides: Record<string, string> = {};
let activeLocale = "en";

/**
 * Resolves a chrome message: active overrides first, English default second
 * — never blank. `{name}` placeholders are replaced from {@code params};
 * unknown placeholders stay verbatim so a bad call remains debuggable.
 */
export function t(key: string, params?: Record<string, string>): string {
    let text = overrides[key] ?? DEFAULTS[key] ?? key;
    if (params) {
        for (const [name, value] of Object.entries(params)) {
            text = text.split(`{${name}}`).join(value);
        }
    }
    return text;
}

export const suiI18n = {
    /** Merges single overrides on top of the active locale — app-specific wording without a full bundle. */
    set(messages: Record<string, string>): void {
        overrides = { ...overrides, ...messages };
    },

    /** Registers (or replaces) a locale bundle; keys missing from it fall back to English per key. */
    register(locale: string, bundle: Record<string, string>): void {
        BUNDLES[normalize(locale)] = { ...bundle };
    },

    /**
     * Activates a locale. Unknown locales fall back to English (returns
     * false); region subtags are ignored ({@code de-AT} → {@code de}).
     * Replaces earlier {@link #set} overrides — call {@code set} after
     * {@code use} for per-app tweaks.
     */
    use(locale: string | null | undefined): boolean {
        const key = normalize(locale ?? "");
        if (key === "en" || key === "") {
            activeLocale = "en";
            overrides = {};
            return key === "en";
        }
        const bundle = BUNDLES[key];
        activeLocale = bundle ? key : "en";
        overrides = bundle ? { ...bundle } : {};
        return !!bundle;
    },

    /** The locale currently in effect ({@code "en"} when falling back). */
    locale(): string {
        return activeLocale;
    },
};

function normalize(locale: string): string {
    return locale.trim().toLowerCase().split(/[-_]/)[0] ?? "";
}

// Follow the page language when the host declared one and a bundle exists —
// a server-driven app that renders <html lang="de"> gets matching chrome
// without any bootstrap call. Guarded: this module may load headless.
if (typeof document !== "undefined" && document.documentElement?.lang) {
    suiI18n.use(document.documentElement.lang);
}
