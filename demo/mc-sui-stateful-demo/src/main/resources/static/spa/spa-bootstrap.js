/*
 * SPA takeover for the stateful demo. The page arrives as server-rendered
 * HTML inside #sui-root; this attaches the core SuiEventBus, so from now on
 * every listener's trigger is a fetch with Accept: application/json and the
 * UiPatch that comes back is applied in place. Nothing here knows about
 * "stateful" — the wire format is the core's.
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

    // "Switch to SSR" has to be a real reload: drop the cookie, load again.
    bus.registerClientHandler("switch-to-ssr", () => {
        document.cookie = "sui-mode=; max-age=0; path=/";
        window.location.reload();
        return null;
    });

    console.info("SUI SPA takeover active on", window.location.pathname);
}
