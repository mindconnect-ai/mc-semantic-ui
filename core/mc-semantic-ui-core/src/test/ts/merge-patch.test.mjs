/**
 * MERGE: change a few fields of a node and leave the rest alone.
 *
 * REPLACE needs the whole node, so flipping one flag means the server
 * rebuilding and resending a subtree it did not otherwise touch. MERGE says
 * only what changed — which means the client has to know what the rest was,
 * and these tests are about where it gets that from.
 *
 * Runs against the compiled output in target/ts-dist, with a DOM stubbed just
 * far enough for the patch dispatcher: no jsdom, same as the offline test
 * next door.
 */
import { test, describe, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let createDefaultRenderer;
const realWarn = console.warn;
let warnings = [];

/** Enough of an element for the patch dispatcher and the fallback morpher. */
function fakeElement() {
    return {
        innerHTML: "",
        outerHTML: "",
        hasAttribute: () => false,
        closest: () => null,
        parentElement: null,
        scrollTop: 0,
        scrollHeight: 0,
        clientHeight: 0,
        querySelectorAll: () => [],
        querySelector: () => null,
        remove: () => { },
    };
}

describe("MERGE", () => {
    let element;

    before(async () => {
        ({ createDefaultRenderer } = await import(path.join(DIST, "renderer.js")));
    });

    beforeEach(() => {
        element = fakeElement();
        warnings = [];
        console.warn = (...args) => warnings.push(args.join(" "));
        globalThis.document = { getElementById: () => element };
        globalThis.requestAnimationFrame = () => 0;
        // The dispatcher samples the scroll container around a swap so a chat
        // that was at the bottom stays there; nothing here scrolls.
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible" });
    });

    after(() => {
        console.warn = realWarn;
        delete globalThis.document;
        delete globalThis.requestAnimationFrame;
        delete globalThis.getComputedStyle;
    });

    function merge(renderer, targetId, attributes) {
        renderer.applyPatch({ patches: [{ op: "MERGE", targetId, attributes }] });
    }

    test("changes the named field and keeps the rest", () => {
        const renderer = createDefaultRenderer();
        // Rendering is what teaches the renderer what this id is.
        renderer.render({ type: "text", id: "greeting", text: "hello", cssClass: "shout" });

        merge(renderer, "greeting", { text: "goodbye" });

        assert.match(element.outerHTML, /goodbye/);
        // The class was never mentioned, so it is still there — that is the
        // whole point of the operation.
        assert.match(element.outerHTML, /shout/);
    });

    test("hides a node without the server resending it", () => {
        const renderer = createDefaultRenderer();
        renderer.render({ type: "text", id: "filters", text: "Filters" });

        merge(renderer, "filters", { display: "HIDDEN" });

        // display folds into the css class, so this is what hidden looks like.
        assert.match(element.outerHTML, /sui-hidden/);
    });

    test("an explicit null clears a field rather than being ignored", () => {
        const renderer = createDefaultRenderer();
        renderer.render({ type: "text", id: "filters", text: "Filters", display: "HIDDEN" });

        merge(renderer, "filters", { display: null });

        // Being able to hide something is only half a feature.
        assert.doesNotMatch(element.outerHTML, /sui-hidden/);
    });

    test("merges accumulate", () => {
        const renderer = createDefaultRenderer();
        renderer.render({ type: "text", id: "t", text: "one" });

        merge(renderer, "t", { text: "two" });
        merge(renderer, "t", { cssClass: "loud" });

        // The second merge starts from the first one's result, not from the
        // node as it first arrived.
        assert.match(element.outerHTML, /two/);
        assert.match(element.outerHTML, /loud/);
    });

    test("a target the renderer has never drawn says so", () => {
        const renderer = createDefaultRenderer();

        merge(renderer, "never-rendered", { text: "x" });

        // Quietly doing nothing would leave the author hunting a server bug.
        assert.equal(element.outerHTML, "");
        assert.ok(warnings.some(w => w.includes("MERGE target has no known model")));
    });

    test("seedModels takes a tree the renderer did not draw", () => {
        const renderer = createDefaultRenderer();
        // What a hybrid page hands over: finished HTML, plus its model.
        renderer.seedModels({
            type: "stack",
            id: "root",
            children: [{ type: "text", id: "greeting", text: "hello", cssClass: "shout" }],
        });

        merge(renderer, "greeting", { text: "goodbye" });

        assert.match(element.outerHTML, /goodbye/);
        assert.match(element.outerHTML, /shout/);
    });

    test("mounting a new page forgets the old one's ids", () => {
        const renderer = createDefaultRenderer();
        renderer.attach(fakeElement());
        renderer.mount({ type: "text", id: "greeting", text: "hello" });
        renderer.mount({ type: "text", id: "other", text: "world" });

        merge(renderer, "greeting", { text: "x" });

        // The id belonged to the page that is gone.
        assert.ok(warnings.some(w => w.includes("MERGE target has no known model")));
    });

    test("a class that merely contains the marker is left alone", () => {
        const renderer = createDefaultRenderer();
        // The marker is stripped so a cleared display cannot leave a stale
        // sui-hidden behind. It has to be stripped as a whole class, though:
        // a \\b-anchored regex matches inside "sui-hidden-x", because a hyphen
        // is a non-word character, and used to leave "-x" behind.
        const html = renderer.render({ type: "text", id: "t", text: "x",
                                       cssClass: "sui-hidden-x panel" });

        assert.match(html, /sui-hidden-x/);
        assert.match(html, /panel/);
    });

    test("the baked-in marker itself is still stripped", () => {
        const renderer = createDefaultRenderer();
        // What the server sends for a hidden node: display, plus the class it
        // folds display into. Clearing display has to clear both.
        renderer.render({ type: "text", id: "t", text: "x",
                          display: "HIDDEN", cssClass: "panel sui-hidden" });

        merge(renderer, "t", { display: null });

        assert.doesNotMatch(element.outerHTML, /sui-hidden/);
        assert.match(element.outerHTML, /panel/);
    });

    test("a merge on a table row stays a table row", () => {
        const renderer = createDefaultRenderer();
        const table = {
            type: "table", id: "orders",
            columns: [{ type: "column", id: "c1", dataKey: "who", label: "Who" }],
            rows: [{ type: "row", id: "row-42", data: { who: "Ada" } }],
        };
        // A hybrid page: the client drew none of this, it read the model out
        // of the page. That is what puts the *rows* in the index too.
        renderer.seedModels(table);

        const wrapper = fakeElement();
        wrapper.getAttribute = (name) =>
            name === "data-node" ? JSON.stringify(table) : null;
        const row = fakeElement();
        row.closest = (selector) =>
            selector === '[data-sui="table"][data-node]' ? wrapper : null;
        globalThis.document = { getElementById: () => row };

        merge(renderer, "row-42", { display: "HIDDEN" });

        // Without the table branch this went down the generic path, which
        // renders a lone row — and a row outside a table is a key/value <dl>
        // by design. The <tr> was replaced by a definition list and the cells
        // went with it.
        assert.doesNotMatch(row.outerHTML, /<dl/);
        assert.match(wrapper.outerHTML, /<table/);
        assert.match(wrapper.outerHTML, /Ada/);
    });

    test("a patch outside a real browser applies without animating", () => {
        // Animation is the one thing in the renderer that has to ask about the
        // environment it is in, and asking must not be what breaks it. This
        // suite is the environment that caught it: a stubbed document and no
        // window at all. The gate asked "is anything missing?" and let the
        // stub through, and the animation then reached for HTMLElement, which
        // is not defined here — so a REMOVE died with a ReferenceError instead
        // of removing anything.
        const renderer = createDefaultRenderer();
        let removed = 0;
        element.remove = () => { removed++; };
        renderer.render({ type: "text", id: "gone", text: "x" });

        renderer.applyPatch({ patches: [{ op: "REMOVE", targetId: "gone" }] });

        assert.equal(removed, 1, "the node still goes, it just does not fade on the way");
    });

    test("REMOVE forgets what the id was", () => {
        const renderer = createDefaultRenderer();
        renderer.render({ type: "text", id: "spinner", text: "thinking" });

        renderer.applyPatch({ patches: [{ op: "REMOVE", targetId: "spinner" }] });
        element.outerHTML = "";
        merge(renderer, "spinner", { text: "x" });

        assert.ok(warnings.some(w => w.includes("MERGE target has no known model")));
    });
});
