/**
 * The document-level wiring of UiField.suggest — keys, the question going out
 * with the right bus's fetch — on its own module instance: wireSuggest sets up
 * its listeners once per page, and this file's stand-in document is the page.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

installControls();
const Input = globalThis.HTMLInputElement;
const listeners = {};
const body = new El("body");
globalThis.window = { innerWidth: 1000, innerHeight: 800, addEventListener() { } };
globalThis.document = {
    body, activeElement: null,
    createElement: (t) => t === "input" ? new Input(t) : new El(t),
    addEventListener: (type, fn) => { (listeners[type] ??= []).push(fn); },
};
const { wireSuggest } = await import(`${DIST}/renderers/suggest.js`);

/** A keydown as the browser sends it, recording whether the page took it. */
function key(target, k, shift = false) {
    const e = { type: "keydown", key: k, shiftKey: shift, target, defaultPrevented: false, stopped: false,
        preventDefault() { this.defaultPrevented = true; }, stopPropagation() { this.stopped = true; } };
    for (const fn of listeners.keydown ?? []) fn(e);
    return e;
}

/** A field under {@code root}, focused, with a fetch that answers two suggestions. */
function fieldIn(root, attrs, requests) {
    const control = new Input("input", { id: "to__input", type: "text", value: "an", "data-sui-suggest": "/contacts",
        "data-sui-suggest-debounce": "0", ...attrs });
    root.appendChild(new El("div", { class: "sui-field" }, [control]));
    wireSuggest(root, () => async (url) => {
        requests.push(url);
        return { ok: true, json: async () => [{ value: "anna@x.ch" }, { value: "andreas@x.ch" }] };
    });
    document.activeElement = control;
    return control;
}

const typed = async (control) => {
    for (const fn of listeners.input ?? []) fn({ type: "input", target: control });
    await new Promise(r => setTimeout(r, 5));
};

describe("the wiring", () => {
    let dialogRequests, pageRequests;
    before(() => { dialogRequests = []; pageRequests = []; wireSuggest(); });

    test("a field in the dialog host asks with the bus's fetch registered for it", async () => {
        const host = new El("div", { id: "sui-dialogs" });
        body.appendChild(host);
        const control = fieldIn(host, { "data-sui-suggest-separator": "," }, dialogRequests);
        await typed(control);
        assert.deepEqual(dialogRequests, ["/contacts?q=an"]);
        assert.equal(control.getAttribute("aria-expanded"), "true");

        // Free text: nothing is highlighted, so Enter and Tab stay the field's.
        assert.equal(control.getAttribute("aria-activedescendant"), null);
        assert.equal(key(control, "Tab").defaultPrevented, false);
        assert.equal(key(control, "Enter").defaultPrevented, false);
        // An arrow chooses; Shift+Tab still goes back, untouched.
        assert.equal(key(control, "ArrowDown").defaultPrevented, true);
        assert.equal(control.getAttribute("aria-activedescendant"), "to__input__suggest__0");
        assert.equal(key(control, "Tab", true).defaultPrevented, false);
        // Enter takes it.
        assert.equal(key(control, "Enter").defaultPrevented, true);
        assert.equal(control.value, "anna@x.ch, ");
        assert.equal(control.getAttribute("aria-expanded"), "false");
    });

    test("a SELECT's search box never submits the form, and offers its first entry", async () => {
        const root = new El("div", { id: "root" });
        body.appendChild(root);
        const control = fieldIn(root, { id: "owner__input", "data-sui-suggest-mode": "one" }, pageRequests);
        control.parentElement.appendChild(new Input("input", { type: "hidden", name: "owner", value: "" }));
        // Before any answer is in: Enter is kept from the form.
        assert.equal(key(control, "Enter").defaultPrevented, true);
        await typed(control);
        assert.deepEqual(pageRequests, ["/contacts?q=an"]);
        assert.equal(control.getAttribute("aria-activedescendant"), "owner__input__suggest__0");
        assert.equal(key(control, "Enter").defaultPrevented, true);
        assert.equal(control.nextElementSibling.value, "anna@x.ch");
    });
});
