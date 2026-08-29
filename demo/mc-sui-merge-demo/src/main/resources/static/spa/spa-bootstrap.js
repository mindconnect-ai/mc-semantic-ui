/*
 * SPA bootstrap.
 *
 * The page arrives as server-rendered HTML wrapped in a #sui-root div. This
 * attaches the SuiEventBus on top, which is what turns the buttons into
 * fetches and applies the patches that come back.
 *
 * The bus also seeds itself from the <script type="application/json"
 * id="sui-model"> blob the converter writes at the end of the body. Without
 * that, a MERGE on this page would have nothing to merge into: the client did
 * not build this tree and knows only what it looks like.
 */
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const root = document.getElementById("sui-root");
if (!root) {
    console.error("SPA bootstrap: no #sui-root element on the page");
} else {
    const renderer = installDefaultHandlers(new SuiRenderer(root));
    const bus = new SuiEventBus(renderer, root);

    bus.setFetcher((input, init = {}) => {
        const headers = new Headers(init.headers ?? {});
        headers.set("Accept", "application/json");
        return fetch(input, { ...init, headers, credentials: "same-origin" });
    });

    console.info("MERGE demo: SPA takeover active");
}
