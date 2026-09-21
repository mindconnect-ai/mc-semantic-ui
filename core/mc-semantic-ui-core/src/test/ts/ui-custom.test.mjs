/**
 * A plugin's node (UiCustom on the server): a registered renderer gets it
 * flat — also when it arrives in a patch — and without one the node is a
 * placeholder that the rest of the page renders around, replaced once the
 * renderer is registered. Runs against the compiled output with a DOM stubbed
 * just far enough, like merge-patch.test.mjs.
 */
import { test, describe, before, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

/** A container that takes appended children; `html` is what it holds. */
function fakeHost() {
    return {
        children: [], innerHTML: "", outerHTML: "",
        get html() { return this.children.map(c => c.outerHTML).join(""); },
        // As in a DOM: appending moves the child out of where it was.
        appendChild(child) { child.parent?.children.splice(child.parent.children.indexOf(child), 1); this.children.push(child); },
        hasAttribute: () => false, closest: () => null, parentElement: null,
        scrollTop: 0, scrollHeight: 0, clientHeight: 0,
        querySelectorAll: () => [], querySelector: () => null, remove() { },
    };
}

/** Enough of document.createElement("div") for an APPEND: its innerHTML becomes one child. */
function fakeScratch() {
    return {
        children: [],
        set innerHTML(html) { this.children = html ? [{ nodeType: 1, outerHTML: html, parent: this }] : []; },
        get firstChild() { return this.children[0] ?? null; },
    };
}

describe("UiCustom in the browser", () => {
    let createDefaultRenderer, renderMissing, warnings, host, morphs;
    const realWarn = console.warn;

    before(async () => {
        ({ createDefaultRenderer, renderMissing } = await import(`${DIST}/renderer.js`));
    });

    beforeEach(() => {
        warnings = [];
        morphs = [];
        console.warn = (...args) => warnings.push(args.join(" "));
        host = fakeHost();
        globalThis.document = { getElementById: () => host, createElement: () => fakeScratch() };
        globalThis.Node = { ELEMENT_NODE: 1 };
        globalThis.requestAnimationFrame = () => 0;
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible" });
    });

    afterEach(() => {
        console.warn = realWarn;
        for (const g of ["document", "Node", "requestAnimationFrame", "getComputedStyle"]) delete globalThis[g];
    });

    /** A renderer whose DOM writes are recorded instead of morphed. */
    function renderer() {
        const r = createDefaultRenderer();
        r.setMorpher((target, html, mode) => morphs.push({ target, html, mode }));
        return r;
    }

    const xDemo = node => `<b id="${node.id}" class="x-demo">${node.a}:${node.list.join("+")}</b>`;

    test("a registered renderer receives the node flat", () => {
        const r = renderer();
        let received;
        r.register("x-demo", node => { received = node; return xDemo(node); });
        const html = r.render({ type: "stack", id: "s", children: [{ type: "x-demo", id: "w", a: 1, list: ["p", "q"] }] });
        assert.deepEqual(received, { type: "x-demo", id: "w", a: 1, list: ["p", "q"] });
        assert.match(html, /<b id="w" class="x-demo">1:p\+q<\/b>/);
        assert.deepEqual(warnings, []);
    });

    test("without a renderer: a placeholder, the rest of the page intact, one warning naming the type", () => {
        const r = renderer();
        const page = { type: "stack", id: "s", children: [
            { type: "text", id: "before", text: "Before" },
            { type: "x-demo", id: "w", a: 1, secret: "not on the page" },
            { type: "x-demo", id: "w2" },
            { type: "text", id: "after", text: "After" },
        ] };
        const html = r.render(page);
        assert.match(html, /<div id="w" class="sui-custom-missing" data-type="x-demo"><\/div>/);
        assert.match(html, /Before/);
        assert.match(html, /After/);
        assert.doesNotMatch(html, /not on the page|<pre>/);
        assert.equal(warnings.length, 1, warnings.join("\n"));
        assert.match(warnings[0], /"x-demo"/);
    });

    test("the placeholder is the server's, character for character", () => {
        // UiCustomRenderTest asserts the same string for SuiServerRenderer.
        assert.equal(renderMissing({ type: "x-demo", id: 'x"1', a: 1 }),
            '<div id="x&quot;1" class="sui-custom-missing" data-type="x-demo"></div>');
    });

    test("arriving in a patch — REPLACE and APPEND — it is rendered by its renderer", () => {
        const r = renderer();
        r.register("x-demo", xDemo);
        r.applyPatch({ patches: [{ op: "REPLACE", targetId: "slot", node: { type: "x-demo", id: "slot", a: 2, list: ["r"] } }] });
        assert.equal(morphs.length, 1);
        assert.equal(morphs[0].html, '<b id="slot" class="x-demo">2:r</b>');

        r.applyPatch({ patches: [{ op: "APPEND", targetId: "feed", node: { type: "x-demo", id: "n", a: 3, list: [] } }] });
        assert.equal(host.html, '<b id="n" class="x-demo">3:</b>');
    });

    test("an APPEND of an unknown type appends the placeholder", () => {
        const r = renderer();
        r.applyPatch({ patches: [{ op: "APPEND", targetId: "feed", node: { type: "x-late", id: "n" } }] });
        assert.equal(host.html, '<div id="n" class="sui-custom-missing" data-type="x-late"></div>');
    });

    test("the placeholder becomes the real node once the renderer is registered", () => {
        const r = renderer();
        // Seeded as a server-rendered page's model would be, or drawn before the plugin.
        r.seedModels({ type: "stack", id: "s", children: [{ type: "x-demo", id: "w", a: 5, list: ["z"] }] });
        const placeholder = { id: "w", getAttribute: name => (name === "data-type" ? "x-demo" : null) };
        const other = { id: "o", getAttribute: () => "x-other" };
        const scope = { querySelectorAll: () => [placeholder, other] };

        assert.equal(r.upgradePlaceholders(scope), 0, "nothing to draw it with yet");
        r.register("x-demo", xDemo);
        assert.equal(r.upgradePlaceholders(scope), 1);
        assert.equal(morphs.at(-1).target, placeholder);
        assert.equal(morphs.at(-1).mode, "outerHTML");
        assert.equal(morphs.at(-1).html, '<b id="w" class="x-demo">5:z</b>');
    });

    test("registering redraws the placeholders in the attached root", () => {
        const placeholder = { id: "w", getAttribute: () => "x-demo" };
        const root = { ...fakeHost(), querySelectorAll: () => [placeholder] };
        const r = renderer();
        r.attach(root);
        r.seedModels({ type: "x-demo", id: "w", a: 7, list: [] });
        r.register("x-demo", xDemo);
        assert.equal(morphs.at(-1).html, '<b id="w" class="x-demo">7:</b>');
    });
});
