// The browser half of the demo.
//
// Everything semantic-ui comes from the npm package — no copied files, no
// script tags pointing at a server path. Vite resolves the bare specifiers,
// bundles the renderer and emits the stylesheet and the icon sprite as build
// assets, exactly as it would for any other dependency.

import { SuiRenderer, installDefaultHandlers } from "@mindconnect-ai/mc-semantic-ui-core";
import { SuiEventBus } from "@mindconnect-ai/mc-semantic-ui-core/eventbus";
import "@mindconnect-ai/mc-semantic-ui-core/sui.css";
import "./demo.css";

const host = document.getElementById("sui-root");
if (!host) throw new Error("demo shell is missing its #sui-root element");

const renderer = installDefaultHandlers(new SuiRenderer(host));

// Constructing the bus seeds the renderer's model index from the
// <script id="sui-model"> the server parked in the page, which is what lets the
// first MERGE patch work on markup this renderer never drew.
const bus = new SuiEventBus(renderer, host);

// The server already rendered this page, so take it over rather than fetch it
// again — re-fetching would throw away the very thing SSR bought. Only when the
// root came back empty (a shell served without SSR) is there anything to load,
// and then the URL says what: no mapping from pages to endpoints, just the one
// URL asked a second time with a different Accept.
if (host.childElementCount === 0) {
    const HOME = "/products";
    const here = location.pathname === "/" ? HOME : location.pathname + location.search;
    void bus.navigate(here);
}
