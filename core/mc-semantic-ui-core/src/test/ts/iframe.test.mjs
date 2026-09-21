/**
 * An iframe's sandbox: absent means none, and the empty string means the
 * strictest one — not "unset". Runs against the compiled output.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

describe("iframe sandbox", () => {
    let renderIFrame;
    before(async () => { ({ renderIFrame } = await import(`${DIST}/renderers/iframe.js`)); });

    test("an empty sandbox is rendered: it is the strictest one", () => {
        assert.match(renderIFrame({ type: "iframe", id: "mail", src: "/mail/1", sandbox: "" }), / sandbox="" /);
    });

    test("a sandbox with tokens is rendered as given", () => {
        assert.match(renderIFrame({ type: "iframe", id: "f", src: "/x", sandbox: "allow-scripts" }), / sandbox="allow-scripts" /);
    });

    test("no sandbox, or null, renders none", () => {
        assert.doesNotMatch(renderIFrame({ type: "iframe", id: "f", src: "/x" }), /sandbox/);
        assert.doesNotMatch(renderIFrame({ type: "iframe", id: "f", src: "/x", sandbox: null }), /sandbox/);
    });
});
