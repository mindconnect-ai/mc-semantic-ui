/*
 * SPA bootstrap for the SSR-first / SPA-takeover mode.
 *
 * The page arrives from the server as fully-rendered HTML — the same
 * markup the pure-SSR mode produces, wrapped in a #sui-root div by the
 * UiPageHtmlMessageConverter. This script attaches the SuiEventBus to
 * that root so subsequent clicks and form submits go through fetch
 * (with Accept: application/json) instead of a full-page navigation.
 * No DOM rebuild on takeover: the SSR HTML stays put; only later
 * updates are SPA-rendered.
 *
 * URL handling
 * ------------
 * URLs are identical between SSR and SPA mode (/admin/products in both),
 * the `sui-mode` cookie alone decides whether the server injects this
 * bootstrap. Browser-side we just keep using the URLs as they are; the
 * EventBus's pushState path lands them back into the address bar.
 *
 * Reload (or arriving via a bookmark) re-runs SSR + this bootstrap, so
 * the experience is seamless.
 */
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const root = document.getElementById("sui-root");
if (!root) {
    console.error("SPA bootstrap: no #sui-root element on the page");
} else {
    const renderer = installDefaultHandlers(new SuiRenderer(root));
    const bus = new SuiEventBus(renderer, root);

    // Ask the server for JSON, not HTML — without this the message
    // converter would happily return more SSR HTML and the JSON path
    // would never trigger.
    bus.setFetcher((input, init = {}) => {
        const headers = new Headers(init.headers ?? {});
        headers.set("Accept", "application/json");
        return fetch(input, { ...init, headers, credentials: "same-origin" });
    });

    console.info("SUI SPA takeover active on", window.location.pathname);
}
