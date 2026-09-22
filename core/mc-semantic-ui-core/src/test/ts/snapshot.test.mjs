/**
 * The bus knows its tree: the renderer keeps a copy of the page it drew and
 * of every patch since, and buildSnapshot turns that copy into what is on the
 * screen — values read out of the DOM, secrets left out, size kept in hand.
 *
 * Two halves, both without jsdom: the copy is checked against a real
 * SuiRenderer driven by the stub DOM (support/mini-dom.mjs), the outline
 * against buildSnapshot with a DOM stand-in that answers for the controls.
 */
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

installControls();
const { buildSnapshot } = await import(`${DIST}/snapshot.js`);
const { SuiRenderer, installDefaultHandlers } = await import(`${DIST}/renderer.js`);

// ── The copy the renderer keeps ─────────────────────────────────────────────

/** A page, its elements, and a document that finds them by id. */
function pageWithDom() {
    const elements = new Map();
    const root = new El("div", { id: "root" });
    const add = (id) => {
        const el = new El("div", { id });
        root.appendChild(el);
        elements.set(id, el);
        return el;
    };
    for (const id of ["shell", "inbox", "compose", "subject", "banner"]) add(id);
    globalThis.window = { getComputedStyle: () => ({ position: "static" }), innerWidth: 1000, innerHeight: 800 };
    globalThis.document = {
        body: root,
        getElementById: (id) => elements.get(id) ?? null,
        createElement: (t) => new El(t),
        querySelectorAll: (s) => root.querySelectorAll(s),
        querySelector: (s) => root.querySelector(s),
        addEventListener() { }, removeEventListener() { }, dispatchEvent() { return true; },
    };
    globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
    globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible", position: "static" });
    return { root, elements, add };
}

describe("the renderer's copy of the tree", () => {
    const { root, elements } = pageWithDom();
    const renderer = new SuiRenderer(root);
    installDefaultHandlers(renderer);
    renderer.animatePatches = false;

    const page = {
        type: "stack", id: "shell", children: [
            { type: "list", id: "inbox", title: "Inbox", items: [{ id: "m-1", label: "Techpresso" }] },
            {
                type: "form", id: "compose", title: "New message",
                fields: [{ type: "field", id: "subject", label: "Subject", fieldType: "TEXT", value: "" }],
            },
            { type: "text", id: "banner", text: "3 unread" },
        ],
    };
    renderer.mount(page);

    test("mount is the page", () => {
        assert.equal(renderer.tree().id, "shell");
        assert.equal(renderer.tree().children.length, 3);
    });

    test("REPLACE swaps the subtree", () => {
        renderer.applyPatch({
            patches: [{
                op: "REPLACE", targetId: "inbox",
                node: { type: "list", id: "inbox", title: "Archive", items: [{ id: "m-9", label: "Receipt" }] },
            }],
        });
        assert.equal(renderer.tree().children[0].title, "Archive");
        assert.deepEqual(renderer.tree().children[0].items.map(i => i.id), ["m-9"]);
    });

    test("MERGE writes over the node it names and keeps the rest", () => {
        renderer.applyPatch({
            patches: [{ op: "MERGE", targetId: "compose", attributes: { title: "Reply" } }],
        });
        const form = renderer.tree().children[1];
        assert.equal(form.title, "Reply");
        assert.equal(form.fields[0].id, "subject");
    });

    test("APPEND adds a child", () => {
        renderer.applyPatch({
            patches: [{
                op: "APPEND", targetId: "inbox",
                node: { type: "list", id: "more", items: [{ id: "m-10", label: "Invoice" }] },
            }],
        });
        assert.deepEqual(renderer.tree().children[0].items.map(i => i.id), ["m-9", "m-10"]);
    });

    test("REMOVE drops it", () => {
        renderer.applyPatch({ patches: [{ op: "REMOVE", targetId: "banner" }] });
        assert.deepEqual(renderer.tree().children.map(c => c.id), ["inbox", "compose"]);
    });

    test("the copy and the DOM say the same thing", () => {
        // What the DOM has: the ids the renderer wrote to, minus the one it
        // removed. What the copy has: the same ids.
        const inCopy = [];
        const walk = (n) => {
            if (n?.id) inCopy.push(n.id);
            for (const c of n?.children ?? []) walk(c);
        };
        walk(renderer.tree());
        assert.deepEqual(inCopy, ["shell", "inbox", "compose"]);
        assert.equal(document.getElementById("banner").parentElement, null);
    });

    test("a table patched through its own model is in the copy too", () => {
        // A row patch never reaches the generic path: the table re-renders
        // from the model parked on its element. The copy has to follow.
        const table = {
            type: "table", id: "orders",
            columns: [{ type: "column", id: "status", label: "Status" }],
            rows: [{ type: "row", id: "row-1", data: { status: "open" } }],
        };
        const wrapper = new El("div", {
            id: "orders", "data-sui": "table", "data-node": JSON.stringify(table),
        });
        const row = new El("tr", { id: "row-1" });
        wrapper.appendChild(row);
        root.appendChild(wrapper);
        elements.set("orders", wrapper);
        elements.set("row-1", row);
        renderer.applyPatch({
            patches: [{ op: "REPLACE", targetId: "compose", node: { type: "stack", id: "compose", children: [table] } }],
        });
        renderer.applyPatch({
            patches: [{ op: "MERGE", targetId: "row-1", attributes: { data: { status: "paid" } } }],
        });
        const inCopy = renderer.tree().children[1].children[0];
        assert.equal(inCopy.id, "orders");
        assert.deepEqual(inCopy.rows[0].data, { status: "paid" });
    });
});

// ── What the snapshot says ──────────────────────────────────────────────────

/** A screen: the tree, plus a DOM stand-in that answers for its controls. */
function screen(typed = {}, attrs = {}) {
    const tree = {
        type: "stack", id: "email-shell", children: [
            {
                type: "list", id: "email-list", title: "All inboxes",
                items: [{
                    id: "pick-1", label: "Techpresso", description: "Apple unveils…",
                    onClick: { url: "/mail/1", method: "GET", behavior: "APPLY_RESPONSE" },
                }],
            },
            // No id and nothing to say: pure layout, so the outline does not
            // grow a level for it — its children move up.
            {
                type: "stack", children: [{
                    type: "form", id: "search-form", title: "Search",
                    fields: [
                        { type: "field", id: "q", label: "Search", fieldType: "TEXT", value: "" },
                        { type: "field", id: "pw", label: "Password", fieldType: "PASSWORD", value: "hunter2" },
                        { type: "field", id: "_csrf", label: "", fieldType: "HIDDEN", value: "abc123" },
                    ],
                    actions: [
                        { type: "action", id: "search", label: "Search", style: "PRIMARY" },
                        {
                            type: "action", id: "delete-picked", label: "Delete the ticked messages",
                            style: "DANGER", confirm: "Delete 3 messages?",
                        },
                    ],
                }],
            },
        ],
    };
    const dom = {
        byId: (id) => (id in attrs ? new El("div", attrs[id]) : new El("div", { id })),
        values: (el) => ({ ...typed }),
    };
    return { tree, dom };
}

describe("snapshot", () => {
    test("the outline is what someone acting on the screen needs", () => {
        const { tree, dom } = screen({ q: "rechnung" });
        const { node, busId } = buildSnapshot(tree, {}, dom, "sui-bus-1");
        assert.equal(busId, "sui-bus-1");
        assert.equal(node.id, "email-shell");

        const list = node.children[0];
        assert.equal(list.title, "All inboxes");
        assert.deepEqual(list.items, [{
            id: "pick-1", label: "Techpresso", description: "Apple unveils…", clickable: true,
        }]);

        // The layout node in between is gone; the form stands where it did.
        const form = node.children[1];
        assert.equal(form.id, "search-form");
        assert.deepEqual(form.actions, [
            { id: "search", label: "Search", style: "PRIMARY", enabled: true },
            {
                id: "delete-picked", label: "Delete the ticked messages", style: "DANGER",
                confirm: "Delete 3 messages?", enabled: true,
            },
        ]);
    });

    test("a typed value is the value; a secret is left out", () => {
        const { tree, dom } = screen({ q: "rechnung" });
        const { node } = buildSnapshot(tree, {}, dom, "b");
        const fields = node.children[1].fields;
        assert.deepEqual(fields[0], { id: "q", label: "Search", type: "TEXT", value: "rechnung" });
        assert.deepEqual(fields[1], { id: "pw", label: "Password", type: "PASSWORD", omitted: true });
        // A hidden CSRF token is a secret whatever its field type says.
        assert.deepEqual(fields[2], { id: "_csrf", label: "", type: "HIDDEN", omitted: true });
    });

    test("a disabled button reports itself disabled, whatever the model said", () => {
        const { tree, dom } = screen({}, { search: { id: "search", disabled: "" } });
        const { node } = buildSnapshot(tree, {}, dom, "b");
        assert.equal(node.children[1].actions[0].enabled, false);
    });

    test("root takes one subtree", () => {
        const { tree, dom } = screen();
        const { node } = buildSnapshot(tree, { root: "email-list" }, dom, "b");
        assert.equal(node.id, "email-list");
        assert.equal(node.items.length, 1);
    });

    test("an unknown root says so instead of guessing", () => {
        const { tree, dom } = screen();
        const shot = buildSnapshot(tree, { root: "nope" }, dom, "b");
        assert.equal(shot.node, null);
        assert.equal(shot.reason, "unknown-root");
    });

    test("depth stops the walk and says where", () => {
        const { tree, dom } = screen();
        const { node, truncated } = buildSnapshot(tree, { depth: 1 }, dom, "b");
        assert.equal(node.children[0].id, "email-list");
        assert.equal(node.children[0].items, undefined);
        assert.equal(node.children[0].truncated, true);
        assert.equal(truncated, true);
    });

    test("maxChars is kept, and the cut is visible", () => {
        const { tree, dom } = screen({ q: "rechnung" });
        const shot = buildSnapshot(tree, { maxChars: 200 }, dom, "b");
        assert.equal(shot.truncated, true);
        assert.ok(JSON.stringify(shot).length <= 200,
            `snapshot was ${JSON.stringify(shot).length} chars`);
    });

    test("full mode carries the model, secrets still withheld", () => {
        const { tree, dom } = screen({ q: "rechnung" });
        const { node } = buildSnapshot(tree, { mode: "full", root: "search-form" }, dom, "b");
        assert.equal(node.type, "form");
        // Every model field is there — a trigger, a style, whatever the node had.
        assert.equal(node.actions[1].confirm, "Delete 3 messages?");
        assert.equal(node.fields[0].value, "rechnung");
        assert.equal(node.fields[1].value, undefined);
        assert.equal(node.fields[1].omitted, true);
    });

    test("nothing rendered yet is a reason, not a crash", () => {
        const shot = buildSnapshot(null, {}, { byId: () => null, values: () => ({}) }, "b");
        assert.equal(shot.node, null);
        assert.equal(shot.reason, "no-tree");
    });
});
