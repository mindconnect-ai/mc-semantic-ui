/*
 * SPA bootstrap. The page arrives as server-rendered HTML (wrapped in a
 * #sui-root div by the UiPageHtmlMessageConverter); this script attaches the
 * SuiEventBus so subsequent navigation, form posts and — crucially for this
 * demo — drag-and-drop uploads go through the bus instead of a full reload.
 */
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";
// Every extension and stylesheet the server's asset registry knows about —
// here the calendar and the kanban from the classpath, and the demo's own
// stylesheets from its SuiAssetContribution (DemoAssets.java). No list to keep.
import { installAll } from "/sui/assets.js";

const root = document.getElementById("sui-root");
if (!root) {
    console.error("SPA bootstrap: no #sui-root element on the page");
} else {
    const renderer = installDefaultHandlers(new SuiRenderer(root));
    const bus = new SuiEventBus(renderer, root);
    // Before anything is re-rendered: the extensions register their node
    // types, and a failing one is logged without stopping the rest.
    const report = await installAll(renderer, bus);
    console.info("SUI assets installed:", report.map(r => `${r.id}${r.ok ? "" : " (failed)"}`).join(", "));

    // Ask the server for JSON, not HTML, and keep the session cookie.
    bus.setFetcher((input, init = {}) => {
        const headers = new Headers(init.headers ?? {});
        headers.set("Accept", "application/json");
        return fetch(input, { ...init, headers, credentials: "same-origin" });
    });

    console.info("SUI SPA takeover active on", window.location.pathname);
}
