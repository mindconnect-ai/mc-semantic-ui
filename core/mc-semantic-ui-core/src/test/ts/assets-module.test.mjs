/**
 * The module the asset registry serves as /sui/assets.js, run for real.
 *
 * The template lives in src/main/resources/ai/mindconnect/ui/assets/; the
 * registry fills its asset list and serves it. Here the list is filled with
 * data: URLs — modules that install, one that does not load, one without
 * install(), one whose install() throws — and the module is imported from a
 * file. SuiAssetRegistryTest checks the Java side of the substitution.
 */
import { test, describe, before, after } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath, pathToFileURL } from "node:url";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TEMPLATE = path.resolve(HERE, "../../main/resources/ai/mindconnect/ui/assets/sui-assets.js");
const js = (source) => "data:text/javascript," + encodeURIComponent(source);

const ASSETS = [
    { id: "base.css", kind: "css", url: "/base.css" },
    { id: "first", kind: "extension", url: js(`export function install(r, o) { r.calls.push(["first", o.bus]); }`) },
    { id: "broken", kind: "extension", url: js(`throw new Error("does not load");`) },
    { id: "lib", kind: "module", url: js(`globalThis.__suiLib = (globalThis.__suiLib ?? 0) + 1; export function install() { throw new Error("a module is not installed"); }`) },
    { id: "noinstall", kind: "extension", url: js(`export const x = 1;`) },
    { id: "throws", kind: "extension", url: js(`export function install() { throw new Error("install failed"); }`) },
    { id: "async", kind: "extension", url: js(`export async function install(r) { await new Promise(t => setTimeout(t, 5)); r.calls.push(["async"]); }`) },
    { id: "last", kind: "extension", url: js(`export function install(r) { r.calls.push(["last"]); }`) },
    { id: "theme.css", kind: "css", url: "/theme.css" },
];

/** Just enough of a document for linkStyles(). */
function fakeDocument(presentIds) {
    const head = { children: [], appendChild(el) { this.children.push(el); setTimeout(() => el.onload && el.onload(), 1); } };
    return {
        head,
        querySelectorAll: () => presentIds.map(id => ({ getAttribute: () => id })),
        createElement: () => ({ attrs: {}, setAttribute(k, v) { this.attrs[k] = v; } }),
    };
}

describe("/sui/assets.js", () => {
    let dir, mod, errors;
    const realError = console.error;

    before(async () => {
        dir = mkdtempSync(path.join(tmpdir(), "sui-assets-"));
        const source = readFileSync(TEMPLATE, "utf8");
        assert.ok(source.includes("/*SUI_ASSETS*/[]"), "the template has the placeholder the registry fills");
        const file = path.join(dir, "assets.mjs");
        writeFileSync(file, source.replace("/*SUI_ASSETS*/[]", JSON.stringify(ASSETS)).replace("/*SUI_GENERATION*/0", "7"));
        mod = await import(pathToFileURL(file).href);
        errors = [];
        console.error = (...args) => errors.push(args.map(String).join(" "));
    });

    after(() => { console.error = realError; rmSync(dir, { recursive: true, force: true }); });

    test("exports the assets as resolved", () => {
        assert.deepEqual(mod.assets.map(a => a.id), ASSETS.map(a => a.id));
    });

    test("installs every extension in order, and a failing one does not stop the others", async () => {
        const renderer = { calls: [] };
        const bus = { name: "bus" };
        const report = await mod.installAll(renderer, bus);
        assert.deepEqual(renderer.calls, [["first", bus], ["async"], ["last"]]);
        assert.deepEqual(report.map(r => [r.id, r.ok]), [
            ["first", true], ["broken", false], ["lib", true], ["noinstall", false],
            ["throws", false], ["async", true], ["last", true]]);
        assert.match(report.find(r => r.id === "noinstall").error, /no install/);
        assert.match(report.find(r => r.id === "throws").error, /install failed/);
        assert.equal(globalThis.__suiLib, 1, "a module is imported, not installed");
        assert.equal(errors.length, 3, "each failure is logged once");
        assert.ok(errors.some(e => e.includes('"broken"') && e.includes("could not be loaded")), errors.join("\n"));
    });

    test("links the stylesheets the page does not have yet, and waits for them", async () => {
        const doc = fakeDocument(["base.css"]);
        await mod.linkStyles(doc);
        assert.deepEqual(doc.head.children.map(l => [l.attrs["data-sui-asset"], new URL(l.href).pathname, l.rel]),
            [["theme.css", "/theme.css", "stylesheet"]]);
    });

    test("a relative url is resolved against the module, not the page", async () => {
        const source = readFileSync(TEMPLATE, "utf8").replace("/*SUI_ASSETS*/[]",
            JSON.stringify([{ id: "x.css", kind: "css", url: "../sui-ext/x/x.css" }]));
        const file = path.join(dir, "sui", "assets.mjs");
        (await import("node:fs")).mkdirSync(path.dirname(file), { recursive: true });
        writeFileSync(file, source);
        const relative = await import(pathToFileURL(file).href);
        const doc = fakeDocument([]);
        await relative.linkStyles(doc);
        // Against the module's real location (a temp dir can sit behind a symlink, as /var does on macOS).
        const real = (await import("node:fs")).realpathSync(dir);
        assert.equal(doc.head.children[0].href, pathToFileURL(path.join(real, "sui-ext", "x", "x.css")).href);
    });

    test("with no document there is nothing to link", async () => {
        assert.equal(await mod.linkStyles(undefined), undefined);
    });
});
