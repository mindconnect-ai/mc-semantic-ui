# mc-sui-shop-node-demo

A **pure Node.js / Express + Vite** demo for semantic-ui — a simplified version
of the shop demo showing just the product list. It proves the point that
semantic-ui needs no Java: the wire format is plain JSON, and the renderer that
draws it is the same npm package in the browser and on the server. The Express
side builds `UiPage` trees and renders them to HTML for a browser navigation;
the bus then takes that page over.

It is also the repo's only consumer of the client as an **npm package** rather
than as JAR resources — so it is where a broken `exports` map, a missing type
declaration or an asset that stops bundling shows up first.

## What it shows

- A product list with search and a per-row **Delete** action.
- Clicking a SKU opens a product **detail dialog** (`GET /products/:id` returns
  a `UiPage` carrying the list *and* the dialog, so the modal has the list
  behind it whether you clicked into it or arrived at the URL directly).
- The exact same JSON shape a Spring Boot controller would return — and the
  server builds it against `UiPage` and friends, the very types the browser
  renders with, so a malformed tree is a compile error rather than a blank page.
- **One URL per page, serving both the page and its data.** `/products/p-1` in
  the address bar, a reload, a bookmark and a shared search link all work.
- TypeScript and Vite consuming `@mindconnect-ai/mc-semantic-ui-core` as an
  ordinary dependency: bare-specifier imports, the stylesheet imported like any
  other CSS, and the icon sprite emitted as a build asset.

## Run it

The client ships compiled, so the core module's TypeScript has to be built once:

```bash
# 1. Compile the core module (one-time, from the repo)
cd core/mc-semantic-ui-core
npm install && npm run build

# 2. Start the demo
cd ../../demo/mc-sui-shop-node-demo
npm install
npm run dev
# then open http://localhost:3000
```

`npm run dev` runs Express with Vite as middleware: one process, one port, and
hot reload for the client. `npm start` builds the bundle and serves it as static
files instead.

Port 3000 taken? Set another one — `PORT=3010 npm run dev`.

## Server-side rendering, and why it matters here

A browser navigation gets **finished HTML**, not an empty shell: the server
draws the `UiPage` with the very renderer the browser runs — `render()` returns
a string and touches no DOM, so it works in plain Node with no jsdom. There is
no second implementation to keep in step, which is the part Java needs 45 tests
for.

The client then **takes the page over instead of fetching it again**: it
attaches the bus to `#sui-root`, seeds the model index from the
`<script id="sui-model">` blob the server parked in the page, and leaves the
markup alone. Re-fetching would throw away exactly what SSR bought.

For indexing, one distinction decides everything:

| Node | Markup | A crawler |
| --- | --- | --- |
| `link` | `<a href="/products/p-1">` | follows it |
| `action` | `<button data-trigger=…>` | never sees it |

Googlebot runs JavaScript but does not click buttons. **Navigation that should
be indexed belongs in `link` nodes; `action` is for mutations.** That is why the
SKU column uses a `cellTemplate` link — without it the crawl graph would be
empty and no page would ever discover another.

The rest is the ordinary SEO checklist, and none of it lives in `UiPage` —
the model describes the UI, not how it is indexed, so `src/pages.ts` derives it
next to the content: a `<title>` and `<meta description>` per URL, a
query-free `<link rel="canonical">` so `?q=…` views do not get indexed as pages
of their own, and a real `404` status instead of a `200` carrying an apology.

## One URL, two answers

A page and its data share a URL; who is asking decides what comes back.

| Request | Answer |
| --- | --- |
| `GET /products/p-1` with `Accept: text/html` | the page, server-rendered |
| `GET /products/p-1` with `Accept: application/json` | the `UiPage` for that product |

A browser navigation gets the shell; the event bus, which sets
`Accept: application/json` on every request, then asks the very same URL for
JSON. So the client needs no table mapping pages to endpoints — it navigates to
`location.pathname` and the server works out the rest. This is the Node
counterpart of the Spring `OncePerRequestFilter` that forwards HTML navigations
to the shell.

## How it works

- `src/pages.ts` — builds the `UiPage` trees, typed against the client package.
  No renderer is imported here; a page is data.
- `src/server.ts` — Express. Holds products in memory (no Postgres), serves the
  pages as JSON, and renders them to HTML for a browser navigation.
- `src/document.ts` — the Node counterpart of Java's
  `UiPageHtmlMessageConverter`: fills the shell's four slots (head, root,
  dialogs, model seed).
- `src/main.ts` — the browser entry: attaches the renderer and the event bus to
  `#sui-root`, and only fetches a page when the server did not render one.
- `index.html` — the shell, with the four `<!--sui-*-->` slots.
- `vite.config.ts` — one setting, and only because this demo links the client
  from the module next door; see the comment there.

The Java shop demo (`demo/mc-sui-shop-spring-demo`) is the full version with Postgres,
SSR, themes and CRUD. This one is the minimal cross-language counterpart.
