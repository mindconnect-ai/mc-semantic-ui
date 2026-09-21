/**
 * A menu-button's keyboard and placement: Enter/Space on the trigger open it
 * with the focus on the first entry, the arrows walk the entries (past
 * headings, dividers and disabled ones), Escape closes and returns to the
 * trigger; the popover opens above a trigger at the bottom of the window and
 * stays inside it sideways. Against the compiled output and a stub DOM.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let listeners, focused, details, trigger, popover, entries;

function entry(id, attrs = {}) {
    const e = new El(attrs.tag ?? "button", { class: "sui-menu-button-item", id, role: "menuitem", ...attrs.a });
    if (attrs.disabled) e.disabled = true;
    e.focus = () => { focused = e; };
    return e;
}

function key(k, target) {
    const event = { key: k, target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
    for (const fn of listeners.keydown ?? []) fn(event);
    return event;
}

function click(target, detail) {
    const event = { target, detail, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
    for (const fn of listeners.click ?? []) fn(event);
}

describe("menu-button keyboard and placement", () => {
    before(async () => {
        installControls();
        listeners = {};
        globalThis.window = { innerWidth: 1000, innerHeight: 800, addEventListener() { } };
        globalThis.document = { addEventListener: (t, fn) => { (listeners[t] ??= []).push(fn); },
            querySelectorAll: (s) => details && details.matches(s) ? [details] : [],
            querySelector: (s) => details && details.matches(s) ? details : null };
        const { wireMenuButtons } = await import(`${DIST}/renderers/menu-button.js`);
        wireMenuButtons();
    });

    beforeEach(() => {
        focused = null;
        trigger = new El("summary", { class: "sui-menu-button-trigger sui-btn sui-btn--secondary" });
        trigger.focus = () => { focused = trigger; };
        entries = {
            draft: entry("draft"),
            heading: new El("div", { class: "sui-menu-button-heading", role: "presentation" }),
            sep: new El("div", { class: "sui-menu-button-sep", role: "separator" }),
            off: entry("off", { disabled: true }),
            shorten: entry("shorten"),
            summarize: entry("summarize"),
        };
        popover = new El("div", { class: "sui-menu-button-popover", role: "menu" },
            [entries.draft, entries.sep, entries.heading, entries.off, entries.shorten, entries.summarize]);
        popover.offsetWidth = 220;
        popover.offsetHeight = 200;
        details = new globalThis.HTMLDetailsElement("details", { class: "sui-menu-button sui-menu-button--action sui-menu-button--align-start" }, [trigger, popover]);
        details.open = false;
    });

    test("Enter or Space (a click without a pointer) opens it on the first entry", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        click(trigger, 0);
        assert.equal(details.open, true);
        assert.equal(trigger.getAttribute("aria-expanded"), "true");
        assert.equal(focused, entries.draft);
    });

    test("Enter and Space on the trigger open it themselves; a click synthesised from them does not toggle it back", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        assert.ok(key("Enter", trigger).defaultPrevented);
        assert.equal(details.open, true);
        assert.equal(focused, entries.draft);
        click(trigger, 0);   // what a browser may still fire from the Enter
        assert.equal(details.open, true);
        key(" ", trigger);
        assert.equal(details.open, false, "Space on the trigger of an open menu closes it");
    });

    test("a mouse click opens it and leaves the focus alone", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        click(trigger, 1);
        assert.equal(details.open, true);
        assert.equal(focused, null);
    });

    test("the arrows walk the entries, past headings, dividers and disabled ones, and wrap", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        assert.ok(key("ArrowDown", trigger).defaultPrevented);
        assert.equal(details.open, true);
        assert.equal(focused, entries.draft);
        key("ArrowDown", focused); assert.equal(focused, entries.shorten, "heading, divider and the disabled entry skipped");
        key("ArrowDown", focused); assert.equal(focused, entries.summarize);
        key("ArrowDown", focused); assert.equal(focused, entries.draft, "wraps");
        key("ArrowUp", focused);   assert.equal(focused, entries.summarize);
        key("Home", focused);      assert.equal(focused, entries.draft);
        key("End", focused);       assert.equal(focused, entries.summarize);
    });

    test("ArrowUp on the trigger opens it on the last entry", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        key("ArrowUp", trigger);
        assert.equal(focused, entries.summarize);
    });

    test("Escape closes it and returns to the trigger", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 50, right: 150 });
        key("ArrowDown", trigger);
        key("Escape", focused);
        assert.equal(details.open, false);
        assert.equal(focused, trigger);
    });

    test("a disabled menu does not open", () => {
        trigger.setAttribute("aria-disabled", "true");
        click(trigger, 0);
        key("ArrowDown", trigger);
        assert.equal(details.open, false);
    });

    test("at the bottom of the window it opens above the trigger", () => {
        trigger.getBoundingClientRect = () => ({ top: 740, bottom: 776, left: 50, right: 150 });
        click(trigger, 1);
        assert.equal(popover.style.top, `${740 - 6 - 200}px`);
        assert.equal(popover.style.position, "fixed");
    });

    test("below when there is room, and inside the window sideways", () => {
        trigger.getBoundingClientRect = () => ({ top: 100, bottom: 136, left: 900, right: 990 });
        click(trigger, 1);
        assert.equal(popover.style.top, `${136 + 6}px`);
        assert.equal(popover.style.left, `${1000 - 220 - 8}px`, "kept 8px inside the right edge");
    });

    test("with room on neither side, on the roomier one, cut to fit and scrolling", () => {
        popover.offsetHeight = 900;
        trigger.getBoundingClientRect = () => ({ top: 600, bottom: 636, left: 50, right: 150 });
        click(trigger, 1);
        assert.equal(popover.style.top, "8px");
        assert.equal(popover.style.maxHeight, `${600 - 6 - 8}px`);
        assert.equal(popover.style.overflowY, "auto");
    });
});
