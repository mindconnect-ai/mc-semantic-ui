/**
 * A responsive menu is a sidebar on a wide screen and a drawer on a narrow
 * one. The stylesheet switches between the two at a breakpoint; these tests
 * are about the STATE the menu carries across that switch.
 *
 * Two things used to go wrong. A window narrowed after load kept its sidebar
 * "expanded", which the drawer stylesheet drew open on top of the page —
 * nothing re-evaluated the menu, because restoreMenuState ran once at mount
 * and no one watched the breakpoint. And a choice saved on the desktop was
 * applied verbatim on a phone, so once you had ever toggled the sidebar, even
 * a fresh narrow load showed the drawer lying across the content.
 *
 * Runs against the compiled output in target/ts-dist, with a DOM stubbed just
 * far enough for the menu state machine: no jsdom, same as the tests next
 * door. matchMedia is stubbed so a test can flip the viewport and fire the
 * change the browser would.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let restoreMenuState, menuStateOf, resetBreakpointWatchForTests;

/** The narrowest stand-in for a .sui-menu element the state machine needs. */
function fakeMenu(id, { responsive = true, state = "expanded" } = {}) {
    const classes = new Set(["sui-menu", `sui-menu--${state}`]);
    if (responsive) classes.add("sui-menu--responsive");
    return {
        id,
        dataset: { menuState: state },
        classList: {
            contains: c => classes.has(c),
            toggle: (c, on) => { on ? classes.add(c) : classes.delete(c); },
        },
        querySelector: () => null,
    };
}

/** A matchMedia whose result the test controls, and whose listener it can fire. */
function fakeMatchMedia() {
    const query = { matches: false, listeners: [] };
    query.addEventListener = (_type, cb) => query.listeners.push(cb);
    query.fire = () => query.listeners.forEach(cb => cb());
    globalThis.matchMedia = () => query;
    return query;
}

/** A document that only knows how to find the menus we hand it. */
function fakeDocument(...menus) {
    globalThis.document = {
        querySelectorAll: selector => menus.filter(m =>
            selector.includes("--responsive") ? m.classList.contains("sui-menu--responsive") : true),
    };
    return globalThis.document;
}

const store = new Map();

describe("a responsive menu across the drawer breakpoint", () => {

    // Inside the suite on purpose. Hooks at file level belong to the root,
    // and when several test files share a run the root's `before` is not
    // guaranteed to have resolved before a nested suite's `beforeEach` fires
    // — which is how these tests once failed together while passing alone.
    before(async () => {
        globalThis.localStorage = {
            getItem: k => store.has(k) ? store.get(k) : null,
            setItem: (k, v) => store.set(k, v),
        };
        ({ restoreMenuState, menuStateOf, resetBreakpointWatchForTests } =
            await import(path.join(DIST, "renderers/menu.js")));
    });

    beforeEach(() => {
        store.clear();
        resetBreakpointWatchForTests();
    });

    test("a sidebar saved as expanded still starts closed on a narrow screen", () => {
        const viewport = fakeMatchMedia();
        viewport.matches = true;                       // narrow
        store.set("sui-menu:nav", "expanded");         // chose it on the desktop
        const menu = fakeMenu("nav");
        const doc = fakeDocument(menu);

        restoreMenuState(doc);

        assert.equal(menuStateOf(menu), "hidden");
        assert.equal(store.get("sui-menu:nav"), "expanded", "the desktop choice is kept, not overwritten");
    });

    test("narrowing the window after load closes the drawer", () => {
        const viewport = fakeMatchMedia();
        viewport.matches = false;                      // wide at mount
        const menu = fakeMenu("nav");
        const doc = fakeDocument(menu);
        restoreMenuState(doc);
        assert.equal(menuStateOf(menu), "expanded");

        viewport.matches = true;                       // the user drags the window in
        viewport.fire();

        assert.equal(menuStateOf(menu), "hidden");
    });

    test("widening it again brings the saved choice back", () => {
        const viewport = fakeMatchMedia();
        viewport.matches = false;
        store.set("sui-menu:nav", "rail");
        const menu = fakeMenu("nav");
        const doc = fakeDocument(menu);
        restoreMenuState(doc);
        assert.equal(menuStateOf(menu), "rail");

        viewport.matches = true;  viewport.fire();
        assert.equal(menuStateOf(menu), "hidden");

        viewport.matches = false; viewport.fire();
        assert.equal(menuStateOf(menu), "rail", "the choice survives the round trip");
        assert.equal(store.get("sui-menu:nav"), "rail", "and nothing about it was rewritten");
    });

    test("the breakpoint is watched once, however often pages mount", () => {
        const viewport = fakeMatchMedia();
        const menu = fakeMenu("nav");
        const doc = fakeDocument(menu);

        restoreMenuState(doc);
        restoreMenuState(doc);
        restoreMenuState(doc);

        assert.equal(viewport.listeners.length, 1);
    });

    test("on a wide screen with nothing saved, the server-rendered state stands", () => {
        const viewport = fakeMatchMedia();
        viewport.matches = false;
        const menu = fakeMenu("nav", { state: "rail" });   // the server chose rail
        const doc = fakeDocument(menu);

        restoreMenuState(doc);

        assert.equal(menuStateOf(menu), "rail", "not forced open just because nothing was saved");
    });

    test("a menu that is not responsive is left alone by the breakpoint", () => {
        const viewport = fakeMatchMedia();
        viewport.matches = false;
        const fixed = fakeMenu("side", { responsive: false });
        const doc = fakeDocument(fixed);
        restoreMenuState(doc);

        viewport.matches = true; viewport.fire();

        assert.equal(menuStateOf(fixed), "expanded");
    });
});
