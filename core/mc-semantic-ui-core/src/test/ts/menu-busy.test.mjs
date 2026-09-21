/**
 * A menu entry shows that it is busy — the way a button does.
 *
 * Two routes lead there: the server sets `loading` on the item, and the event
 * bus marks a clicked `[data-href]` entry for as long as its page takes to
 * arrive. The bus runs against a stand-in DOM here: a root that hands out its
 * click listener, one fake anchor, and a navigation handler the test resolves
 * by hand. Runs against the compiled output in target/ts-dist.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

// ── Rendering ───────────────────────────────────────────────────────────────

describe("renderMenuItem with `loading`", () => {
    let renderMenuItem;
    const r = { render: () => "" };

    before(async () => {
        ({ renderMenuItem } = await import(`${DIST}/renderers/menu.js`));
    });

    test("a group's fly-out is headed by the group's own name", () => {
        const html = renderMenuItem({ type: "menu-item", id: "g", label: "beisdog · Outlook", icon: "mail",
            children: [{ type: "menu-item", id: "c", label: "Inbox", href: "/inbox" }] },
            { render: () => "<li>Inbox</li>" });
        assert.match(html, /<ul class="sui-menu-sublist" role="menu"><li class="sui-menu-flyout-title" role="presentation" aria-hidden="true">beisdog · Outlook<\/li><li>Inbox<\/li>/);
    });

    test("a loading leaf carries is-loading and aria-busy", () => {
        const html = renderMenuItem({ type: "menu-item", id: "m", label: "Reports", icon: "chart", href: "/reports", loading: true }, r);
        assert.match(html, /<a class="sui-menu-link is-loading" aria-busy="true"/);
        assert.match(html, /data-href="\/reports"/);
    });

    test("an ordinary leaf carries neither", () => {
        const html = renderMenuItem({ type: "menu-item", id: "m", label: "Reports", href: "/reports" }, r);
        assert.doesNotMatch(html, /is-loading|aria-busy/);
    });
});

// ── The bus marks a clicked link ────────────────────────────────────────────

function element(id = "") {
    return { id, className: "", innerHTML: "", addEventListener() {}, appendChild() {},
             querySelector() { return null; }, querySelectorAll() { return []; },
             contains() { return false; } };
}

/** One menu link: `<a data-href="/slow">` as far as the bus can tell. */
function link(href) {
    const classes = new Set();
    const attrs = new Map();
    const el = {
        dataset: { href },
        classList: {
            add: (c) => classes.add(c), remove: (c) => classes.delete(c),
            contains: (c) => classes.has(c),
        },
        setAttribute: (k, v) => attrs.set(k, v),
        removeAttribute: (k) => attrs.delete(k),
        getAttribute: (k) => attrs.get(k) ?? null,
        closest: (sel) => sel === "[data-trigger], [data-href]" ? el : null,
    };
    el.busy = () => classes.has("is-loading") && attrs.get("aria-busy") === "true";
    el.marked = () => classes.has("is-loading") || attrs.has("aria-busy");
    return el;
}

describe("SuiEventBus marks a [data-href] link busy while it navigates", () => {
    let SuiEventBus, listeners, root, renderer;

    before(async () => {
        globalThis.window = { location: { pathname: "/page", search: "" }, addEventListener() {} };
        globalThis.document = {
            getElementById: () => null, createElement: () => element(), body: element("body"),
            querySelectorAll: () => [],
        };
        ({ SuiEventBus } = await import(path.join(DIST, "eventbus.js")));
    });

    beforeEach(() => {
        listeners = {};
        root = { ...element("root"), addEventListener: (type, fn) => { listeners[type] = fn; }, contains: () => true };
        renderer = { mount() {}, applyPatch() {}, render() { return ""; }, seedModels() {}, showLoading() {}, hideLoading() {} };
    });

    /** A navigation that finishes when the test says so. */
    function slowNavigation(bus) {
        let finish;
        const pending = new Promise(r => { finish = r; });
        const visited = [];
        bus.setOnNavigate(async (href) => { visited.push(href); await pending; });
        return { finish, visited };
    }

    const click = (target) => listeners.click({ target, preventDefault() {} });

    test("busy from the click until the page has arrived", async () => {
        const bus = new SuiEventBus(renderer, root).setHistoryEnabled(false);
        const nav = slowNavigation(bus);
        const a = link("/slow");
        const done = click(a);
        assert.equal(a.busy(), true, "marked while the navigation is pending");
        assert.deepEqual(nav.visited, ["/slow"]);
        nav.finish();
        await done;
        assert.equal(a.marked(), false, "cleared once it has arrived");
    });

    test("cleared even when the navigation fails", async () => {
        const bus = new SuiEventBus(renderer, root).setHistoryEnabled(false);
        bus.setOnNavigate(async () => { throw new Error("boom"); });
        const a = link("/broken");
        await click(a).catch(() => {});
        assert.equal(a.marked(), false);
    });

    test("the manual loading policy leaves the link alone", async () => {
        const bus = new SuiEventBus(renderer, root).setHistoryEnabled(false).setLoadingPolicy("manual");
        const nav = slowNavigation(bus);
        const a = link("/slow");
        const done = click(a);
        assert.equal(a.marked(), false);
        nav.finish();
        await done;
    });
});
