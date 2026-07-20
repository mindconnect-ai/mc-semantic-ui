/*
 * SPA bootstrap. The page arrives as server-rendered HTML (wrapped in a
 * #sui-root div by the UiPageHtmlMessageConverter); this script attaches the
 * SuiEventBus so subsequent navigation, form posts and — crucially for this
 * demo — drag-and-drop uploads go through the bus instead of a full reload.
 */
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const root = document.getElementById("sui-root");
if (!root) {
    console.error("SPA bootstrap: no #sui-root element on the page");
} else {
    const renderer = installDefaultHandlers(new SuiRenderer(root));
    const bus = new SuiEventBus(renderer, root);

    // Ask the server for JSON, not HTML, and keep the session cookie.
    bus.setFetcher((input, init = {}) => {
        const headers = new Headers(init.headers ?? {});
        headers.set("Accept", "application/json");
        return fetch(input, { ...init, headers, credentials: "same-origin" });
    });

    console.info("SUI SPA takeover active on", window.location.pathname);
}
