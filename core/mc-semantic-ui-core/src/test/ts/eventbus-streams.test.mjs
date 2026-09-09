/**
 * One shown stream costs one connection — the lifecycle of the stream
 * registry in SuiEventBus.
 *
 * The bus is built against a stand-in DOM: a root whose "mounted" stream
 * targets are whatever the fake renderer was last asked to mount, and a
 * fetcher that hands out server-sent event bodies the test writes into by
 * hand. Runs against the compiled output in target/ts-dist, like the other
 * TypeScript tests.
 */
import { test, describe, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

// ── A DOM just big enough for the bus ───────────────────────────────────────

const mounted = new Set();           // stream targets the current page shows

function element(id = "") {
    return { id, className: "", innerHTML: "", addEventListener() {}, appendChild() {},
             querySelector() { return null; }, querySelectorAll() { return []; } };
}

function installFakeDom() {
    globalThis.window = { location: { pathname: "/page", search: "" }, addEventListener() {} };
    globalThis.document = {
        getElementById: (id) => mounted.has(id) ? element(id) : null,
        createElement: () => element(),
        body: element("body"),
        querySelectorAll: () => [],
    };
}

const root = {
    ...element("root"),
    querySelector(selector) {
        const m = /^\[data-sui-stream-target="(.*)"\]$/.exec(selector);
        return m && mounted.has(m[1]) ? element(m[1]) : null;
    },
};

/** Swaps the DOM at once, unless a test asks for the view-transition delay. */
let mountDelayMs = 0;
const renderer = {
    mount(node) {
        const swap = () => { mounted.clear(); for (const t of node.targets ?? []) mounted.add(t); };
        if (mountDelayMs === 0) swap(); else setTimeout(swap, mountDelayMs);
    },
    applied: [],
    applyPatch(patch) { this.applied.push(patch.n); }, render() { return ""; }, seedModels() {},
    showLoading() {}, hideLoading() {},
};

// ── A server the test drives ────────────────────────────────────────────────

/** Every request the bus made, in order; each carries the tools to answer it. */
let requests;

function fakeFetcher(url, init) {
    const request = { url, init, aborted: false };
    let controller;
    const body = new ReadableStream({ start(c) { controller = c; } });
    init.signal.addEventListener("abort", () => {
        request.aborted = true;
        try { controller.error(new DOMException("aborted", "AbortError")); } catch { /* closed */ }
    });
    const headers = new Map([["sui-stream-channel", request.channel ?? ""]]);
    request.respond = (channel) => {
        if (channel) headers.set("sui-stream-channel", channel);
        request.resolve({
            ok: true, status: 200, body,
            headers: { get: (name) => headers.get(name.toLowerCase()) || null },
        });
    };
    request.write = (text) => controller.enqueue(new TextEncoder().encode(text));
    request.end = () => { try { controller.close(); } catch { /* already errored by an abort */ } };
    requests.push(request);
    return new Promise(resolve => { request.resolve = resolve; });
}

/** Lets the bus's reader loops and promise chains run. */
const settle = (ms = 10) => new Promise(r => setTimeout(r, ms));

function page(...targets) {
    return {
        node: { targets },
        activeStreams: targets.map(channelId => ({
            channelId, resumeUrl: `/streams/${channelId}`, returnHref: "/page", label: "Chat",
        })),
    };
}

const handles = (bus) => bus.activeStreams().map(h => ({ channelId: h.channelId, state: h.state }));

describe("SuiEventBus stream lifecycle", () => {
    let SuiEventBus, bus;

    beforeEach(async () => {
        installFakeDom();
        mounted.clear();
        mountDelayMs = 0;
        renderer.applied = [];
        requests = [];
        ({ SuiEventBus } = await import(path.join(DIST, "eventbus.js")));
        bus = new SuiEventBus(renderer, root)
            .setFetcher(fakeFetcher)
            .setHistoryEnabled(false)
            .setLoadingPolicy("manual");
    });

    test("a reconnect is registered before its response arrives, so a second render opens no second connection", async () => {
        bus.applyPage(page("msg-list-1"));
        await settle();
        assert.deepEqual(handles(bus), [{ channelId: "msg-list-1", state: "idle" }]);
        bus.applyPage(page("msg-list-1"));           // response still pending
        await settle();
        assert.equal(requests.length, 1);
        requests[0].respond();
        await settle();
        assert.equal(requests.length, 1);
        assert.equal(bus.activeStreams().length, 1);
    });

    test("a heartbeat comment does not promote an idle stream; an event does", async () => {
        bus.applyPage(page("msg-list-1"));
        await settle();
        requests[0].respond();
        await settle();
        requests[0].write(":hb\n\n:attached\n\nid: 7\n\n");
        await settle();
        assert.equal(bus.activeStreams()[0].state, "idle");
        assert.equal(bus.activeStreams()[0].lastSeq, 7);
        requests[0].write("id: 8\nevent: patch\ndata: {\"patches\":[]}\n\n");
        await settle();
        assert.equal(bus.activeStreams()[0].state, "running");
        assert.equal(bus.activeStreams()[0].lastSeq, 8);
    });

    test("leaving the page closes an idle stream; coming back reopens it", async () => {
        bus.applyPage(page("msg-list-1"));
        await settle();
        requests[0].respond();
        await settle();
        bus.applyPage(page());                         // a page without the chat
        await settle();
        assert.equal(requests[0].aborted, true);
        assert.deepEqual(handles(bus), []);
        bus.applyPage(page("msg-list-1"));             // back to the chat
        await settle();
        assert.equal(requests.length, 2);
        assert.deepEqual(handles(bus), [{ channelId: "msg-list-1", state: "idle" }]);
    });

    test("a running stream is closed on leaving the page too; the return reopens it", async () => {
        bus.applyPage(page("msg-list-1"));
        await settle();
        requests[0].respond();
        await settle();
        requests[0].write("event: patch\ndata: {\"patches\":[]}\n\n");
        await settle();
        assert.equal(bus.activeStreams()[0].state, "running");
        bus.applyPage(page());
        await settle();
        assert.equal(requests[0].aborted, true);
        assert.deepEqual(handles(bus), []);
        bus.applyPage(page("msg-list-1"));
        await settle();
        assert.equal(requests.length, 2, "reopened from the page's resume URL");
    });

    test("which streams a page shows is the page's word, not the DOM's — a swap that lands a frame later changes nothing", async () => {
        mountDelayMs = 20;                             // what a view transition does
        bus.applyPage(page("msg-list-A"));
        await settle(30);
        requests[0].respond();
        bus.applyPage(page("msg-list-B"));
        await settle(30);
        requests[1].respond();
        bus.applyPage(page("msg-list-C"));
        await settle(30);
        requests[2].respond();
        await settle();
        assert.equal(requests[0].aborted, true, "A closed when B arrived");
        assert.equal(requests[1].aborted, true, "B closed when C arrived");
        assert.deepEqual(handles(bus), [{ channelId: "msg-list-C", state: "idle" }]);
    });

    test("an event that arrives before its target is drawn waits, and lands in order once it is", async () => {
        mountDelayMs = 40;                              // the page is announced, drawn later
        bus.applyPage(page("msg-list-1"));
        requests[0].respond();
        await settle();
        requests[0].write("event: patch\ndata: {\"n\":1}\n\n");
        await settle();
        assert.deepEqual(renderer.applied, [], "nothing lands on a page that is not drawn");
        assert.equal(bus.activeStreams()[0].bufferedEvents.length, 1);
        await settle(50);                               // the swap lands
        requests[0].write("event: patch\ndata: {\"n\":2}\n\n");
        await settle();
        assert.deepEqual(renderer.applied, [1, 2]);
        assert.equal(bus.activeStreams()[0].bufferedEvents.length, 0);
    });

    test("a message's POST stream takes over the channel and closes the connection it replaces", async () => {
        bus.applyPage(page("msg-list-1"));
        await settle();
        requests[0].respond();
        await settle();
        const posting = bus.dispatch({ url: "/chat", behavior: "STREAM", method: "POST" });
        await settle();
        requests[1].respond("msg-list-1");
        await posting;
        await settle();
        assert.equal(requests[0].aborted, true, "the GET connection is closed");
        assert.deepEqual(handles(bus), [{ channelId: "msg-list-1", state: "running" }]);
        // The closed GET stream ended idle; it must not have taken the successor with it.
        assert.deepEqual(handles(bus), [{ channelId: "msg-list-1", state: "running" }]);
        // The page renders again while the POST stream is live: no third connection.
        bus.applyPage(page("msg-list-1"));
        await settle();
        assert.equal(requests.length, 2);
    });
});
