/**
 * The RICHTEXT field: what it renders, and what a paste may keep.
 *
 * The sanitiser itself needs a DOM parser and is exercised in the browser;
 * the policy it applies — which tags survive, which are dropped with their
 * content, which links are safe — is plain logic and is pinned here. Runs
 * against the compiled output in target/ts-dist.
 */
import { test, describe, before } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

describe("RICHTEXT field", () => {
    let renderField, rt;

    before(async () => {
        ({ renderField } = await import(`${DIST}/renderers/field.js`));
        rt = await import(`${DIST}/renderers/richtext.js`);
    });

    test("editable: toolbar, an editable area holding the HTML, a hidden input carrying it", () => {
        const html = renderField({ type: "field", id: "notes", label: "Notes", fieldType: "RICHTEXT", editable: true,
            value: "<p>Hi <b>there</b></p>", placeholder: "Write…" });
        assert.match(html, /<div class="sui-richtext" data-sui-richtext><div class="sui-richtext-toolbar" role="toolbar"/);
        for (const cmd of ["bold", "italic", "underline", "bullets", "numbers", "link", "quote", "clear"]) {
            assert.match(html, new RegExp(`data-sui-richtext-cmd="${cmd}"`));
        }
        assert.match(html, /<div class="sui-richtext-editor" id="notes__input" contenteditable="true" role="textbox" aria-multiline="true" data-placeholder="Write…"><p>Hi <b>there<\/b><\/p><\/div>/);
        assert.match(html, /<input type="hidden" name="notes" value="&lt;p&gt;Hi &lt;b&gt;there&lt;\/b&gt;&lt;\/p&gt;" data-sui-type="RICHTEXT">/);
    });

    test("read-only: the HTML is shown as formatted text, or a dash when empty", () => {
        assert.match(renderField({ type: "field", id: "n", label: "N", fieldType: "RICHTEXT", value: "<p>x</p>" }),
            /<div class="sui-richtext-view"><p>x<\/p><\/div>/);
        assert.match(renderField({ type: "field", id: "n", label: "N", fieldType: "RICHTEXT" }), /<span class="sui-value">—<\/span>/);
    });

    test("a paste keeps formatting tags and drops what runs or styles", () => {
        for (const t of ["p", "b", "strong", "i", "em", "u", "ul", "ol", "li", "a", "blockquote", "br", "h2", "code"]) {
            assert.equal(rt.allowedTag(t), true, t);
            assert.equal(rt.droppedTag(t), false, t);
        }
        for (const t of ["script", "style", "link", "iframe", "object", "embed", "form", "input", "svg", "meta"]) {
            assert.equal(rt.droppedTag(t), true, t);
        }
        for (const t of ["table", "font", "center", "section", "video"]) {
            assert.equal(rt.allowedTag(t), false, t);   // unwrapped, text kept
            assert.equal(rt.droppedTag(t), false, t);
        }
    });

    test("images stay when embedded as data or fetched over http(s)", () => {
        assert.equal(rt.allowedTag("img"), true);
        assert.equal(rt.safeImageSrc("data:image/png;base64,iVBORw0KGgo="), true);
        assert.equal(rt.safeImageSrc("https://example.com/a.jpg"), true);
        for (const bad of ["data:text/html;base64,PHNjcmlwdD4=", "javascript:alert(1)", "file:///etc/passwd", ""]) {
            assert.equal(rt.safeImageSrc(bad), false, bad);
        }
    });

    test("only links that go somewhere harmless survive", () => {
        for (const ok of ["https://example.com", "http://x", "mailto:a@b.c", "tel:+41", "/docs", "docs/a", "#top", "?q=1"]) {
            assert.equal(rt.safeHref(ok), true, ok);
        }
        for (const bad of ["javascript:alert(1)", " JAVASCRIPT:x", "data:text/html,x", "vbscript:x", ""]) {
            assert.equal(rt.safeHref(bad), false, bad);
        }
    });

    test("the toolbar lists its commands in order", () => {
        assert.deepEqual(rt.RICHTEXT_COMMANDS.map(c => c[0]), ["bold", "italic", "underline", "bullets", "numbers", "link", "quote", "clear"]);
    });
});
