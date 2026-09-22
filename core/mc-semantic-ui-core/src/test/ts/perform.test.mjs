/**
 * bus.perform(): from the outside, what a click does from the inside.
 *
 * The real SuiEventBus over the stub DOM (support/mini-dom.mjs) and a
 * recording fetcher: the same screen is once clicked and once performed, and
 * the two requests are compared. Also what perform refuses to do, and why.
 */
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

installControls();
const { SuiEventBus } = await import(`${DIST}/eventbus.js`);

/** mini-dom's input classes, which installControls put on globalThis. */
const Input = globalThis.HTMLInputElement;

const trigger = (url, method) => JSON.stringify({ url, method, payload: null, behavior: "APPLY_RESPONSE" });

/** The screen every test here works on, freshly built. */
function screen() {
    const input = new Input("input", { id: "q__input", name: "q", type: "text", value: "", "data-sui-type": "TEXT" });
    const field = new El("div", { class: "sui-field", id: "q", "data-field": "q" }, [input]);

    const editor = new El("div", { class: "sui-richtext-editor", id: "body__input" });
    const hidden = new Input("input", { type: "hidden", name: "body", "data-sui-type": "RICHTEXT" });
    const richtext = new El("div", { class: "sui-richtext", "data-sui-richtext": "" }, [
        new El("div", { class: "sui-richtext-toolbar" }), editor, hidden,
    ]);
    const body = new El("div", { class: "sui-field", id: "body" }, [richtext]);

    const search = new El("button", {
        id: "search", "data-action": "search", "data-trigger": trigger("/search", "GET"),
    });
    const remove = new El("button", {
        id: "delete-picked", "data-action": "delete-picked", "data-confirm": "Delete 3 messages?",
        "data-trigger": trigger("/mail/delete", "POST"),
    });
    const off = new El("button", {
        id: "off", "data-action": "off", disabled: "", title: "pick a message first",
        "data-trigger": trigger("/nothing", "POST"),
    });
    const dead = new El("button", { id: "dead", "data-action": "dead" });
    const footer = new El("div", { class: "sui-form-footer" }, [search, remove, off, dead]);

    const form = new El("form", { id: "search-form", "data-sui": "form" }, [field, body, footer]);
    const root = new El("div", { id: "root" }, [form]);
    const all = new Map();
    const index = (el) => { if (el.id) all.set(el.id, el); el.children.forEach(index); };
    index(root);

    const requests = [];
    /** What window.confirm was asked, and what it answers. */
    const confirm = { asked: [], answer: false };
    globalThis.window = {
        location: { pathname: "/", search: "", href: "http://localhost/" },
        addEventListener() { }, innerWidth: 1000, innerHeight: 800,
        history: { pushState() { }, replaceState() { } },
        confirm: (message) => { confirm.asked.push(message); return confirm.answer; },
        getComputedStyle: () => ({ position: "static" }),
    };
    globalThis.document = {
        body: root,
        getElementById: (id) => all.get(id) ?? null,
        createElement: (t) => new El(t),
        querySelectorAll: (s) => root.querySelectorAll(s),
        querySelector: (s) => root.querySelector(s),
        addEventListener() { }, removeEventListener() { }, dispatchEvent() { return true; },
    };
    globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
    globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible", position: "static" });

    const renderer = {
        render: () => "", seedModels() { }, applyPatch() { }, mount() { },
        showLoading() { }, hideLoading() { }, setInputReader() { }, tree: () => null,
    };
    const bus = new SuiEventBus(renderer, root);
    bus.setFetcher(async (url, init) => {
        requests.push({ url, method: init?.method ?? "GET", body: init?.body ?? null });
        return {
            ok: true, status: 200, headers: { get: () => "application/json" },
            json: async () => ({ patches: [] }), text: async () => "{}",
        };
    });
    return { bus, root, input, editor, hidden, search, requests, confirm, all };
}

const settle = () => new Promise(r => setTimeout(r, 10));

describe("perform does what a click does", () => {
    test("the same request, down to the payload", async () => {
        // Once by hand: type into the field, click the button.
        const clicked = screen();
        clicked.input.value = "rechnung";
        clicked.search.dispatchEvent(new Event("click", { bubbles: true }));
        await settle();

        // Once from the outside.
        const performed = screen();
        const result = await performed.bus.perform({ fields: { q: "rechnung" }, action: "search" });
        await settle();

        assert.deepEqual(result.triggered, true);
        assert.deepEqual(result.fields, ["q"]);
        assert.equal(clicked.requests.length, 1);
        assert.deepEqual(performed.requests, clicked.requests);
        assert.match(performed.requests[0].url, /\/search\?q=rechnung$/);
    });

    test("a field that is typed into is the field the form sends", async () => {
        const s = screen();
        await s.bus.perform({ fields: { q: "hallo" } });
        assert.equal(s.input.value, "hallo");
    });

    test("rich text is set through the editor, so the hidden input follows", async () => {
        const s = screen();
        const result = await s.bus.perform({ fields: { body: "<p>Guten Tag</p>" } });
        assert.equal(result.ok, true);
        assert.equal(s.editor.innerHTML, "<p>Guten Tag</p>");
        // wireRichText's own listener copied it into what the form submits.
        assert.equal(s.hidden.value, "<p>Guten Tag</p>");
    });

    test("fields alone change the screen and fire nothing", async () => {
        const s = screen();
        const result = await s.bus.perform({ fields: { q: "rechnung" } });
        await settle();
        assert.deepEqual(result, { ok: true, triggered: false, busId: "root", fields: ["q"] });
        assert.deepEqual(s.requests, []);
    });
});

describe("perform and the confirm question", () => {
    test("without confirmed it asks, and a no fires nothing", async () => {
        const s = screen();
        s.confirm.answer = false;
        const result = await s.bus.perform({ action: "delete-picked" });
        await settle();
        assert.deepEqual(s.confirm.asked, ["Delete 3 messages?"]);
        assert.equal(result.reason, "cancelled");
        assert.deepEqual(s.requests, []);
    });

    test("with confirmed it does not ask", async () => {
        const s = screen();
        const result = await s.bus.perform({ action: "delete-picked", confirmed: true });
        await settle();
        assert.deepEqual(s.confirm.asked, []);
        assert.equal(result.triggered, true);
        assert.equal(s.requests.length, 1);
        assert.equal(s.requests[0].method, "POST");
    });
});

describe("perform says why not", () => {
    test("an action nobody has heard of", async () => {
        const s = screen();
        const result = await s.bus.perform({ action: "nope" });
        assert.equal(result.ok, false);
        assert.equal(result.reason, "unknown-action");
        assert.deepEqual(s.requests, []);
    });

    test("a disabled action, with what the screen says about it", async () => {
        const s = screen();
        const result = await s.bus.perform({ action: "off" });
        assert.equal(result.reason, "disabled");
        assert.match(result.message, /pick a message first/);
        assert.deepEqual(s.requests, []);
    });

    test("an action with nothing wired to it", async () => {
        const s = screen();
        const result = await s.bus.perform({ action: "dead" });
        assert.equal(result.reason, "no-trigger");
    });

    test("an unknown field stops the command before it acts", async () => {
        const s = screen();
        const result = await s.bus.perform({ fields: { nope: "x" }, action: "search" });
        await settle();
        assert.equal(result.reason, "unknown-field");
        assert.equal(result.triggered, false);
        assert.deepEqual(s.requests, []);
    });
});

describe("a bus says who it is", () => {
    test("by the root's id", () => {
        const s = screen();
        assert.equal(s.bus.id(), "root");
        assert.equal(s.root.getAttribute("data-sui-bus"), "root");
    });

    test("and can be renamed", () => {
        const s = screen();
        s.bus.setId("widget");
        assert.equal(s.bus.id(), "widget");
        assert.equal(s.root.getAttribute("data-sui-bus"), "widget");
    });
});
