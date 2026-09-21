/**
 * The TIME field renders the browser's own time picker. Runs against the
 * compiled output in target/ts-dist.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

describe("TIME field", () => {
    let renderField;
    before(async () => { ({ renderField } = await import(`${DIST}/renderers/field.js`)); });

    test("editable: an <input type=time> with the value and bounds", () => {
        const html = renderField({ type: "field", id: "from", label: "From", fieldType: "TIME", editable: true, value: "08:30", min: "06:00", max: "20:00", step: "900" });
        assert.match(html, /<input type="time" id="from__input" name="from" value="08:30" min="06:00" max="20:00" step="900">/);
    });

    test("read-only: the value as text", () => {
        assert.match(renderField({ type: "field", id: "from", label: "From", fieldType: "TIME", value: "08:30" }), /<span class="sui-value">08:30<\/span>/);
    });
});
