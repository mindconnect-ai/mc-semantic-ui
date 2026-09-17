/**
 * The CSRF token rides along on exactly the requests that need it: unsafe
 * methods to this page's own origin, when the page carries a token — and on
 * nothing else.
 *
 * Runs against the compiled output in target/ts-dist, with a document stubbed
 * just far enough for csrf.ts: a cookie string, meta tags by name, a location.
 * The fetch under test is a recorder, so each test reads the headers that
 * would have gone out.
 */
import { test, describe, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const DIST = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), "../../../target/ts-dist");

let withCsrf, findCsrfToken;

function page({ cookie = "", meta = {} } = {}) {
    globalThis.location = new URL("https://app.example/admin/agents");
    globalThis.document = {
        cookie,
        querySelector: selector => {
            const name = /meta\[name="([^"]+)"\]/.exec(selector)?.[1];
            return name && name in meta ? { content: meta[name] } : null;
        },
    };
}

function recorder() {
    const calls = [];
    const fetcher = async (input, init) => {
        calls.push({ input, init, headers: new Headers(init?.headers) });
        return new Response("{}");
    };
    return { calls, fetcher };
}

describe("csrf", () => {
    before(async () => {
        ({ withCsrf, findCsrfToken } = await import(path.join(DIST, "csrf.js")));
    });

    beforeEach(() => page());

    test("the XSRF-TOKEN cookie goes out as X-XSRF-TOKEN on a POST", async () => {
        page({ cookie: "JSESSIONID=abc; XSRF-TOKEN=tok%3D1" });
        const { calls, fetcher } = recorder();

        await withCsrf(fetcher)("/ui/agents/save", { method: "POST", headers: { "Content-Type": "application/json" } });

        assert.equal(calls[0].headers.get("X-XSRF-TOKEN"), "tok=1");
        // The caller's own headers survive the wrap.
        assert.equal(calls[0].headers.get("Content-Type"), "application/json");
    });

    test("meta tags win over the cookie and name their own header", async () => {
        page({ cookie: "XSRF-TOKEN=from-cookie", meta: { _csrf: "from-meta", _csrf_header: "X-CSRF-TOKEN" } });
        const { calls, fetcher } = recorder();

        await withCsrf(fetcher)("/ui/x", { method: "DELETE" });

        assert.equal(calls[0].headers.get("X-CSRF-TOKEN"), "from-meta");
        assert.equal(calls[0].headers.get("X-XSRF-TOKEN"), null);
    });

    test("a GET, a page without a token, or another origin gets nothing", async () => {
        page({ cookie: "XSRF-TOKEN=t" });
        const { calls, fetcher } = recorder();
        const send = withCsrf(fetcher);

        await send("/ui/agents");                                        // GET
        await send("https://evil.example/steal", { method: "POST" });    // foreign origin
        page();                                                           // no token at all
        await send("/ui/agents/save", { method: "POST" });

        for (const call of calls) {
            assert.equal(call.headers.get("X-XSRF-TOKEN"), null, String(call.input));
        }
    });

    test("an absolute URL to this origin counts as same-origin", async () => {
        page({ cookie: "XSRF-TOKEN=t" });
        const { calls, fetcher } = recorder();

        await withCsrf(fetcher)("https://app.example/ui/save", { method: "PUT" });

        assert.equal(calls[0].headers.get("X-XSRF-TOKEN"), "t");
    });

    test("a header the caller set is not overwritten, and false turns it off", async () => {
        page({ cookie: "XSRF-TOKEN=t" });
        const { calls, fetcher } = recorder();

        await withCsrf(fetcher)("/ui/save", { method: "POST", headers: { "X-XSRF-TOKEN": "mine" } });
        await withCsrf(fetcher, false)("/ui/save", { method: "POST" });

        assert.equal(calls[0].headers.get("X-XSRF-TOKEN"), "mine");
        assert.equal(calls[1].headers.get("X-XSRF-TOKEN"), null);
    });

    test("cookie and header can be renamed", async () => {
        page({ cookie: "csrftoken=d" });
        const { calls, fetcher } = recorder();

        await withCsrf(fetcher, { cookieName: "csrftoken", headerName: "X-CSRFToken" })("/save", { method: "POST" });

        assert.equal(calls[0].headers.get("X-CSRFToken"), "d");
        assert.equal(findCsrfToken(), null);   // the default cookie name finds nothing here
    });
});
