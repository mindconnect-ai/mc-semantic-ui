/**
 * Icon sets in the browser: a token takes the sprite of the longest prefix it
 * starts with, else the standard sprite — the same markup IconRenderer writes
 * on the server (the cases are shared with IconSetsTest). Runs against the
 * compiled output.
 */
import { test, describe, before, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.resolve(HERE, "../../../target/ts-dist");
const FIXTURE = JSON.parse(readFileSync(path.resolve(HERE, "../resources/icons/icon-sets-cases.json"), "utf8"));

describe("icon sets", () => {
    let icon, createDefaultRenderer;
    before(async () => {
        icon = await import(`${DIST}/renderers/icon.js`);
        ({ createDefaultRenderer } = await import(`${DIST}/renderer.js`));
    });

    afterEach(() => {
        for (const set of icon.iconSpritesInUse()) icon.removeIconSprite(set.prefix);
        icon.setIconSpriteUrl(FIXTURE.standard);
    });

    function useFixtureSets() {
        icon.setIconSpriteUrl(FIXTURE.standard);
        for (const [prefix, url] of Object.entries(FIXTURE.sets)) icon.addIconSprite(prefix, url);
    }

    for (const c of FIXTURE.cases) {
        test(`as on the server: ${c.name}`, () => {
            useFixtureSets();
            const opts = {};
            if (c.cssClass) opts.cssClass = c.cssClass;
            if (c.title) opts.title = c.title;
            if (c.id) opts.id = c.id;
            assert.equal(icon.renderIcon(c.token, opts), c.html);
        });
    }

    test("the longest prefix wins, whatever order the sets came in", () => {
        icon.addIconSprite("acme-logo-", "/logos.svg");
        icon.addIconSprite("acme-", "/acme.svg");
        assert.equal(icon.spriteUrlFor("acme-logo-main"), "/logos.svg");
        assert.equal(icon.spriteUrlFor("acme-logout"), "/acme.svg");
        assert.equal(icon.spriteUrlFor("acme"), FIXTURE.standard);
        assert.deepEqual(icon.iconSpritesInUse().map(s => s.prefix), ["acme-logo-", "acme-"]);
    });

    test("a second set for a prefix replaces the first; removing it falls back", () => {
        icon.addIconSprite("brand-", "/old.svg");
        icon.addIconSprite("brand-", "/new.svg");
        assert.equal(icon.iconSpritesInUse().length, 1);
        assert.equal(icon.spriteUrlFor("brand-x"), "/new.svg");
        assert.equal(icon.removeIconSprite("brand-"), true);
        assert.equal(icon.spriteUrlFor("brand-x"), FIXTURE.standard);
    });

    test("a prefix must be lowercase-kebab ending in a dash", () => {
        for (const bad of ["", "brand", "Brand-", "brand--", "-", "1brand-", 'b"-']) {
            assert.throws(() => icon.addIconSprite(bad, "/b.svg"), /lowercase-kebab/, bad);
        }
    });

    test("a plugin wraps the resolver instead of replacing it", () => {
        useFixtureSets();
        const previous = icon.getIconResolver();
        try {
            icon.setIconResolver((name, opts) => name === "star" ? "<b>*</b>" : previous(name, opts));
            assert.equal(icon.renderIcon("star"), "<b>*</b>");
            assert.match(icon.renderIcon("brand-google"), /brand-icons\.svg#brand-google/);
            assert.match(icon.renderIcon("trash"), /\/sui\/icons\.svg#trash/);
        } finally {
            icon.setIconResolver(previous);
        }
    });

    test("an app's own resolver replaces everything, sets included", () => {
        useFixtureSets();
        const previous = icon.getIconResolver();
        try {
            icon.setIconResolver(name => `<i>${name}</i>`);
            assert.equal(icon.renderIcon("brand-google"), "<i>brand-google</i>");
        } finally {
            icon.setIconResolver(previous);
        }
    });

    test("the renderer adds a set to the icon module it renders with", () => {
        const renderer = createDefaultRenderer();
        assert.equal(renderer.addIconSprite("brand-", "/b.svg"), renderer);
        assert.match(renderer.render({ type: "icon", id: "g", name: "brand-google" }),
            /<svg id="g" class="sui-icon sui-icon--set" aria-hidden="true"><use href="\/b\.svg#brand-google"><\/use><\/svg>/);
    });
});
