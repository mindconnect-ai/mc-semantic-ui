/**
 * Where a rail fly-out or tooltip is placed. The DOM side — opening on hover,
 * focus and tap, closing on leave, Escape and outside clicks — needs a browser
 * and is not covered here; this locks the geometry.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let flyoutPosition;
const viewport = { width: 1000, height: 600 };
const item = { top: 100, bottom: 140, left: 8, right: 52 };

describe("rail fly-out placement", () => {
    before(async () => {
        ({ flyoutPosition } = await import(`${DIST}/renderers/menu.js`));
    });

    test("beside the item, level with its top, past the gap", () => {
        assert.deepEqual(flyoutPosition(item, { width: 180, height: 90 }, viewport, "left"), { top: 100, left: 60 });
    });

    test("a right-side menu opens its fly-out to the left", () => {
        const right = { top: 100, bottom: 140, left: 948, right: 992 };
        assert.deepEqual(flyoutPosition(right, { width: 180, height: 90 }, viewport, "right"), { top: 100, left: 760 });
    });

    test("moved up when it would run past the bottom of the viewport", () => {
        const low = { top: 560, bottom: 600, left: 8, right: 52 };
        assert.deepEqual(flyoutPosition(low, { width: 180, height: 200 }, viewport, "left"), { top: 392, left: 60 });
    });

    test("never above the top margin, even when taller than the viewport", () => {
        assert.equal(flyoutPosition(item, { width: 180, height: 900 }, viewport, "left").top, 8);
    });

    test("a tooltip is centred on the item", () => {
        assert.deepEqual(flyoutPosition(item, { width: 80, height: 20 }, viewport, "left", "center"), { top: 110, left: 60 });
    });
});
