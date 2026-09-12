/**
 * An expanded SELECT / MULTISELECT shows every option at once.
 *
 * The SSR side is locked by SuiServerRendererTest; this holds the SPA renderer
 * to the same markup rules. The value harvest and the reordering live in the
 * EventBus and need a DOM, so they are not covered here.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

const SIZES = [
    { value: "s", label: "Small" },
    { value: "m", label: "Medium" },
    { value: "l", label: "Large" },
];

let render;

describe("expanded choice fields", () => {
    before(async () => {
        const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);
        const renderer = createDefaultRenderer();
        render = node => renderer.render(node);
    });

    test("SELECT asRadio renders a radio per option, the value checked", () => {
        const html = render({ type: "field", id: "size", label: "Size", fieldType: "SELECT",
            editable: true, expanded: true, value: "m", options: SIZES });
        assert.doesNotMatch(html, /<select/);
        assert.match(html, /role="radiogroup"/);
        assert.equal(html.match(/type="radio" name="size"/g).length, 3);
        assert.match(html, /value="m" data-sui-type="SELECT" checked>/);
        assert.match(html, /value="s" data-sui-type="SELECT">/);
    });

    test("MULTISELECT asCheckboxes keeps option order and has no move buttons", () => {
        const html = render({ type: "field", id: "tags", label: "Tags", fieldType: "MULTISELECT",
            editable: true, expanded: true, value: ["l", "s"], options: SIZES });
        assert.doesNotMatch(html, /<select/);
        assert.match(html, /value="s" data-sui-type="MULTISELECT" checked>/);
        assert.match(html, /value="m" data-sui-type="MULTISELECT">/);
        assert.ok(html.indexOf('value="s"') < html.indexOf('value="l"'));
        assert.doesNotMatch(html, /data-sui-move/);
    });

    test("orderable leads with the checked options in value order", () => {
        const html = render({ type: "field", id: "tags", label: "Tags", fieldType: "MULTISELECT",
            editable: true, expanded: true, orderable: true, value: "l,s", options: SIZES });
        assert.match(html, /sui-choice-group--orderable/);
        const l = html.indexOf('value="l"'), s = html.indexOf('value="s"'), m = html.indexOf('value="m"');
        assert.ok(l < s && s < m, html);
        assert.equal(html.match(/data-sui-move="up"/g).length, 3);
    });

    test("not expanded stays a select", () => {
        const html = render({ type: "field", id: "size", label: "Size", fieldType: "SELECT",
            editable: true, value: "m", options: SIZES });
        assert.match(html, /<select/);
    });
});
