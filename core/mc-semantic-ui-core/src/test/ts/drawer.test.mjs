/**
 * UiDrawer in the browser: the server's markup, the classes per edge and
 * state, and — the point of it — that minimizing and opening again never
 * redraw the content. Against the compiled output and a stub DOM
 * (support/mini-dom.mjs), like the other TypeScript tests.
 */
import { test, describe, before, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const CASES = JSON.parse(readFileSync(path.resolve(HERE, "../resources/drawer/drawer-cases.json"), "utf8"));

// Loaded up front: a top-level before() does not run first on every Node
// the build uses.
const icon = await import(`${DIST}/renderers/icon.js`);
const drawerJs = await import(`${DIST}/renderers/drawer.js`);
const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);

describe("rendering", () => {
    // DrawerRenderTest renders the same cases through drawer.hbs.
    for (const c of CASES) {
        test(`as on the server: ${c.name}`, () => {
            icon.setIconSpriteUrl("/sui/icons.svg");
            assert.equal(createDefaultRenderer().render(c.node), c.html);
        });
    }

    test("a class per edge, state, scope and mode; defaults right, open, viewport, overlay", () => {
        const r = createDefaultRenderer();
        const cls = (node) => /class="([^"]*)"/.exec(r.render({ type: "drawer", id: "d", title: "D", ...node }))[1];
        assert.equal(cls({}), "sui-drawer sui-drawer--right sui-drawer--open sui-drawer--viewport sui-drawer--overlay");
        for (const edge of ["TOP", "BOTTOM", "LEFT", "RIGHT"]) {
            for (const state of ["OPEN", "MINIMIZED", "CLOSED"]) {
                const c = cls({ edge, state });
                assert.ok(c.includes(`sui-drawer--${edge.toLowerCase()}`) && c.includes(`sui-drawer--${state.toLowerCase()}`), c);
            }
        }
        assert.match(cls({ scope: "CONTAINER", mode: "PUSH", resizable: true }), /sui-drawer--container sui-drawer--push sui-drawer--resizable/);
    });

    test("a size that is not a CSS length never reaches the style", () => {
        const html = createDefaultRenderer().render({ type: "drawer", id: "d", title: "D", size: '1px;background:url(x)', minSize: "200px", maxSize: '9" onmouseover="x' });
        assert.match(html, / style="--sui-drawer-min:200px;"/);
        assert.doesNotMatch(html, /background|onmouseover/);
    });
});

// ── Minimize and open: a class swap, the content untouched ──────────────────

function drawerElement(state = "open") {
    installControls();
    const input = new globalThis.HTMLInputElement("input", { name: "ask", value: "typed" });
    input.focus = () => { focused = input; };
    const body = new El("div", { class: "sui-drawer-body" }, [input]);
    const panel = new El("div", { class: "sui-drawer-panel", id: "d__panel", tabindex: "-1" }, [new El("div", { class: "sui-drawer-header" }), body]);
    panel.focus = () => { focused = panel; };
    const handle = new El("button", { class: "sui-drawer-handle", "data-sui-drawer": "open", "aria-expanded": String(state === "open") });
    handle.focus = () => { focused = handle; };
    const el = new El("div", { class: `sui-drawer sui-drawer--bottom sui-drawer--${state} sui-drawer--viewport sui-drawer--overlay`, id: "d", "data-sui": "drawer", "data-state": state }, [handle, panel]);
    return { el, body, input, handle, panel };
}
let focused;

describe("state", () => {
    beforeEach(() => { focused = null; });

    test("minimizing and opening again is a class swap: the content is the same, untouched", () => {
        const { el, body, input } = drawerElement("open");
        const before = { children: [...body.children], html: body.innerHTML };
        const r = createDefaultRenderer();
        let renders = 0;
        const realRender = r.render.bind(r);
        r.render = (n) => { renders++; return realRender(n); };

        assert.equal(drawerJs.setDrawerState(el, "minimized", true), true);
        assert.match(el.className, /sui-drawer--minimized/);
        assert.doesNotMatch(el.className, /sui-drawer--open/);
        assert.equal(el.getAttribute("data-state"), "minimized");
        assert.equal(el.querySelector(".sui-drawer-handle").getAttribute("aria-expanded"), "false");

        assert.equal(drawerJs.setDrawerState(el, "open", true), true);
        assert.deepEqual(body.children, before.children, "the very same elements");
        assert.equal(body.innerHTML, before.html);
        assert.equal(input.value, "typed", "what was typed is still there");
        assert.equal(renders, 0, "nothing was rendered");
    });

    test("the focus goes into the drawer on open, back to the handle on minimize", () => {
        const { el, input, handle } = drawerElement("open");
        drawerJs.setDrawerState(el, "minimized", true);
        assert.equal(focused, handle);
        drawerJs.setDrawerState(el, "open", true);
        assert.equal(focused, input, "the first control inside");
    });

    test("a hidden field inside does not take the focus", () => {
        const { el, body, input } = drawerElement("minimized");
        const hidden = new globalThis.HTMLInputElement("input", { type: "hidden", name: "html" });
        hidden.focus = () => { focused = hidden; };
        body.children.unshift(hidden); hidden.parentElement = body;
        drawerJs.setDrawerState(el, "open", true);
        assert.equal(focused, input);
    });

    test("the same state again changes nothing", () => {
        const { el } = drawerElement("open");
        assert.equal(drawerJs.setDrawerState(el, "open"), false);
    });
});

describe("a replace keeps what the user chose", () => {
    const lookup = (states) => (id) => (id in states ? { getAttribute: () => states[id] } : null);

    test("a drawer without a state takes the one it has on the page", () => {
        const node = { type: "stack", id: "s", children: [
            { type: "drawer", id: "chat", title: "Chat", content: { type: "text", id: "t", text: "new" } },
            { type: "drawer", id: "log", title: "Log", state: "OPEN" },
        ] };
        const kept = drawerJs.keepDrawerStates(node, lookup({ chat: "minimized", log: "minimized" }));
        assert.equal(kept.children[0].state, "MINIMIZED", "the user minimized it; the patch did not say");
        assert.equal(kept.children[1].state, "OPEN", "the patch said OPEN: it wins");
        assert.equal(kept.children[0].content.text, "new", "the rest is the patch's");
        assert.equal(node.children[0].state, undefined, "the patch itself is not changed");
    });

    test("a drawer new to the page opens", () => {
        const kept = drawerJs.keepDrawerStates({ type: "drawer", id: "fresh", title: "F" }, lookup({}));
        assert.equal(kept.state, undefined);
    });
});

describe("the user's controls", () => {
    let listeners, changes, parts;

    before(() => {
        installControls();
        listeners = {};
        globalThis.document = {
            addEventListener: (t, fn) => { (listeners[t] ??= []).push(fn); },
            // A drag adds pointermove/pointerup and takes them away again.
            removeEventListener: (t, fn) => { listeners[t] = (listeners[t] ?? []).filter(f => f !== fn); },
            querySelectorAll: () => [],
        };
        globalThis.window = { innerWidth: 1000, innerHeight: 800 };
        drawerJs.wireDrawers((drawer, state, closed) => changes.push([drawer.id, state, closed]));
    });

    beforeEach(() => {
        changes = [];
        focused = null;
        parts = drawerElement("minimized");
        const minimize = new El("button", { "data-sui-drawer": "minimize" });
        const close = new El("button", { "data-sui-drawer": "close" });
        parts.panel.children[0].appendChild(minimize);
        parts.panel.children[0].appendChild(close);
        Object.assign(parts, { minimize, close });
    });

    afterEach(() => { });

    const fire = (type, target, extra = {}) => {
        const event = { type, target, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, ...extra };
        for (const fn of listeners[type] ?? []) fn(event);
        return event;
    };

    /** A grip on the drawer, and a size for whatever carries it. */
    const withGrip = (edge, mode, box) => {
        const grip = new El("div", { class: "sui-drawer-resize", "data-sui-drawer": "resize" });
        parts.panel.appendChild(grip);
        parts.el.className = `sui-drawer sui-drawer--${edge} sui-drawer--open sui-drawer--viewport sui-drawer--${mode} sui-drawer--resizable`;
        const sized = mode === "push" ? parts.el : parts.panel;
        sized.getBoundingClientRect = () => ({ ...box, top: 0, left: 0, right: box.width, bottom: box.height });
        return grip;
    };
    const size = () => parts.el.style.getPropertyValue("--sui-drawer-size");

    test("the grip resizes by how far the pointer came, not by where it is", () => {
        // The drawer is 260 wide; the pointer travels 60px away from its edge.
        const grip = withGrip("right", "overlay", { width: 260, height: 300 });
        fire("pointerdown", grip, { clientX: 900, clientY: 400 });
        fire("pointermove", grip, { clientX: 840, clientY: 400 });
        assert.equal(size(), "320px");
        // And back the other way, past where it started: narrower.
        fire("pointermove", grip, { clientX: 960, clientY: 400 });
        assert.equal(size(), "200px");
        fire("pointerup", grip, { clientX: 960, clientY: 400 });
    });

    test("a drawer that takes room in the layout measures from its own box", () => {
        // PUSH: the element is the box, and it is nowhere near the window's
        // edge — a neighbour stands between them. The window's width must not
        // enter into it, which is what the old reading did: it measured the
        // distance from the window's edge and jumped by however far the two
        // edges were apart.
        globalThis.window = { innerWidth: 4000, innerHeight: 3000 };
        const grip = withGrip("right", "push", { width: 280, height: 300 });
        fire("pointerdown", grip, { clientX: 700, clientY: 500 });
        fire("pointermove", grip, { clientX: 640, clientY: 500 });
        assert.equal(size(), "340px");
        fire("pointerup", grip, { clientX: 640, clientY: 500 });
        globalThis.window = { innerWidth: 1000, innerHeight: 800 };
    });

    test("every edge grows away from itself", () => {
        for (const [edge, from, to, expected] of [
            ["bottom", { clientX: 0, clientY: 500 }, { clientX: 0, clientY: 420 }, "380px"],
            ["top", { clientX: 0, clientY: 500 }, { clientX: 0, clientY: 580 }, "380px"],
            ["left", { clientX: 500, clientY: 0 }, { clientX: 580, clientY: 0 }, "340px"],
            ["right", { clientX: 500, clientY: 0 }, { clientX: 420, clientY: 0 }, "340px"],
        ]) {
            parts = drawerElement("open");
            const grip = withGrip(edge, "overlay", { width: 260, height: 300 });
            fire("pointerdown", grip, from);
            fire("pointermove", grip, to);
            assert.equal(size(), expected, `${edge}: dragging away from the edge must grow it`);
            fire("pointerup", grip, to);
        }
    });

    /** Two drawers at one edge of one container, both open, as a server may send them. */
    const twoAtOneEdge = (mode = "overlay") => {
        const make = (id) => {
            const p = drawerElement("open");
            p.el.id = id;
            p.el.className = `sui-drawer sui-drawer--bottom sui-drawer--open sui-drawer--container sui-drawer--${mode}`;
            return p;
        };
        const a = make("log"), b = make("console");
        const host = new El("div", { class: "sui-drawer-host" }, [a.el, b.el]);
        return { a, b, host };
    };

    test("opening a drawer at an edge where another is open sends that one to its handle", () => {
        const { a, b } = twoAtOneEdge();
        drawerJs.setDrawerState(a.el, "minimized");
        // The user brings the log back while the console is open at the same edge.
        fire("click", a.handle);
        assert.equal(a.el.getAttribute("data-state"), "open");
        assert.equal(b.el.getAttribute("data-state"), "minimized");
        // Both changes are the user's, and both are reported.
        assert.deepEqual(changes, [["log", "open", false], ["console", "minimized", false]]);
    });

    test("a PUSH drawer covers nothing, so it minimizes nothing", () => {
        const { a, b } = twoAtOneEdge("push");
        drawerJs.setDrawerState(a.el, "minimized");
        fire("click", a.handle);
        assert.equal(b.el.getAttribute("data-state"), "open");
    });

    test("drawers of a group keep their own rule", () => {
        const { a, b, host } = twoAtOneEdge();
        host.className = "sui-drawer-group sui-drawer-group--bottom sui-drawer-group--share";
        drawerJs.setDrawerState(a.el, "minimized");
        fire("click", a.handle);
        assert.equal(b.el.getAttribute("data-state"), "open");
    });

    test("the handle opens it, minimize and close do what they say, each reported once", () => {
        fire("click", parts.handle);
        assert.equal(parts.el.getAttribute("data-state"), "open");
        fire("click", parts.minimize);
        assert.equal(parts.el.getAttribute("data-state"), "minimized");
        fire("click", parts.close);
        assert.equal(parts.el.getAttribute("data-state"), "closed");
        assert.deepEqual(changes, [["d", "open", false], ["d", "minimized", false], ["d", "closed", true]]);
    });

    test("Escape inside an open drawer minimizes it and returns to the handle", () => {
        fire("click", parts.handle);
        const e = fire("keydown", parts.input, { key: "Escape" });
        assert.ok(e.defaultPrevented);
        assert.equal(parts.el.getAttribute("data-state"), "minimized");
        assert.equal(focused, parts.handle);
    });

    test("Escape leaves the drawer alone while a menu inside is open", () => {
        fire("click", parts.handle);
        const menu = new globalThis.HTMLDetailsElement("details", { class: "sui-menu-button" });
        menu.open = true;
        parts.panel.children[1].appendChild(menu);
        fire("keydown", parts.input, { key: "Escape" });
        assert.equal(parts.el.getAttribute("data-state"), "open");
    });
});
