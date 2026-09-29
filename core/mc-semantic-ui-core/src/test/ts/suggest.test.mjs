/**
 * UiField.suggest in the browser: which token is asked about and how a pick
 * replaces it, the timing of the questions (a pause, then the latest wins),
 * the listbox's keyboard, the markup both renderers write, and what a pick
 * does to a field — against the compiled output and the stub DOM.
 */
import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { El, installControls } from "./support/mini-dom.mjs";

const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

installControls();
const {
    tokenAt, replaceToken, suggestUrl, parseSuggestions, SuggestSession, listKey,
    acceptSuggestion, setSuggestPicks, leaveSuggest, MAX_SUGGESTIONS,
} = await import(`${DIST}/renderers/suggest.js`);
const { createDefaultRenderer } = await import(`${DIST}/renderer.js`);
const { SuiEventBus } = await import(`${DIST}/eventbus.js`);

const Input = globalThis.HTMLInputElement;

// ── The token ───────────────────────────────────────────────────────────────

describe("the token under the caret", () => {
    test("without a separator the whole value is one token, trimmed", () => {
        assert.deepEqual(tokenAt("  anna ", 3, null), { start: 0, end: 7, text: "anna" });
        assert.deepEqual(tokenAt("anna, bert", 10), { start: 0, end: 10, text: "anna, bert" });
    });

    test("with one, the stretch between the separators around the caret", () => {
        const line = "anna@x.ch, be";
        assert.deepEqual(tokenAt(line, line.length, ","), { start: 10, end: 13, text: "be" });
        // In the middle: "a@x, b|o, c@y".
        assert.deepEqual(tokenAt("a@x, bo, c@y", 6, ","), { start: 4, end: 7, text: "bo" });
        // Right after a separator: an empty token, nothing to ask about yet.
        assert.equal(tokenAt("anna@x.ch,", 10, ",").text, "");
        // At the very start.
        assert.deepEqual(tokenAt("an, bert", 0, ","), { start: 0, end: 2, text: "an" });
    });
});

describe("a pick replaces only the token", () => {
    test("without a separator it is the whole value, the caret at its end", () => {
        assert.deepEqual(replaceToken("ann", 3, null, "anna@x.ch"), { value: "anna@x.ch", caret: 9 });
    });

    test("the last entry gets the separator and a space, ready for the next", () => {
        const next = replaceToken("anna@x.ch, be", 13, ",", "bert@y.ch");
        assert.deepEqual(next, { value: "anna@x.ch, bert@y.ch, ", caret: 22 });
        assert.deepEqual(replaceToken("an", 2, ",", "anna@x.ch"), { value: "anna@x.ch, ", caret: 11 });
        // A separator typed without its space gets one.
        assert.equal(replaceToken("a@x,bo", 6, ",", "bob@z").value, "a@x, bob@z, ");
    });

    test("a whitespace separator — one entry per line — gets no space after it", () => {
        assert.deepEqual(replaceToken("anna@x.ch\nbe", 12, "\n", "bert@y.ch"), { value: "anna@x.ch\nbert@y.ch\n", caret: 20 });
    });

    test("an entry in the middle keeps the separator that follows it", () => {
        assert.deepEqual(replaceToken("a@x, bo, c@y", 6, ",", "bob@z"), { value: "a@x, bob@z, c@y", caret: 10 });
    });
});

test("the request url takes q with ? or &, before a fragment", () => {
    assert.equal(suggestUrl("/contacts/suggest", "an na"), "/contacts/suggest?q=an%20na");
    assert.equal(suggestUrl("/s?kind=person", "a&b"), "/s?kind=person&q=a%26b");
    assert.equal(suggestUrl("/s?", "a"), "/s?q=a");
    assert.equal(suggestUrl("/s#top", "a"), "/s?q=a#top");
});

test("the answer: entries with a value, the label defaulting to it, at most eight", () => {
    assert.deepEqual(parseSuggestions({ value: "a" }), []);
    assert.deepEqual(parseSuggestions([
        { value: "anna@x.ch", label: "Anna Muster <anna@x.ch>", detail: "Sales" },
        { value: 42 },
        { label: "no value" },
        null,
    ]), [
        { value: "anna@x.ch", label: "Anna Muster <anna@x.ch>", detail: "Sales" },
        { value: "42", label: "42" },
    ]);
    const many = Array.from({ length: 20 }, (_, i) => ({ value: `v${i}` }));
    assert.equal(parseSuggestions(many).length, MAX_SUGGESTIONS);
});

// ── Timing ──────────────────────────────────────────────────────────────────

/** Timers that run when told to, and a fetch whose answers are handed out by the test. */
function harness({ minChars = 1, debounceMs = 200 } = {}) {
    const timers = [];
    const requests = [];
    const results = [];
    const session = new SuggestSession({
        url: "/contacts/suggest", minChars, debounceMs,
        setTimer: (fn, ms) => { const t = { fn, ms, live: true }; timers.push(t); return t; },
        clearTimer: (t) => { t.live = false; },
        fetch: (url, init) => new Promise((resolve, reject) => {
            const request = { url, init, answer: (items) => resolve({ ok: true, json: async () => items }), fail: reject };
            init.signal?.addEventListener?.("abort", () => { request.aborted = true; reject(Object.assign(new Error("aborted"), { name: "AbortError" })); });
            requests.push(request);
        }),
        onResults: (items, token) => results.push({ token, items }),
    });
    const runTimers = () => { for (const t of timers.splice(0)) if (t.live) t.fn(); };
    return { session, timers, requests, results, runTimers };
}

const tick = () => new Promise(r => setTimeout(r, 0));

describe("the questions a field asks", () => {
    test("typing restarts the pause; only the token it ends on is asked for", async () => {
        const h = harness();
        h.session.update("a");
        h.session.update("an");
        h.session.update("ann");
        assert.equal(h.timers.filter(t => t.live).length, 1);
        assert.equal(h.timers.at(-1).ms, 200);
        h.runTimers();
        assert.equal(h.requests.length, 1);
        assert.equal(h.requests[0].url, "/contacts/suggest?q=ann");
        assert.equal(h.requests[0].init.method, "GET");
        assert.equal(h.requests[0].init.credentials, "same-origin");
        h.requests[0].answer([{ value: "anna@x.ch" }]);
        await tick();
        assert.deepEqual(h.results, [{ token: "ann", items: [{ value: "anna@x.ch", label: "anna@x.ch" }] }]);
    });

    test("a token shorter than the minimum answers none, without a request", async () => {
        const h = harness({ minChars: 2 });
        h.session.update("a");
        h.runTimers();
        await tick();
        assert.equal(h.requests.length, 0);
        assert.deepEqual(h.results, [{ token: "a", items: [] }]);
    });

    test("the latest question wins: an older answer arriving late is dropped, and aborted", async () => {
        const h = harness();
        h.session.update("an");
        h.runTimers();
        h.session.update("anna");
        h.runTimers();
        assert.equal(h.requests.length, 2);
        assert.equal(h.requests[0].aborted, true);
        h.requests[1].answer([{ value: "anna@x.ch" }]);
        await tick();
        h.requests[0].answer([{ value: "andreas@x.ch" }]);
        await tick();
        assert.deepEqual(h.results.map(r => r.token), ["anna"]);
    });

    test("cancel drops the answer still out", async () => {
        const h = harness();
        h.session.update("an");
        h.runTimers();
        h.session.cancel();
        h.requests[0].answer([{ value: "anna@x.ch" }]);
        await tick();
        assert.deepEqual(h.results, []);
    });

    test("a failed request is no suggestions, not an error", async () => {
        const h = harness();
        h.session.update("an");
        h.runTimers();
        h.requests[0].fail(new Error("offline"));
        await tick();
        assert.deepEqual(h.results, [{ token: "an", items: [] }]);
    });
});

// ── Keyboard ────────────────────────────────────────────────────────────────

test("the list's keyboard: arrows move and wrap, Enter and Tab take, Escape closes", () => {
    assert.deepEqual(listKey("ArrowDown", 3, 0), { handled: true, active: 1 });
    assert.deepEqual(listKey("ArrowDown", 3, 2), { handled: true, active: 0 });
    assert.deepEqual(listKey("ArrowUp", 3, 0), { handled: true, active: 2 });
    assert.deepEqual(listKey("Enter", 3, 1), { handled: true, active: 1, action: "accept" });
    assert.deepEqual(listKey("Tab", 3, 2), { handled: true, active: 2, action: "accept" });
    assert.deepEqual(listKey("Escape", 3, 1), { handled: true, active: 1, action: "close" });
    // Shift+Tab goes back to the field before, as always.
    assert.deepEqual(listKey("Tab", 3, 1, true), { handled: false, active: 1 });
    // Nothing highlighted: Enter and Tab are the field's; an arrow starts at the top.
    assert.equal(listKey("Tab", 3, -1).handled, false);
    assert.deepEqual(listKey("ArrowDown", 3, -1), { handled: true, active: 0 });
    // Everything else is the field's, and an empty list has no keys.
    assert.equal(listKey("a", 3, 1).handled, false);
    assert.equal(listKey("Enter", 3, -1).handled, false);
    assert.equal(listKey("ArrowDown", 0, -1).handled, false);
});

// ── Markup ──────────────────────────────────────────────────────────────────

const render = (node) => createDefaultRenderer().render(node);
const field = (extra) => ({ type: "field", id: "to", label: "To", editable: true, suggest: { url: "/contacts/suggest" }, ...extra });

describe("the markup", () => {
    test("a TEXT field hands its input to the enhancer, with what the model set", () => {
        const html = render(field({ fieldType: "TEXT", suggestSeparator: ",", suggestMinChars: 2, suggestDebounceMs: 150 }));
        assert.match(html, /<input type="text" id="to__input" name="to" value="" placeholder="" autocomplete="off" role="combobox" aria-autocomplete="list" aria-expanded="false" data-sui-suggest="\/contacts\/suggest" data-sui-suggest-separator="," data-sui-suggest-min="2" data-sui-suggest-debounce="150">/);
    });

    test("a TEXTAREA too; defaults are left to the enhancer", () => {
        const html = render(field({ fieldType: "TEXTAREA" }));
        assert.match(html, /<textarea id="to__input" name="to" rows="4" autocomplete="off" role="combobox" aria-autocomplete="list" aria-expanded="false" data-sui-suggest="\/contacts\/suggest">/);
        assert.doesNotMatch(html, /data-sui-suggest-(min|debounce|separator|mode)/);
    });

    test("a SELECT is a search box showing the picked label, and the hidden value", () => {
        const html = render(field({ fieldType: "SELECT", value: "u7", options: [{ value: "u7", label: "Anna Muster" }] }));
        assert.match(html, /<input type="text" id="to__input" value="Anna Muster" placeholder="" data-sui-suggest-label="Anna Muster" autocomplete="off" role="combobox"[^>]* data-sui-suggest-mode="one"><input type="hidden" name="to" value="u7" data-sui-type="SELECT">/);
        assert.doesNotMatch(html, /<select/);
    });

    test("a MULTISELECT is a chip per pick and a search box after them", () => {
        const html = render(field({ fieldType: "MULTISELECT", value: ["u7", "u9"], options: [{ value: "u7", label: "Anna" }] }));
        assert.match(html, /<div class="sui-chips" data-sui-chips="to">/);
        assert.match(html, /<span class="sui-chip" data-value="u7"><span class="sui-chip-label">Anna<\/span><button type="button" class="sui-chip-remove" data-sui-chip-remove aria-label="Remove Anna">/);
        // A value no option knows is its own label.
        assert.match(html, /<span class="sui-chip" data-value="u9"><span class="sui-chip-label">u9<\/span>/);
        assert.equal((html.match(/<input type="hidden" name="to" value="u(7|9)" data-sui-type="MULTISELECT">/g) ?? []).length, 2);
        assert.match(html, /<input type="text" id="to__input" class="sui-chips-input"[^>]* data-sui-suggest-mode="many"><\/div>/);
    });

    test("no suggestions where they do not apply: expanded, read-only, no url, other types", () => {
        for (const f of [
            field({ fieldType: "SELECT", expanded: true, options: [{ value: "a", label: "A" }] }),
            field({ fieldType: "TEXT", editable: false, value: "x" }),
            field({ fieldType: "TEXT", suggest: { url: " " } }),
            field({ fieldType: "NUMBER" }),
        ]) {
            assert.doesNotMatch(render(f), /data-sui-suggest/, JSON.stringify(f));
        }
    });

    test("the url is an attribute value, escaped", () => {
        assert.match(render(field({ fieldType: "TEXT", suggest: { url: '/s?x="><b>' } })), /data-sui-suggest="\/s\?x=&quot;&gt;&lt;b&gt;"/);
    });
});

// ── A pick in the field ─────────────────────────────────────────────────────

/** A control with a listener that records the events it sends. */
function recorded(control) {
    const heard = [];
    control.addEventListener("input", () => heard.push("input"));
    control.addEventListener("change", () => heard.push("change"));
    return heard;
}

/** As a browser does: createElement("input") is an input element, the rest plain elements. */
const createElement = (t) => t === "input" ? new Input(t) : new El(t);

function withDocument(fn) {
    globalThis.document = { createElement, activeElement: null };
    try { return fn(); } finally { delete globalThis.document; }
}

describe("taking a suggestion", () => {
    test("text: over the token, the separator after it, and the field's input and change", () => withDocument(() => {
        const control = new Input("input", {
            id: "to__input", name: "to", type: "text", value: "anna@x.ch, be",
            "data-sui-suggest": "/s", "data-sui-suggest-separator": ",",
        });
        const heard = recorded(control);
        acceptSuggestion(control, { value: "bert@y.ch", label: "Bert" });
        assert.equal(control.value, "anna@x.ch, bert@y.ch, ");
        assert.deepEqual(heard, ["input", "change"]);
    }));

    test("one: the label in the search box, the value in the hidden input", () => withDocument(() => {
        const control = new Input("input", { id: "owner__input", type: "text", value: "Ann", "data-sui-suggest": "/s", "data-sui-suggest-mode": "one" });
        const hidden = new Input("input", { type: "hidden", name: "owner", value: "", "data-sui-type": "SELECT" });
        new El("div", { class: "sui-field", id: "owner" }, [control, hidden]);
        const heard = recorded(control);
        acceptSuggestion(control, { value: "u7", label: "Anna Muster" });
        assert.equal(hidden.value, "u7");
        assert.equal(control.value, "Anna Muster");
        assert.equal(control.getAttribute("data-sui-suggest-label"), "Anna Muster");
        assert.deepEqual(heard, ["input", "change"]);
    }));

    test("many: a chip before the search box, once per value", () => withDocument(() => {
        const control = new Input("input", { id: "tags__input", type: "text", value: "ur", "data-sui-suggest": "/s", "data-sui-suggest-mode": "many" });
        const chips = new El("div", { class: "sui-chips", "data-sui-chips": "tags" }, [control]);
        const heard = recorded(control);
        acceptSuggestion(control, { value: "urgent", label: "Urgent" });
        acceptSuggestion(control, { value: "urgent", label: "Urgent" });
        const made = chips.querySelectorAll(".sui-chip");
        assert.equal(made.length, 1);
        assert.equal(made[0].getAttribute("data-value"), "urgent");
        assert.equal(chips.children.at(-1), control);
        const input = made[0].querySelector('input[type="hidden"]');
        assert.equal(input.getAttribute("name"), "tags");
        assert.equal(input.getAttribute("value"), "urgent");
        assert.equal(control.value, "");
        assert.deepEqual(heard, ["change"]);
    }));
});

describe("leaving a SELECT with suggestions", () => {
    function owner(typed) {
        const control = new Input("input", { id: "owner__input", type: "text", value: typed, "data-sui-suggest": "/s", "data-sui-suggest-mode": "one", "data-sui-suggest-label": "Bert Keller" });
        const hidden = new Input("input", { type: "hidden", name: "owner", value: "u2", "data-sui-type": "SELECT" });
        new El("div", { class: "sui-field", id: "owner" }, [control, hidden]);
        return { control, hidden, heard: recorded(control) };
    }

    test("typed text that was not picked is undone, and nothing is said", () => withDocument(() => {
        const f = owner("zzz");
        leaveSuggest(f.control);
        assert.equal(f.control.value, "Bert Keller");
        assert.equal(f.hidden.value, "u2");
        assert.deepEqual(f.heard, []);
    }));

    test("an emptied box clears the pick, as a change", () => withDocument(() => {
        const f = owner("  ");
        leaveSuggest(f.control);
        assert.equal(f.control.value, "");
        assert.equal(f.hidden.value, "");
        assert.equal(f.control.getAttribute("data-sui-suggest-label"), "");
        assert.deepEqual(f.heard, ["input", "change"]);
    }));
});

describe("a MERGE redraws a field from its model", () => {
    test("with the labels of picks that came from the server", () => {
        const renderer = createDefaultRenderer();
        const form = {
            type: "form", id: "f", title: "Task", fields: [
                { type: "field", id: "owner", label: "Owner", fieldType: "SELECT", editable: true, value: "u2",
                  options: [{ value: "u2", label: "Bert Keller" }], suggest: { url: "/people" } },
                { type: "field", id: "tags", label: "Tags", fieldType: "MULTISELECT", editable: true, value: [],
                  suggest: { url: "/tags" } },
            ],
        };
        renderer.render(form);
        // The page after the user picked Eva (u7) and a chip "Travel".
        const box = new Input("input", { id: "owner__input", type: "text", value: "Eva Schmid", "data-sui-suggest": "/people", "data-sui-suggest-mode": "one", "data-sui-suggest-label": "Eva Schmid" });
        const hidden = new Input("input", { type: "hidden", name: "owner", value: "u7", "data-sui-type": "SELECT" });
        const label = new El("span", { class: "sui-chip-label" });
        label.textContent = "Travel";
        const chip = new El("span", { class: "sui-chip", "data-value": "travel" }, [label,
            new Input("input", { type: "hidden", name: "tags", value: "travel", "data-sui-type": "MULTISELECT" })]);
        const chips = new El("div", { class: "sui-chips", "data-sui-chips": "tags" }, [chip]);
        const target = new El("form", { id: "f" }, [new El("div", { id: "owner" }, [box, hidden]), new El("div", { id: "tags" }, [chips])]);
        globalThis.document = { getElementById: (id) => id === "f" ? target : null, createElement };
        globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible", position: "static" });
        try {
            renderer.setInputReader(() => ({ owner: "u7", tags: ["travel"] }));
            renderer.applyPatch({ patches: [{ op: "MERGE", targetId: "f", attributes: { formError: "Check the owner" } }] });
            const html = target.outerHTML;
            assert.match(html, /Check the owner/);
            assert.match(html, /id="owner__input" value="Eva Schmid"/);
            assert.match(html, /<input type="hidden" name="owner" value="u7"/);
            assert.match(html, /<span class="sui-chip" data-value="travel"><span class="sui-chip-label">Travel<\/span>/);
        } finally {
            delete globalThis.document;
        }
    });
});

// ── The form sees it ────────────────────────────────────────────────────────

describe("what the form submits", () => {
    /** A form with a chip field and a save button, on a real bus over the stub DOM. */
    function chipForm(values) {
        const control = new Input("input", { id: "tags__input", type: "text", value: "", "data-sui-suggest": "/s", "data-sui-suggest-mode": "many" });
        const chipEls = values.map(v => new El("span", { class: "sui-chip", "data-value": v }, [
            new Input("input", { type: "hidden", name: "tags", value: v, "data-sui-type": "MULTISELECT" }),
        ]));
        const chips = new El("div", { class: "sui-chips", "data-sui-chips": "tags" }, [...chipEls, control]);
        const tagField = new El("div", { class: "sui-field", id: "tags", "data-field": "tags" }, [chips]);
        const save = new El("button", {
            id: "save", "data-action": "save",
            "data-trigger": JSON.stringify({ url: "/save", method: "POST", behavior: "APPLY_RESPONSE" }),
        });
        const form = new El("form", { id: "f", "data-sui": "form" }, [tagField, new El("div", { class: "sui-form-footer" }, [save])]);
        const root = new El("div", { id: "root" }, [form]);
        const page = new El("body", {}, [root]);
        const all = new Map();
        const index = (el) => { if (el.id) all.set(el.id, el); el.children.forEach(index); };
        index(page);
        globalThis.window = {
            location: { pathname: "/", search: "", href: "http://localhost/" },
            addEventListener() { }, innerWidth: 1000, innerHeight: 800,
            history: { pushState() { }, replaceState() { } }, getComputedStyle: () => ({ position: "static" }),
        };
        globalThis.document = {
            body: page, getElementById: (id) => all.get(id) ?? null, createElement,
            querySelectorAll: (s) => page.querySelectorAll(s), querySelector: (s) => page.querySelector(s),
            addEventListener() { }, removeEventListener() { }, dispatchEvent() { return true; },
        };
        globalThis.requestAnimationFrame = (fn) => { fn(); return 0; };
        globalThis.getComputedStyle = () => ({ overflowY: "visible", overflowX: "visible", position: "static" });
        const renderer = {
            render: () => "", seedModels() { }, applyPatch() { }, mount() { },
            showLoading() { }, hideLoading() { }, setInputReader() { }, tree: () => null,
        };
        const requests = [];
        const bus = new SuiEventBus(renderer, root);
        bus.setFetcher(async (url, init) => {
            requests.push({ url, body: init?.body ? JSON.parse(init.body) : null });
            return { ok: true, status: 200, headers: { get: () => "application/json" }, json: async () => ({ patches: [] }), text: async () => "{}" };
        });
        return { bus, requests, chips };
    }

    const settle = () => new Promise(r => setTimeout(r, 10));

    test("the chips as a list, and no chips as an empty one", async () => {
        const two = chipForm(["urgent", "later"]);
        await two.bus.perform({ action: "save" });
        await settle();
        assert.deepEqual(two.requests[0].body.tags, ["urgent", "later"]);

        const none = chipForm([]);
        await none.bus.perform({ action: "save" });
        await settle();
        assert.deepEqual(none.requests[0].body.tags, []);
    });

    test("perform fills a chip field in as chips", async () => {
        const f = chipForm(["urgent"]);
        const result = await f.bus.perform({ fields: { tags: ["later", "urgent"] }, action: "save" });
        await settle();
        assert.equal(result.ok, true);
        assert.deepEqual(f.chips.querySelectorAll(".sui-chip").map(c => c.getAttribute("data-value")), ["later", "urgent"]);
        assert.deepEqual(f.requests[0].body.tags, ["later", "urgent"]);
        // Nothing moved, nothing said.
        assert.equal(setSuggestPicks(f.chips, ["later", "urgent"]), null);
    });
});
