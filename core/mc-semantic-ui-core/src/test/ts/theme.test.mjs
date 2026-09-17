/**
 * The theme picker's state: which theme is on, what putting one on does to
 * <html>, and that the remembered choice survives only while the list knows it.
 *
 * Runs against the compiled output in target/ts-dist, with a DOM stubbed just
 * far enough for theme.ts: a class list on <html> and a localStorage. No jsdom,
 * same as the tests next door.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let applyTheme, currentTheme, SUI_THEMES;

function fakeStorage() {
    const store = new Map();
    return {
        getItem: k => (store.has(k) ? store.get(k) : null),
        setItem: (k, v) => store.set(k, String(v)),
    };
}

function fakeRoot(initial = []) {
    const classes = new Set(initial);
    return {
        classes,
        classList: {
            add: c => classes.add(c),
            remove: c => classes.delete(c),
            [Symbol.iterator]: () => classes.values(),
        },
    };
}

describe("themes", () => {
    before(async () => {
        ({ applyTheme, currentTheme, SUI_THEMES } = await import(path.join(DIST, "theme.js")));
    });

    beforeEach(() => {
        globalThis.localStorage = fakeStorage();
        globalThis.document = { documentElement: fakeRoot() };
    });

    test("nothing remembered means the default, or the one the app names", () => {
        assert.equal(currentTheme(), "default");
        assert.equal(currentTheme({ defaultTheme: "amethyst" }), "amethyst");
    });

    test("putting a theme on swaps the class and remembers it", () => {
        document.documentElement = fakeRoot(["sui-theme-clody", "keep-me"]);

        applyTheme("amethyst");

        assert.deepEqual([...document.documentElement.classes].sort(), ["keep-me", "sui-theme-amethyst"]);
        assert.equal(currentTheme(), "amethyst");
    });

    test("the default is no theme class at all", () => {
        document.documentElement = fakeRoot(["sui-theme-sorbet"]);

        applyTheme("default");

        assert.deepEqual([...document.documentElement.classes], []);
        assert.equal(localStorage.getItem("sui-theme"), "default");
    });

    test("a remembered name the list does not know falls back", () => {
        localStorage.setItem("sui-theme", "retired-theme");
        assert.equal(currentTheme({ defaultTheme: "gipiti" }), "gipiti");
    });

    test("every shipped theme but the default has a stylesheet of its name", async () => {
        const { existsSync } = await import("node:fs");
        const resources = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../main/resources");
        for (const { id } of SUI_THEMES.filter(t => t.id !== "default")) {
            assert.ok(existsSync(path.join(resources, `sui-${id}.css`)), `sui-${id}.css`);
        }
    });
});
