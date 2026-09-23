/**
 * UiDrawerGroup in the browser: the group is the element at the edge and
 * owns edge, scope and mode; SHARE and STACK are classes the stylesheet
 * lays out; in a STACK the front drawer is decided and switched without a
 * redraw, and the group's onActiveChange hears of it. Against the compiled
 * output and the stub DOM (support/mini-dom.mjs), like the drawer's tests.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const CASES = JSON.parse(readFileSync(path.resolve(HERE, "../resources/drawer/drawer-group-cases.json"), "utf8"));

const icon = await import(`${DIST}/renderers/icon.js`);
const drawerJs = await import(`${DIST}/renderers/drawer.js`);
const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);

const group = (extra = {}) => ({
    type: "drawer-group", id: "dock", edge: "BOTTOM", scope: "CONTAINER", size: "60%",
    drawers: [
        { type: "drawer", id: "chat", title: "Chat", edge: "LEFT", scope: "VIEWPORT", mode: "PUSH", resizable: true, content: { type: "text", id: "t1", text: "hi" } },
        { type: "drawer", id: "files", title: "Files", content: { type: "text", id: "t2", text: "a.pdf" } },
    ],
    ...extra,
});

describe("rendering", () => {
    // DrawerGroupRenderTest renders the same cases through drawer-group.hbs.
    for (const c of CASES) {
        test(`as on the server: ${c.name}`, () => {
            icon.setIconSpriteUrl("/sui/icons.svg");
            assert.equal(createDefaultRenderer().render(c.node), c.html);
        });
    }

    test("the group stands at the edge with the strip's size, the drawers inside it", () => {
        const html = createDefaultRenderer().render(group({ resizable: true, arrange: "SHARE" }));
        assert.match(html, /^<div class="sui-drawer-group sui-drawer-group--bottom sui-drawer-group--container sui-drawer-group--overlay sui-drawer-group--share sui-drawer-group--resizable" id="dock" data-sui="drawer-group" style="--sui-drawer-size:60%;">/);
        assert.equal((html.match(/data-sui="drawer"/g) ?? []).length, 2);
        // The strip's grip is the group's, last in it.
        assert.match(html, /<div class="sui-drawer-resize" data-sui-drawer="resize"[^>]*><\/div><\/div>$/);
    });

    test("a drawer inside takes the group's edge, scope and mode, and loses its own grip", () => {
        const html = createDefaultRenderer().render(group());
        // The chat asked for LEFT, VIEWPORT, PUSH and a grip; the group said otherwise.
        assert.match(html, /id="chat"/);
        assert.match(html, /class="sui-drawer sui-drawer--bottom sui-drawer--open sui-drawer--container sui-drawer--overlay" id="chat"/);
        assert.doesNotMatch(html, /sui-drawer--left|sui-drawer--push|sui-drawer--resizable/);
        assert.doesNotMatch(html, /id="chat"[\s\S]*?sui-drawer-resize[\s\S]*?id="files"/);
    });

    test("STACK carries who is in front and how to report a switch", () => {
        const html = createDefaultRenderer().render(group({
            arrange: "STACK", active: "files",
            onActiveChange: { url: "/dock/front/{id}", method: "POST", behavior: "APPLY_RESPONSE" },
        }));
        assert.match(html, /sui-drawer-group--stack/);
        assert.match(html, /data-active="files"/);
        assert.match(html, /data-active-trigger='\{"url":"\/dock\/front\/\{id\}","method":"POST","behavior":"APPLY_RESPONSE"\}'/);
    });

    test("a size that is not a CSS length never reaches the style", () => {
        const html = createDefaultRenderer().render(group({ size: "60%; background:url(x)" }));
        assert.doesNotMatch(html, /style=/);
    });
});

// ── The stack in the DOM ────────────────────────────────────────────────────

/** A STACK group of three, as the renderer's markup stands in the DOM. */
function stackElements(active) {
    const drawers = ["log", "notes", "tasks"].map(id => {
        const header = new El("div", { class: "sui-drawer-header" });
        const panel = new El("div", { class: "sui-drawer-panel" }, [header, new El("div", { class: "sui-drawer-body" })]);
        const handle = new El("button", { class: "sui-drawer-handle", "data-sui-drawer": "open" });
        const el = new El("div", { class: "sui-drawer sui-drawer--bottom sui-drawer--open sui-drawer--container sui-drawer--overlay", id, "data-sui": "drawer", "data-state": "open" }, [handle, panel]);
        for (const x of [el, panel, handle]) x.focus = () => { };
        return { el, header, handle, panel };
    });
    const groupEl = new El("div", { class: "sui-drawer-group sui-drawer-group--bottom sui-drawer-group--container sui-drawer-group--overlay sui-drawer-group--stack", id: "dock", "data-sui": "drawer-group", ...(active ? { "data-active": active } : {}) },
        drawers.map(d => d.el));
    const root = new El("div", { id: "root" }, [groupEl]);
    return { root, groupEl, drawers: Object.fromEntries(drawers.map(d => [d.el.id, d])) };
}

const front = (groupEl) => groupEl.children.filter(d => d.classList.contains("sui-drawer--front")).map(d => d.id);

describe("a stack", () => {
    let listeners, fronts, changes;

    before(() => {
        installControls();
        listeners = {};
        globalThis.document = {
            addEventListener: (t, fn) => { (listeners[t] ??= []).push(fn); },
            removeEventListener: (t, fn) => { listeners[t] = (listeners[t] ?? []).filter(f => f !== fn); },
            querySelectorAll: () => [],
        };
        globalThis.window = { innerWidth: 1000, innerHeight: 800 };
        drawerJs.wireDrawers(
            (drawer, state, closed) => changes.push([drawer.id, state, closed]),
            (groupEl, drawer) => fronts.push([groupEl.id, drawer.id]));
    });

    beforeEach(() => { fronts = []; changes = []; });

    const fire = (type, target) => {
        const event = { type, target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
        for (const fn of listeners[type] ?? []) fn(event);
        return event;
    };

    test("the layout pass puts the named drawer in front, or the first open one", () => {
        const named = stackElements("notes");
        drawerJs.layoutDrawerHandles(named.root);
        assert.deepEqual(front(named.groupEl), ["notes"]);

        const unnamed = stackElements(null);
        drawerJs.layoutDrawerHandles(unnamed.root);
        assert.deepEqual(front(unnamed.groupEl), ["log"]);
        assert.equal(unnamed.groupEl.getAttribute("data-active"), "log");
    });

    test("a click on a peeking header brings that drawer forward and says so", () => {
        const { root, groupEl, drawers } = stackElements("notes");
        drawerJs.layoutDrawerHandles(root);
        fire("click", drawers.tasks.header);
        assert.deepEqual(front(groupEl), ["tasks"]);
        assert.equal(groupEl.getAttribute("data-active"), "tasks");
        assert.deepEqual(fronts, [["dock", "tasks"]]);
        // Nothing was redrawn: the same elements stand where they were.
        assert.equal(groupEl.children[2], drawers.tasks.el);
    });

    test("a click on the front drawer's own header changes nothing", () => {
        const { root, groupEl, drawers } = stackElements("notes");
        drawerJs.layoutDrawerHandles(root);
        fire("click", drawers.notes.header);
        assert.deepEqual(front(groupEl), ["notes"]);
        assert.deepEqual(fronts, []);
    });

    test("minimizing the front drawer leaves the next open one in front", () => {
        const { root, groupEl, drawers } = stackElements("notes");
        drawerJs.layoutDrawerHandles(root);
        const minimize = new El("button", { "data-sui-drawer": "minimize" });
        drawers.notes.header.appendChild(minimize);
        fire("click", minimize);
        assert.equal(drawers.notes.el.getAttribute("data-state"), "minimized");
        assert.deepEqual(front(groupEl), ["log"]);
        assert.deepEqual(changes, [["notes", "minimized", false]]);
    });

    test("opening a drawer from its handle puts it in front", () => {
        const { root, groupEl, drawers } = stackElements("notes");
        drawerJs.layoutDrawerHandles(root);
        drawerJs.setDrawerState(drawers.tasks.el, "minimized");
        fire("click", drawers.tasks.handle);
        assert.equal(drawers.tasks.el.getAttribute("data-state"), "open");
        assert.deepEqual(front(groupEl), ["tasks"]);
        assert.deepEqual(fronts, [["dock", "tasks"]]);
    });
});

describe("a replace keeps who is in front", () => {
    test("a group without an active takes the one on the page; one with an active keeps it", () => {
        const lookup = (id) => id === "dock" ? new El("div", { id, "data-active": "tasks" }) : null;
        const kept = drawerJs.keepDrawerStates({ type: "drawer-group", id: "dock", drawers: [] }, lookup);
        assert.equal(kept.active, "tasks");
        const told = drawerJs.keepDrawerStates({ type: "drawer-group", id: "dock", active: "log", drawers: [] }, lookup);
        assert.equal(told.active, "log");
    });
});
