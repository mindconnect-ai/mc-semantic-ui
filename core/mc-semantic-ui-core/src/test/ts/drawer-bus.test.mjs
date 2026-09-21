/**
 * A drawer the user changes tells the server through SuiEventBus: its
 * onStateChange fires with {state} filled in, and the X fires onClose too.
 * The real bus, a stub DOM (support/mini-dom.mjs), a recording fetcher.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

describe("drawer triggers", () => {
    let requests, docListeners, drawer;

    before(async () => {
        installControls();
        requests = [];
        docListeners = {};
        const trigger = (url, method) => JSON.stringify({ url, method, behavior: "APPLY_RESPONSE" });
        const handle = new El("button", { class: "sui-drawer-handle", "data-sui-drawer": "open" });
        const close = new El("button", { class: "sui-drawer-btn", "data-sui-drawer": "close" });
        const minimize = new El("button", { class: "sui-drawer-btn", "data-sui-drawer": "minimize" });
        const panel = new El("div", { class: "sui-drawer-panel" }, [new El("div", { class: "sui-drawer-header" }, [minimize, close]), new El("div", { class: "sui-drawer-body" })]);
        drawer = new El("div", { class: "sui-drawer sui-drawer--bottom sui-drawer--minimized sui-drawer--viewport sui-drawer--overlay", id: "chat",
            "data-sui": "drawer", "data-state": "minimized",
            "data-state-trigger": trigger("/chat/1/state/{state}", "POST"), "data-close-trigger": trigger("/chat/1", "DELETE") }, [handle, panel]);
        for (const b of [handle, close, minimize, panel]) b.focus = () => { };
        const root = new El("div", { id: "root" }, [drawer]);
        const body = new El("body", {}, [root]);
        const byId = (id, from = body) => from.id === id ? from : from.children.map(c => byId(id, c)).find(Boolean) ?? null;
        globalThis.window = { location: { pathname: "/", search: "", href: "http://localhost/" }, addEventListener() { }, innerWidth: 1000, innerHeight: 800,
            history: { pushState() { }, replaceState() { } } };
        globalThis.document = { body, getElementById: (id) => byId(id), createElement: (t) => new El(t),
            querySelectorAll: (s) => body.querySelectorAll(s), querySelector: (s) => body.querySelector(s),
            addEventListener: (t, fn) => { (docListeners[t] ??= []).push(fn); }, removeEventListener() { } };
        globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible" });
        const { SuiEventBus } = await import(`${DIST}/eventbus.js`);
        const renderer = { render: () => "", seedModels() { }, applyPatch() { }, showLoading() { }, hideLoading() { }, mount() { } };
        const bus = new SuiEventBus(renderer, root);
        bus.setFetcher(async (url, init) => {
            requests.push(`${init.method} ${url}`);
            return { ok: true, status: 200, headers: { get: () => "application/json" }, json: async () => ({ patches: [] }), text: async () => "{}" };
        });
        // The bus wires the drawers in its first enhance pass.
        await new Promise(r => setTimeout(r, 20));
        const click = async (target) => {
            const e = { type: "click", target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
            for (const fn of docListeners.click ?? []) await fn(e);
            await new Promise(r => setTimeout(r, 10));
        };
        await click(handle);
        await click(minimize);
        await click(close);
    });

    test("each change fires onStateChange with the new state in the URL", () => {
        assert.deepEqual(requests.filter(r => r.startsWith("POST")),
            ["POST /chat/1/state/OPEN", "POST /chat/1/state/MINIMIZED", "POST /chat/1/state/CLOSED"]);
    });

    test("the X fires onClose as well, once", () => {
        assert.deepEqual(requests.filter(r => r.startsWith("DELETE")), ["DELETE /chat/1"]);
        assert.equal(drawer.getAttribute("data-state"), "closed");
    });
});
