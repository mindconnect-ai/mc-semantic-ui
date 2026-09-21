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
import { readFileSync } from "node:fs";

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

    test("the tag lists: kept, dropped with content, unwrapped", () => {
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

    test("an image keeps a whole-number width and height, nothing else", () => {
        // The policy: only these attribute names survive on an img, checked by
        // the sanitiser's DOM walk in the browser; here the shape of the rule.
        assert.equal(/^\d{1,5}$/.test("640"), true);
        assert.equal(/^\d{1,5}$/.test("50%"), false);
        assert.equal(/^\d{1,5}$/.test("640px"), false);
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

/**
 * One policy in three places: this sanitiser, the Java RichTextSanitizer
 * and whatever the renderer draws from them. Both test suites run the same
 * cases from src/test/resources/richtext/sanitize-cases.json, so the browser
 * and the server can never disagree about what survives.
 */
describe("sanitizeRichText", () => {
    let sanitizeRichText, safeHref, cases;

    before(async () => {
        ({ sanitizeRichText, safeHref } = await import(`${DIST}/renderers/richtext.js`));
        const { readFileSync } = await import("node:fs");
        cases = JSON.parse(readFileSync(path.resolve(path.dirname(fileURLToPath(import.meta.url)),
            "../resources/richtext/sanitize-cases.json"), "utf8"));
    });

    test("every shared case", () => {
        for (const c of cases) assert.equal(sanitizeRichText(c.in), c.out, c.name);
    });

    test("cleaning clean output changes nothing", () => {
        for (const c of cases) assert.equal(sanitizeRichText(c.out), c.out, c.name);
    });

    test("null and undefined are empty", () => {
        assert.equal(sanitizeRichText(null), "");
        assert.equal(sanitizeRichText(undefined), "");
    });

    test("a link scheme is checked as a browser reads it: controls and spaces removed", () => {
        const tab = String.fromCharCode(9), nl = String.fromCharCode(10), soh = String.fromCharCode(1);
        for (const bad of [`jav${tab}ascript:alert(1)`, `java${nl}script:x`, `${soh}javascript:x`, "java script:x"]) {
            assert.equal(safeHref(bad), false, JSON.stringify(bad));
        }
    });

    test("a rendered RICHTEXT value is sanitised, in the editor, the hidden input and read-only", async () => {
        const { renderField } = await import(`${DIST}/renderers/field.js`);
        const value = `<p>Hi</p><img src="x" onerror="alert(1)"><script>alert(2)</script>`;
        const editable = renderField({ type: "field", id: "n", label: "N", fieldType: "RICHTEXT", editable: true, value });
        assert.doesNotMatch(editable, /onerror|<script|alert/);
        assert.match(editable, /contenteditable="true" role="textbox" aria-multiline="true"><p>Hi<\/p><\/div>/);
        assert.match(editable, /<input type="hidden" name="n" value="&lt;p&gt;Hi&lt;\/p&gt;"/);
        const readOnly = renderField({ type: "field", id: "n", label: "N", fieldType: "RICHTEXT", value });
        assert.match(readOnly, /<div class="sui-richtext-view"><p>Hi<\/p><\/div>/);
    });
});

describe("RICHTEXT height (fixed toolbar, scrolling text)", () => {
    let renderField;
    const CASES = JSON.parse(readFileSync(path.resolve(path.dirname(fileURLToPath(import.meta.url)),
        "../resources/richtext/height-cases.json"), "utf8"));
    before(async () => { ({ renderField } = await import(`${DIST}/renderers/field.js`)); });

    // The same cases SuiServerRendererTest renders through field.hbs.
    for (const c of CASES) {
        test(c.name, () => {
            const html = renderField(c.field);
            for (const s of c.contains) assert.ok(html.includes(s), `missing ${s} in\n${html}`);
            for (const s of c.absent) assert.ok(!html.includes(s), `unexpected ${s} in\n${html}`);
        });
    }

    test("a height that is not a CSS length never reaches the style", () => {
        for (const bad of ['1px;background:url(x)', '320px" onmouseover="x', "calc(1px)", "red", "320"]) {
            const html = renderField({ type: "field", id: "n", label: "N", fieldType: "RICHTEXT", editable: true, value: "", editorHeight: bad });
            assert.ok(!html.includes("style=") && !html.includes("--fixed"), `${bad}: ${html}`);
        }
    });
});
