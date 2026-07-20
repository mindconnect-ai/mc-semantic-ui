/**
 * The renderer's string API must work with no DOM and no network.
 *
 * This is the server-side-rendering contract: a Node backend imports the same
 * renderer the browser uses and calls render() to produce HTML. That path has
 * no DOM to morph, so it must not reach for Idiomorph — an outbound request to
 * a third-party CDN from a backend process, on every construction.
 *
 * Runs against the compiled output in target/ts-dist, so it also proves the
 * shipped artifact is importable from plain Node (ESM, no bundler, no shims).
 */
import { test, describe, before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let createDefaultRenderer;
let warnings = [];
const realWarn = console.warn;

/**
 * The Idiomorph load is a dynamic import of an https: URL. Node's ESM loader
 * refuses that scheme, so the attempt always fails — and the failure is logged.
 * A captured warning is therefore a reliable "the load was attempted" signal.
 */
function loadWasAttempted() {
    return warnings.some(w => String(w).includes("Idiomorph"));
}

/** Enough of an element for innerHtmlMorpher, which only assigns innerHTML. */
function fakeElement() {
    return { innerHTML: "", outerHTML: "", hasAttribute: () => false };
}

describe("SuiRenderer offline contract", () => {
    before(async () => {
        // Any read of a browser global is a bug for this use case — make it throw.
        for (const g of ["document", "window", "navigator", "localStorage"]) {
            Object.defineProperty(globalThis, g, {
                get() { throw new Error(`SuiRenderer touched browser global "${g}"`); },
                configurable: true,
            });
        }
        ({ createDefaultRenderer } = await import(`${DIST}/renderer.js`));
    });

    beforeEach(() => {
        warnings = [];
        console.warn = (...args) => warnings.push(args.join(" "));
    });

    after(() => { console.warn = realWarn; });

    test("constructing and rendering touches no network", async () => {
        const html = createDefaultRenderer().render({
            type: "stack", id: "root", children: [
                { type: "text", id: "t", text: "Orders" },
                {
                    type: "table", id: "orders",
                    columns: [{ type: "column", id: "c", label: "Customer", dataKey: "customer" }],
                    rows: [{ type: "row", id: "r", data: { customer: "Ada <&> Co" } }],
                },
            ],
        });

        // Give a would-be import a turn of the event loop to fail and log.
        await new Promise(r => setTimeout(r, 50));

        assert.equal(loadWasAttempted(), false,
            "render-only use must not attempt the Idiomorph CDN load");
        assert.ok(html.includes("<table"), "expected real markup");
        assert.ok(!html.includes("<pre>"), "expected no unknown-type fallback dump");
        assert.ok(html.includes("Ada &lt;&amp;&gt; Co"), "expected HTML escaping");
    });

    test("a DOM write does trigger the load", async () => {
        // The other half of the contract: the browser path is unchanged.
        const r = createDefaultRenderer();
        r.attach(fakeElement());
        r.mount({ type: "text", id: "t", text: "hi" });

        await new Promise(r => setTimeout(r, 50));

        assert.equal(loadWasAttempted(), true,
            "mount() must still kick off the Idiomorph load");
    });

    test("setMorpher suppresses the load entirely", async () => {
        const r = createDefaultRenderer();
        r.setMorpher((target, content) => { target.innerHTML = content; });
        r.attach(fakeElement());
        r.mount({ type: "text", id: "t", text: "hi" });

        await new Promise(r => setTimeout(r, 50));

        assert.equal(loadWasAttempted(), false,
            "an explicitly chosen morpher must cancel the CDN load");
    });
});
