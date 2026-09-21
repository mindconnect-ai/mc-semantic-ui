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
        assert.match(html, /<input type="time" id="from__input" name="from" value="08:30" min="06:00" max="20:00" step="900" list="from__input__list">/);
    });

    test("a step of five minutes or more brings a datalist of the times it allows", async () => {
        const { timeOptions } = await import(`${DIST}/renderers/field.js`);
        assert.deepEqual(timeOptions({ step: "900", min: "08:00", max: "09:00" }), ["08:00", "08:15", "08:30", "08:45", "09:00"]);
        assert.equal(timeOptions({ step: "300" }).length, 288);
        assert.deepEqual(timeOptions({ step: "60" }), []);      // every minute: the picker does that itself
        assert.deepEqual(timeOptions({}), []);
        const html = renderField({ type: "field", id: "t", label: "T", fieldType: "TIME", editable: true, value: "08:30", min: "08:00", max: "08:30", step: "900" });
        assert.match(html, /<input type="time" id="t__input" name="t" value="08:30" min="08:00" max="08:30" step="900" list="t__input__list">/);
        assert.match(html, /<datalist id="t__input__list"><option value="08:00"><\/option><option value="08:15"><\/option><option value="08:30"><\/option><\/datalist>/);
    });

    test("a typed time is rounded to the nearest step", async () => {
        const { snapTimeValue } = await import(`${DIST}/renderers/field.js`);
        assert.equal(snapTimeValue("09:07", 900), "09:00");
        assert.equal(snapTimeValue("09:08", 900), "09:15");
        assert.equal(snapTimeValue("09:03", 300), "09:05");
        assert.equal(snapTimeValue("23:59", 600), "23:50");   // never past the day
        assert.equal(snapTimeValue("09:07", 60), "09:07");
        assert.equal(snapTimeValue("", 900), "");
        assert.equal(snapTimeValue("nope", 900), "nope");
    });

    test("read-only: the value as text", () => {
        assert.match(renderField({ type: "field", id: "from", label: "From", fieldType: "TIME", value: "08:30" }), /<span class="sui-value">08:30<\/span>/);
    });
});
