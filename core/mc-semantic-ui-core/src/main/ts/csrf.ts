/*
 * CSRF tokens on the requests that need them, without the app wiring anything.
 *
 * A backend that protects state-changing requests against cross-site forgery
 * hands the page a token and expects it back in a header. Two conventions
 * cover practically every such backend, and the page tells which one is in
 * use simply by carrying it:
 *
 *   1. A cookie, XSRF-TOKEN, readable by script, echoed as the X-XSRF-TOKEN
 *      header. Spring Security's CookieCsrfTokenRepository, Angular, Laravel
 *      and Django-with-settings all speak it.
 *   2. Meta tags in the document: <meta name="_csrf" content="…"> and
 *      <meta name="_csrf_header" content="X-CSRF-TOKEN">. Spring Security's
 *      documented way for server-rendered pages, and what
 *      UiPageHtmlMessageConverter writes when the request carries a token.
 *
 * The meta tags win when both are there: they were written for this page, the
 * cookie may be left over from another. With neither, nothing is added — a
 * backend without CSRF protection sees exactly the request it always did.
 *
 * Only unsafe methods get the token (a GET must not change anything, and a
 * token in every GET ends up in logs and caches), and only requests to this
 * page's own origin: a token sent to someone else's server is a token given
 * away. A header the caller set itself is left alone.
 */

export interface CsrfOptions {
    /** Cookie the token is read from. Defaults to `XSRF-TOKEN`. */
    cookieName?: string;
    /** Header the cookie's token is sent in. Defaults to `X-XSRF-TOKEN`. */
    headerName?: string;
}

/** A token and the header it belongs in. */
export interface CsrfToken {
    headerName: string;
    token: string;
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

/**
 * The token for this page, or `null` when the page carries none. Meta tags
 * first, then the cookie.
 */
export function findCsrfToken(options: CsrfOptions = {}): CsrfToken | null {
    if (typeof document === "undefined") return null;

    const meta = (name: string) =>
        document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.content || null;
    const metaToken = meta("_csrf");
    if (metaToken) {
        return { headerName: meta("_csrf_header") || "X-CSRF-TOKEN", token: metaToken };
    }

    const cookieName = options.cookieName ?? "XSRF-TOKEN";
    let cookies = "";
    try {
        cookies = document.cookie;
    } catch {
        return null;   // a sandboxed frame may not read cookies at all
    }
    for (const part of cookies.split(";")) {
        const eq = part.indexOf("=");
        if (eq < 0 || part.slice(0, eq).trim() !== cookieName) continue;
        const raw = part.slice(eq + 1).trim();
        if (!raw) return null;
        let token = raw;
        try {
            token = decodeURIComponent(raw);
        } catch {
            // Not percent-encoded after all; the raw value is the token.
        }
        return { headerName: options.headerName ?? "X-XSRF-TOKEN", token };
    }
    return null;
}

/** Whether a request with this method and target should carry the token. */
export function needsCsrfToken(input: RequestInfo | URL, method: string | undefined): boolean {
    const verb = (method ?? (input instanceof Request ? input.method : "GET")).toUpperCase();
    if (SAFE_METHODS.has(verb)) return false;
    if (typeof location === "undefined") return false;
    const href = input instanceof Request ? input.url : String(input);
    try {
        return new URL(href, location.href).origin === location.origin;
    } catch {
        return false;
    }
}

/**
 * Wraps a fetch so unsafe same-origin requests carry the page's CSRF token.
 * The token is looked up per request, so one that rotates (after a login, for
 * instance) is picked up without reloading. Pass `false` as options to get
 * the fetch back unchanged.
 */
export function withCsrf(fetcher: typeof fetch, options: CsrfOptions | false = {}): typeof fetch {
    if (options === false) return fetcher;
    return (input, init) => {
        if (!needsCsrfToken(input, init?.method)) return fetcher(input, init);
        const found = findCsrfToken(options);
        if (!found) return fetcher(input, init);
        // The Headers constructor, not object spread: a Headers instance
        // spreads to {} and would drop Content-Type and Accept.
        const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
        if (!headers.has(found.headerName)) headers.set(found.headerName, found.token);
        return fetcher(input, { ...init, headers });
    };
}
