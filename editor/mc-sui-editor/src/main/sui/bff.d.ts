/**
 * BFF (Backend-for-Frontend) helpers.
 *
 * <p>Optional sub-module — only import this if your backend uses the BFF
 * OIDC pattern (Spring Security {@code oauth2Login}, server-held session,
 * HttpOnly JSESSIONID cookie, CSRF token mirrored into the {@code XSRF-TOKEN}
 * cookie). Hosts without server-side sessions don't load this file at all.
 *
 * <p>The helpers are deliberately framework-agnostic on the JS side: they
 * read browser cookies, build {@link RequestInit} headers, and bounce the
 * window to a configurable login URL. They don't import {@link SuiEventBus}
 * or {@link SuiRenderer} — wiring is done in the host {@code app.js}:
 *
 * <pre>
 *   import { bffFetch, redirectToLogin } from "/sui/bff.js";
 *   const bus = new SuiEventBus(renderer, main)
 *       .setFetcher(bffFetch)
 *       .setOnUnauthenticated(() => redirectToLogin("/oauth2/authorization/keycloak"));
 * </pre>
 *
 * <p>There is no client-side redirect-loop guard. A loop only happens when
 * the OAuth setup is structurally broken (Keycloak issues a token Spring
 * doesn't accept, or the post-login redirect lands on a URL that 401s on
 * its own). Both are server-side bugs that surface clearly in Spring's
 * logs; the right place to handle them is the Spring
 * {@code AuthenticationFailureHandler}, not browser sessionStorage.
 */
/**
 * Reads the Spring-set XSRF-TOKEN cookie. Returns {@code null} when no
 * session has been established yet (first page load before login) so
 * callers can decide whether to send the header at all.
 */
export declare function csrfToken(): string | null;
/** {@code X-XSRF-TOKEN} header object, or empty {@code {}} when no token exists. */
export declare function csrfHeader(): Record<string, string>;
/**
 * Wraps {@code fetch} so it always sends the session cookie and, on
 * mutating verbs, the CSRF token Spring expects. Drop-in replacement for
 * the global {@code fetch} — feed it to {@link SuiEventBus#setFetcher}.
 */
export declare function bffFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;
/**
 * Bounces the browser to the login flow at {@code loginUrl}, encoding the
 * current path so the OIDC return-trip lands the user back where they
 * were.
 */
export declare function redirectToLogin(loginUrl: string): void;
/**
 * Builds and submits a logout {@code POST} form with the CSRF token, then
 * lets Spring Security do the rest (server-side session invalidation +
 * Keycloak RP-initiated logout + redirect to the configured post-logout
 * URI). Async-friendly: returns a never-resolving promise because the page
 * is about to navigate away.
 */
export declare function submitLogoutForm(logoutUrl?: string): Promise<never>;
/** User profile shape returned by the typical {@code GET /me} endpoint. */
export interface BffUser {
    name?: string;
    email?: string;
    username?: string;
    [key: string]: unknown;
}
/**
 * Loads the current user from a {@code /me} endpoint. Returns {@code null}
 * when the request 401s or fails — the caller (typically the header
 * renderer) treats {@code null} as "anonymous, hide the user widget".
 *
 * @param meUrl Defaults to {@code "/me"} — override if your BFF exposes
 *              the user endpoint under a different path.
 */
export declare function loadUser(meUrl?: string): Promise<BffUser | null>;
