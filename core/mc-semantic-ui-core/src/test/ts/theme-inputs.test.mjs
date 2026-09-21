/**
 * A theme overlay loads after sui.css, so a rule of its for "every field
 * input" at the same specificity beats sui.css's checkbox and radio rules:
 * in clody it put the surface colour back over the checked fill (white tick
 * on white) and rounded the box into a circle. Every overlay must leave
 * checkboxes and radios out of such a rule. sui-sbb.css is exempt — it
 * replaces sui.css and draws its own controls.
 */
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";

const RESOURCES = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../main/resources");
const OVERLAYS = readdirSync(RESOURCES).filter(f => /^sui-.+\.css$/.test(f) && f !== "sui-sbb.css");

/** The selectors of every rule in a stylesheet, comments removed. */
function selectors(css) {
    const plain = css.replace(/\/\*[\s\S]*?\*\//g, "");
    return [...plain.matchAll(/([^{}]+)\{/g)].map(m => m[1].trim()).filter(s => !s.startsWith("@"));
}

// `.sui-field input` followed by nothing that narrows it to one type.
const BARE_INPUT = /\.sui-field input(?![\w:[-])/;

describe("theme overlays leave checkboxes and radios to sui.css", () => {
    for (const file of OVERLAYS) {
        test(file, () => {
            // A pseudo-element rule (::placeholder) styles no box, so it is harmless.
            const bare = selectors(readFileSync(path.join(RESOURCES, file), "utf8"))
                .filter(s => BARE_INPUT.test(s) && !s.includes("::"));
            assert.deepEqual(bare, [], `${file} styles every field input, checkboxes and radios included — ` +
                'narrow it with input:where(:not([type="checkbox"], [type="radio"]))');
        });
    }

    test("the check covers the themes with such a rule", () => {
        assert.ok(OVERLAYS.includes("sui-clody.css") && OVERLAYS.length >= 6, OVERLAYS.join(", "));
    });
});
