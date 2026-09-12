/**
 * An expanded SELECT / MULTISELECT shows every option at once.
 *
 * The markup is locked against the same fixtures SuiServerRendererTest renders
 * through field.hbs (src/test/resources/fixtures/choice-fields.json), so SSR
 * and SPA cannot drift apart. The ordering rule the EventBus applies to rows
 * (seatAfterToggle) is locked against fixtures the JavaFX renderer checks too.
 * The DOM side of the EventBus — harvest, focus, the move clicks — needs a
 * browser and is not covered here.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const FIXTURES = path.resolve(HERE, "../resources/fixtures");

const fields = JSON.parse(fs.readFileSync(path.join(FIXTURES, "choice-fields.json"), "utf8"));
const seats = JSON.parse(fs.readFileSync(path.join(FIXTURES, "choice-seats.json"), "utf8"));

/** The choice group's outer <div>, with icons reduced to <svg/> (their sprite URLs differ per renderer). */
function choiceGroup(html) {
    const start = html.indexOf('<div class="sui-choice-group');
    assert.ok(start >= 0, html);
    let depth = 0;
    for (const m of html.slice(start).matchAll(/<div\b|<\/div>/g)) {
        depth += m[0] === "<div" ? 1 : -1;
        if (depth === 0) {
            return html.slice(start, start + m.index + m[0].length).replace(/<svg[\s\S]*?<\/svg>/g, "<svg/>");
        }
    }
    assert.fail("unclosed choice group: " + html);
}

/** The values of the options marked selected in a field's dropdown (whitespace inside <option> differs per renderer). */
function selectedOptions(html, id) {
    const select = html.match(new RegExp(`<select id="${id}__input"[\\s\\S]*?</select>`));
    assert.ok(select, html);
    return [...select[0].matchAll(/<option value="([^"]*)"\s*selected/g)].map(m => m[1]);
}

let render;
let choices;

describe("expanded choice fields", () => {
    before(async () => {
        const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);
        const renderer = createDefaultRenderer();
        render = node => renderer.render(node);
        choices = await import(`${DIST}/renderers/choices.js`);
    });

    for (const f of fields) {
        test(`matches field.hbs: ${f.name}`, () => {
            const html = render(f.node);
            if (f.html !== undefined) assert.equal(choiceGroup(html), f.html);
            else assert.deepEqual(selectedOptions(html, f.node.id), f.selected);
        });
    }

    test("an icon does not wrap an expanded group", () => {
        const html = render({ ...fields[0].node, icon: "search" });
        assert.doesNotMatch(html, /sui-input-icon/);
        assert.match(render({ ...fields[0].node, icon: "search", expanded: false }), /sui-input-icon/);
    });

    test("the caption labels the group by id, not a control by for", () => {
        const html = render(fields[0].node);
        assert.match(html, /<label id="size__label">Size<\/label>/);
        assert.doesNotMatch(html, /for="size__input"/);
    });

    test("not expanded stays a select whose caption points at it", () => {
        const html = render({ ...fields[0].node, expanded: false });
        assert.match(html, /<select id="size__input"/);
        assert.match(html, /<label for="size__input">/);
    });

    for (const s of seats) {
        test(`seatAfterToggle: ${s.name}`, () => {
            const rows = s.checked.map((checked, i) => ({ checked, index: s.index[i] }));
            assert.equal(choices.seatAfterToggle(rows, s.at), s.seat);
        });
    }

    test("selectedValues: arrays, comma strings, blanks and numbers", () => {
        assert.deepEqual(choices.selectedValues(["a", null, 2]), ["a", "2"]);
        assert.deepEqual(choices.selectedValues(" a , b "), ["a", "b"]);
        assert.deepEqual(choices.selectedValues("   "), []);
        assert.deepEqual(choices.selectedValues(null), []);
        assert.deepEqual(choices.selectedValues(0), ["0"]);
        assert.deepEqual(choices.selectedValues("a,"), ["a", ""]);
    });
});
