/**
 * A menu in a form's button bar (UiActionMenu): the server's markup, its place
 * in the bar, an entry that sends the form's fields — pushed through the real
 * click path of SuiEventBus — and the keyboard. Runs against the compiled
 * output with a DOM stubbed just far enough (support/mini-dom.mjs).
 */
import { test, describe, before, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const CASE = JSON.parse(readFileSync(path.resolve(HERE, "../resources/menu/action-menu-case.json"), "utf8"));

describe("UiActionMenu", () => {
    let menuButton, form, icon;
    before(async () => {
        icon = await import(`${DIST}/renderers/icon.js`);
        menuButton = await import(`${DIST}/renderers/menu-button.js`);
        form = await import(`${DIST}/renderers/form.js`);
    });

    test("the server's markup, character for character", () => {
        // ActionMenuRenderTest renders the same case through action-menu.hbs.
        icon.setIconSpriteUrl("/sui/icons.svg");
        assert.equal(menuButton.renderActionMenu(CASE.menu), CASE.html);
    });

    test("in the button bar in the order added, and never the form's submit", async () => {
        const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);
        const api = (url) => ({ url, method: "POST", payload: "compose", behavior: "APPLY_RESPONSE" });
        const html = form.renderForm({
            type: "form", id: "compose", title: "New mail", fields: [],
            actions: [
                { ...CASE.menu, id: "ai-first" },
                { type: "action", id: "send", label: "Send", style: "PRIMARY", onClick: api("/mail/send") },
                { type: "action", id: "check", label: "Check", onClick: api("/mail/check") },
            ],
        }, createDefaultRenderer());
        const footer = html.slice(html.indexOf("sui-form-footer"));
        assert.ok(footer.indexOf('id="ai-first"') < footer.indexOf('id="send"'));
        assert.ok(footer.indexOf('id="send"') < footer.indexOf('id="check"'));
        assert.match(html, /method="post" action="\/mail\/send"/, "the menu, though first, is not the submit");
    });

    test("a heading and a divider are not entries; a disabled entry is disabled", () => {
        const html = menuButton.renderActionMenu(CASE.menu);
        assert.match(html, /<div class="sui-menu-button-heading" role="presentation">Quick actions<\/div>/);
        assert.match(html, /<div class="sui-menu-button-sep" role="separator"><\/div>/);
        assert.match(html, /id="shorten"[^>]* disabled title="Nothing to shorten">/);
    });

    test("a disabled entry without a trigger is a link to nowhere, as on the server", () => {
        // ActionMenuRenderTest.theDocumentedExampleBuildsAndChains asserts the same markup.
        const html = menuButton.renderActionMenu({ type: "action-menu", id: "m", label: "M",
            items: [{ type: "menu-item", id: "translate", label: "Translate", enabled: false, disabledReason: "No translation service" }] });
        assert.ok(html.includes('<a class="sui-menu-button-item" id="translate" data-id="translate" role="menuitem" aria-disabled="true" title="No translation service">'), html);
        assert.doesNotMatch(html, /<a [^>]*href=/);
    });

    test("a disabled menu does not open", () => {
        const html = menuButton.renderActionMenu({ ...CASE.menu, enabled: false, disabledReason: "No AI key" });
        assert.match(html, /<summary [^>]*aria-disabled="true" title="No AI key">/);
    });
});

// ── The click path: an entry sends the form's fields ──────────────────────────

describe("a menu entry in a form sends the form", () => {
    let SuiEventBus, requests, root, formEl, docListeners;

    function button(id, trigger) {
        return new El("button", { type: "button", class: "sui-menu-button-item", id, role: "menuitem", "data-trigger": JSON.stringify(trigger) });
    }

    beforeEach(async () => {
        installControls();
        ({ SuiEventBus } = await import(`${DIST}/eventbus.js`));
        requests = [];
        docListeners = {};
        const body = new El("body");
        formEl = new globalThis.HTMLFormElement("form", { id: "compose", class: "sui-form", "data-sui": "form" }, [
            new globalThis.HTMLInputElement("input", { name: "subject", value: "Hello", "data-sui-type": "TEXT" }),
            new globalThis.HTMLTextAreaElement("textarea", { name: "body", value: "<p>Draft</p>", "data-sui-type": "RICHTEXT" }),
            new El("div", { class: "sui-form-footer" }, [
                new globalThis.HTMLDetailsElement("details", { class: "sui-menu-button sui-menu-button--action", id: "ai", "data-sui": "menu-button" }, [
                    new El("summary", { class: "sui-menu-button-trigger sui-btn sui-btn--secondary" }),
                    new El("div", { class: "sui-menu-button-popover", role: "menu" }, [
                        button("draft", { url: "/ai/draft", method: "POST", payload: "compose", behavior: "APPLY_RESPONSE" }),
                        button("shorten", { url: "/ai/shorten", method: "POST", behavior: "APPLY_RESPONSE" }),
                    ]),
                ]),
            ]),
        ]);
        // An entry of a menu somewhere else on the page — as far from the form as
        // a popover fixed to the window is — that names the form by id.
        const elsewhere = new El("div", { class: "sui-menu-button-popover", role: "menu" }, [
            button("far", { url: "/ai/far", method: "POST", payload: "compose", behavior: "APPLY_RESPONSE" }),
        ]);
        root = new El("div", { id: "root" }, [formEl, elsewhere]);
        body.appendChild(root);
        const byId = (id, from = body) => from.id === id ? from : from.children.map(c => byId(id, c)).find(Boolean) ?? null;
        globalThis.window = { location: { pathname: "/", search: "", href: "http://localhost/" }, addEventListener() { }, confirm: () => true,
            innerWidth: 1200, innerHeight: 800, history: { pushState() { }, replaceState() { } } };
        globalThis.document = { body, getElementById: (id) => byId(id), createElement: (t) => new El(t),
            querySelectorAll: (s) => body.querySelectorAll(s), querySelector: (s) => body.querySelector(s),
            addEventListener: (t, fn) => { (docListeners[t] ??= []).push(fn); }, removeEventListener() { } };
        globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible" });
    });

    afterEach(() => {
        for (const g of ["window", "document", "requestAnimationFrame", "getComputedStyle"]) delete globalThis[g];
    });

    function bus() {
        const renderer = { render: () => "", seedModels() { }, applyPatch() { }, showLoading() { }, hideLoading() { }, mount() { } };
        const b = new SuiEventBus(renderer, root);
        b.setFetcher(async (url, init) => {
            requests.push({ url, method: init.method, body: init.body ? JSON.parse(init.body) : null });
            return { ok: true, status: 200, headers: { get: () => "application/json" }, json: async () => ({ patches: [] }), text: async () => "{}" };
        });
        return b;
    }

    async function click(id) {
        const target = document.getElementById(id);
        const event = { type: "click", target, detail: 1, button: 0, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, stopPropagation() { } };
        for (const fn of root.listeners.click ?? []) await fn(event);
        await new Promise(r => setTimeout(r, 10));
    }

    test("an entry that names the form sends its fields", async () => {
        bus();
        await click("draft");
        assert.equal(requests.length, 1);
        assert.equal(requests[0].url, "/ai/draft");
        assert.deepEqual(requests[0].body, { subject: "Hello", body: "<p>Draft</p>" });
    });

    test("by the form's id, not by where the menu sits", async () => {
        bus();
        await click("far");
        assert.deepEqual(requests[0].body, { subject: "Hello", body: "<p>Draft</p>" });
    });

    test("in the form's button bar, an entry that names nothing sends the form too", async () => {
        bus();
        await click("shorten");
        assert.equal(requests[0].url, "/ai/shorten");
        assert.deepEqual(requests[0].body, { subject: "Hello", body: "<p>Draft</p>" });
    });
});
