/**
 * A list's rows are nodes: a patch that names an item, its summary or one of
 * the buttons around it lands in the copy of the tree as well as on the
 * screen. What mindconnect's migrations screen sends after "Apply" — a REMOVE
 * of an item deep in a group, a REPLACE of the group's counter, a MERGE on the
 * header button — and what a snapshot then says.
 *
 * Against the compiled output and the stub DOM (support/mini-dom.mjs), the
 * way snapshot.test.mjs checks the copy: every id the patches name has an
 * element, and the fallback morpher writes what it rendered onto it.
 */
import { test, describe, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

installControls();
const { SuiRenderer, installDefaultHandlers } = await import(`${DIST}/renderer.js`);

/** The migrations screen: a list of groups, each a collapsible item holding a list of items, and a button in the header. */
function migrationsPage() {
    return {
        type: "stack", id: "shell", children: [
            {
                type: "list", id: "migrations", title: "Migrations",
                actions: [{ type: "action", id: "apply-all", label: "Apply all", style: "PRIMARY", onClick: { url: "/apply", method: "POST" } }],
                items: [
                    {
                        id: "group-agent", label: "Agents",
                        collapseSummary: "Agents  (2)", collapseSummaryId: "group-agent-sum", collapseOpen: true,
                        content: {
                            type: "list", id: "group-agent-list",
                            items: [
                                { id: "agent:title-generator", label: "title-generator",
                                  actions: [{ type: "action", id: "fix-title", label: "Fix", onClick: { url: "/fix/title", method: "POST" } }] },
                                { id: "agent:summarizer", label: "summarizer" },
                            ],
                        },
                    },
                ],
            },
            {
                type: "table", id: "orders",
                columns: [{ type: "column", id: "status", label: "Status" }],
                rows: [{ type: "row", id: "order-1", data: { status: "open" } }],
            },
        ],
    };
}

/** Elements for every id the patches will name, and a document that finds them. */
function pageWithDom() {
    const elements = new Map();
    const root = new El("div", { id: "root" });
    const add = (tag, id, parent = root) => {
        const el = new El(tag, { id });
        parent.appendChild(el);
        elements.set(id, el);
        return el;
    };
    add("div", "shell");
    const list = add("div", "migrations");
    add("button", "apply-all", list);
    const group = add("li", "group-agent", list);
    add("span", "group-agent-sum", group);
    const inner = add("div", "group-agent-list", group);
    const first = add("li", "agent:title-generator", inner);
    add("button", "fix-title", first);
    add("li", "agent:summarizer", inner);
    const table = add("div", "orders");
    table.setAttribute("data-sui", "table");
    add("tr", "order-1", table);
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
    return { root, elements };
}

describe("patches that name a list's rows", () => {
    let renderer, elements, warnings;
    const realWarn = console.warn;

    before(() => { console.warn = (...args) => warnings.push(args.join(" ")); });
    after(() => { console.warn = realWarn; });

    beforeEach(() => {
        warnings = [];
        ({ elements } = pageWithDom());
        renderer = new SuiRenderer(document.body);
        installDefaultHandlers(renderer);
        renderer.animatePatches = false;
        renderer.mount(migrationsPage());
        // The table's element carries its model, as renderTable writes it.
        elements.get("orders").setAttribute("data-node", JSON.stringify(renderer.nodeById("orders")));
    });

    const patch = (...patches) => renderer.applyPatch({ patches });
    const group = () => renderer.nodeById("group-agent");
    const noneAdrift = () => assert.deepEqual(warnings.filter(w => w.includes("tree copy")), []);

    test("a row is a node of type item in the copy, whatever the server wrote", () => {
        assert.equal(group().type, "item");
        assert.equal(renderer.nodeById("agent:summarizer").type, "item");
        // The summary with an id is a text node with that id.
        assert.deepEqual(renderer.nodeById("group-agent-sum"), { type: "text", id: "group-agent-sum", text: "Agents  (2)" });
    });

    test("REMOVE of an item deep in a group leaves the screen and the copy", () => {
        patch({ op: "REMOVE", targetId: "agent:title-generator" });
        assert.equal(elements.get("agent:title-generator").parentElement, null);
        assert.deepEqual(group().content.items.map(i => i.id), ["agent:summarizer"]);
        assert.equal(renderer.nodeById("agent:title-generator"), undefined);
        // Its button went with it: nothing below a removed row is on the page.
        assert.equal(renderer.nodeById("fix-title"), undefined);
        noneAdrift();
    });

    test("REMOVE of the last item, then of the group", () => {
        patch({ op: "REMOVE", targetId: "agent:title-generator" }, { op: "REMOVE", targetId: "agent:summarizer" });
        assert.deepEqual(group().content.items, []);
        patch({ op: "REMOVE", targetId: "group-agent" });
        assert.equal(elements.get("group-agent").parentElement, null);
        assert.deepEqual(renderer.nodeById("migrations").items, []);
        assert.equal(group(), undefined);
        assert.equal(renderer.nodeById("group-agent-sum"), undefined);
        noneAdrift();
    });

    test("REPLACE on the summary's id with a text", () => {
        patch({ op: "REPLACE", targetId: "group-agent-sum", node: { type: "text", id: "group-agent-sum", text: "Agents  (1)" } });
        assert.match(elements.get("group-agent-sum").outerHTML, /class="sui-text" id="group-agent-sum">Agents {2}\(1\)</);
        assert.equal(group().collapseSummaryNode.text, "Agents  (1)");
        assert.equal(renderer.nodeById("group-agent-sum").text, "Agents  (1)");
        noneAdrift();
    });

    test("MERGE on the summary's id changes its words", () => {
        patch({ op: "MERGE", targetId: "group-agent-sum", attributes: { text: "Agents  (0)" } });
        assert.match(elements.get("group-agent-sum").outerHTML, /Agents {2}\(0\)/);
        assert.equal(group().collapseSummaryNode.text, "Agents  (0)");
        noneAdrift();
    });

    test("MERGE on a list's header button and on an item's button", () => {
        patch({ op: "MERGE", targetId: "apply-all", attributes: { label: "Apply 1 migration", enabled: false } });
        const button = elements.get("apply-all").outerHTML;
        assert.match(button, /Apply 1 migration/);
        assert.match(button, /disabled/);
        assert.equal(renderer.nodeById("migrations").actions[0].label, "Apply 1 migration");
        // The trigger was never mentioned, so it is still there.
        assert.match(button, /data-trigger=/);

        patch({ op: "MERGE", targetId: "fix-title", attributes: { loading: true } });
        assert.match(elements.get("fix-title").outerHTML, /is-loading/);
        assert.equal(group().content.items[0].actions[0].loading, true);
        noneAdrift();
    });

    test("MERGE on an item itself", () => {
        patch({ op: "MERGE", targetId: "agent:summarizer", attributes: { label: "summarizer (fixed)", description: "done" } });
        assert.match(elements.get("agent:summarizer").outerHTML, /summarizer \(fixed\)/);
        assert.equal(group().content.items[1].description, "done");
        noneAdrift();
    });

    test("REPLACE of an item with an item", () => {
        patch({ op: "REPLACE", targetId: "agent:summarizer", node: { type: "item", id: "agent:summarizer", label: "summarizer v2" } });
        assert.match(elements.get("agent:summarizer").outerHTML, /^<li class="sui-list-item" id="agent:summarizer"/);
        assert.equal(group().content.items[1].label, "summarizer v2");
        noneAdrift();
    });

    test("a table's rows are rows, and a patch on an item leaves them alone", () => {
        patch({ op: "REMOVE", targetId: "agent:title-generator" });
        const table = renderer.nodeById("orders");
        assert.equal(table.rows[0].type, "row");
        assert.deepEqual(table.rows[0].data, { status: "open" });
        // And the other way round: a row goes through the table's own path.
        patch({ op: "REMOVE", targetId: "order-1" });
        assert.deepEqual(renderer.nodeById("orders").rows, []);
        assert.deepEqual(group().content.items.map(i => i.id), ["agent:summarizer"]);
        noneAdrift();
    });

    test("the snapshot after the patches shows the screen as it is", async () => {
        const { buildSnapshot } = await import(`${DIST}/snapshot.js`);
        patch(
            { op: "REMOVE", targetId: "agent:title-generator" },
            { op: "MERGE", targetId: "group-agent-sum", attributes: { text: "Agents  (1)" } },
            { op: "MERGE", targetId: "apply-all", attributes: { label: "Apply 1 migration" } },
        );
        const dom = { byId: () => null, values: () => ({}) };
        const shot = buildSnapshot(renderer.tree(), { root: "migrations" }, { dom, busId: "t", outlineFor: t => renderer.outlineFor(t) });
        const list = shot.node;
        assert.equal(list.actions[0].label, "Apply 1 migration");
        const agents = list.items[0];
        assert.equal(agents.id, "group-agent");
        const words = JSON.stringify(agents);
        assert.match(words, /Agents {2}\(1\)/);
        assert.doesNotMatch(words, /title-generator/);
        assert.match(words, /summarizer/);
    });
});
