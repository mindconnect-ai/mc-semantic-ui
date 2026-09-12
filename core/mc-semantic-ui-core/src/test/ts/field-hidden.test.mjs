/**
 * A HIDDEN field is a value the form submits without showing it.
 *
 * The SPA renderer has to emit exactly the input field.hbs does — the SSR test
 * locks that markup, this one holds the browser side to it.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let render;

describe("HIDDEN field", () => {
    before(async () => {
        const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);
        const renderer = createDefaultRenderer();
        render = node => renderer.render(node);
    });

    test("renders a bare hidden input, same as field.hbs", () => {
        const html = render({ type: "field", id: "orderId", fieldType: "HIDDEN", value: 4711 });
        assert.equal(html,
            '<input type="hidden" id="orderId" name="orderId" value="4711" data-sui-type="HIDDEN">');
    });

    test("submits whether or not it is editable, and escapes its value", () => {
        const html = render({ type: "field", id: "ctx", fieldType: "HIDDEN", value: '"><b>' });
        assert.match(html, /name="ctx"/);
        assert.doesNotMatch(html, /<b>/);
        assert.doesNotMatch(html, /sui-value/);
    });

    test("a form carries it without a label", () => {
        const html = render({
            type: "form", id: "order", title: "Order",
            fields: [
                { type: "field", id: "orderId", fieldType: "HIDDEN", value: "4711" },
                { type: "field", id: "note", label: "Note", fieldType: "TEXT", editable: true },
            ],
        });
        assert.match(html, /<input type="hidden" id="orderId" name="orderId" value="4711"/);
        assert.doesNotMatch(html, /for="orderId__input"/);
    });

    test("a visible field's wrapper carries cssClass and the display state, same as field.hbs", () => {
        const hidden = render({ type: "field", id: "note", label: "Note", fieldType: "TEXT", editable: true, display: "HIDDEN" });
        const styled = render({ type: "field", id: "note", label: "Note", fieldType: "TEXT", editable: true, cssClass: "wide" });
        assert.match(hidden, /class="sui-field sui-hidden "/);
        assert.match(styled, /class="sui-field wide "/);
    });

    test("a detail view gives it no row", () => {
        const html = render({
            type: "detail", id: "d", title: "Order",
            fields: [
                { type: "field", id: "orderId", fieldType: "HIDDEN", value: "4711" },
                { type: "field", id: "customer", label: "Customer", fieldType: "TEXT", value: "Ada" },
            ],
        });
        assert.match(html, /Customer/);
        assert.doesNotMatch(html, /4711/);
    });
});
