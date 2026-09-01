---
title: 'Quickstart: Node.js backend'
---

# Quickstart — Node.js backend + browser client

The wire format is **plain JSON**, so a Node backend can get away with
returning the `UiPage` shape and letting the browser renderer paint it — that
is the whole of steps 1–4 below.

It can also do more than that. The renderer ships as an npm package and
`render()` returns an HTML string without touching the DOM, so the same Node
process can render a page server-side; see [step 6](#6-render-on-the-server).

## 1. Emit the tree from Express

```js
import express from "express";
const app = express();

// The page builder — plain data, reused by every endpoint below.
const productListPage = (q = "") => ({
  type: "page",
  navigate: "/products",
  node: {
    type: "table", id: "products",
    columns: [
      { type: "column", id: "c-sku",  label: "SKU",   dataKey: "sku"  },
      { type: "column", id: "c-name", label: "Name",  dataKey: "name" },
      { type: "column", id: "c-price",label: "Price", dataKey: "price"}
    ],
    rows: db.findProducts(q).map(r => ({ type: "row", id: r.id, data: r })),
    rowActions: [
      { type: "action", id: "delete", label: "Delete", style: "DANGER",
        confirm: "Delete this product?",
        onClick: { behavior: "APPLY_RESPONSE", method: "DELETE",
                   url: "/products/{id}" } }
    ]
  }
});

app.get("/products", (req, res) => res.json(productListPage(req.query.q)));
```

The shape is the whole contract — the `type` discriminator plus each node's
fields. No ORM, no framework, no Java.

## 2. Handle the trigger

The row action fires `DELETE /products/<id>` (`{id}` is substituted from the
row). **Answer it with the same shape** — the client applies whatever comes
back, so returning the refreshed list re-renders the table:

```js
app.delete("/products/:id", (req, res) => {
  db.remove(req.params.id);
  res.json(productListPage());        // → client re-renders the list
});
```

That round-trip — node emits a trigger, server answers with a `UiPage` (or a
`UiPatch` for a partial update) — is the whole interaction model. See
**[Triggers & actions](./triggers.md)** for the other behaviors.

## 3. Serve the browser runtime

The client imports `/sui/renderer.js`, so your server has to serve it. Copy the
compiled bundle out of `mc-semantic-ui-core` once, then hand it to
`express.static`:

```js
// after: cd core/mc-semantic-ui-core && npm install && npm run build
// copy core/mc-semantic-ui-core/target/ts-dist/** + sui*.css → public/sui/
app.use("/sui", express.static("public/sui"));
app.use(express.static("public"));    // your index.html shell

app.listen(3000);
```

(The runnable demo below ships a `scripts/copy-client.js` that does the copy.
Alternatively, load the runtime from a [CDN](./cdn-assets.md) and skip this.)

## 4. Boot the client

Let the **event bus** fetch the page — it unwraps the `UiPage` envelope and
wires every trigger. (`renderer.mount()` takes a *node*, not a `UiPage`; the bus
handles the envelope for you.)

```html
<div id="app"></div>
<script type="module">
  import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
  import { SuiEventBus } from "/sui/eventbus.js";

  const host = document.getElementById("app");
  const renderer = installDefaultHandlers(new SuiRenderer(host));
  const bus = new SuiEventBus(renderer, host);

  bus.navigate("/products");   // fetches the UiPage JSON and renders it
</script>
```

## 5. Make a reload work

Step 1 has `/products` returning JSON. That is right for the event bus — and
wrong for the address bar. Press F5 on `/products` and the browser makes a
*document* request to the same URL, so it gets **raw JSON on screen** instead of
your page.

Express solves it the same way the Java side does: look at what the client
asked for.

```js
app.get("/products", (req, res) => {
  const page = productListPage(req.query.q);
  // The bus sends Accept: application/json; a browser address-bar request
  // sends Accept: text/html. One route, both answers.
  if (req.accepts("html")) {
    return res.sendFile(resolve("public/index.html"));   // the shell boots and routes
  }
  res.json(page);
});
```

The shell then boots and asks for the same URL as JSON, so the user lands on the
page they linked to. If you'd rather not repeat that per route, put it last as a
catch-all:

```js
// Every unmatched GET that wants HTML gets the shell. Keep it AFTER your API
// routes and after express.static, or it will swallow them.
app.get(/.*/, (req, res, next) =>
  req.accepts("html") ? res.sendFile(resolve("public/index.html")) : next());
```

:::note Serving the shell, or the page itself
Answering with the shell is the simple option: the client boots, asks the same
URL for JSON, and the user lands where they linked to. The cost is one extra
round trip and an empty first paint.

With SSR enabled the Java side instead answers the document request with real
HTML from the same controller — see
[server-side rendering](./server-side-rendering.md#reloading-a-deep-url). Node
can do that too; [step 6](#6-render-on-the-server) replaces the `sendFile` above
with a rendered page.
:::

## 6. Render on the server

Step 5 sends the shell and lets the client fetch. If you want the content in the
response instead — for a faster first paint, or because a crawler has to read it
— render it here. `SuiRenderer.render()` returns an HTML string and touches no
DOM, so it runs in plain Node with no jsdom:

```bash
npm install @mindconnect-ai/mc-semantic-ui-core
```

```js
import { createDefaultRenderer, setIconSpriteUrl } from "@mindconnect-ai/mc-semantic-ui-core";

// In Node `import.meta.url` is a file: URL, so the icon renderer's default
// would put an absolute path off this machine's disk into every page it sends.
setIconSpriteUrl("/sui/icons.svg");

const renderer = createDefaultRenderer();

function document(page, meta) {
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>${meta.title}</title>
<meta name="description" content="${meta.description}">
<link rel="canonical" href="${meta.canonical}">
<link rel="stylesheet" href="/sui/sui.css"></head>
<body>
<div id="sui-root">${renderer.render(page.node)}</div>
<script type="application/json" id="sui-model">${JSON.stringify(page.node).replace(/<\//g, "<\\/")}</script>
<script type="module" src="/app.js"></script>
</body></html>`;
}

app.get("/products", (req, res) => {
  const page = productListPage(req.query.q);
  if (req.accepts("html")) return res.type("html").send(document(page, metaFor(req)));
  res.json(page);
});
```

Because this is the renderer the browser runs, there is nothing to keep in
step: the server's markup and the SPA's are the same code's output.

Two pieces of that template are load-bearing:

- **`<div id="sui-root">`** is what the bus attaches to.
- **`<script id="sui-model">`** is the tree the bus seeds its model index from.
  A `MERGE` patch changes a few fields of a node and leaves the rest alone, so
  the client has to know what the rest were — and it never drew this page.

Then have the client **take the page over instead of fetching it again**;
re-fetching would throw away what you just paid for:

```js
const host = document.getElementById("sui-root");
const renderer = installDefaultHandlers(new SuiRenderer(host));
const bus = new SuiEventBus(renderer, host);   // seeds from #sui-model
if (host.childElementCount === 0) bus.navigate(location.pathname + location.search);
```

### If you are doing this for SEO

One distinction decides whether your pages are discoverable at all:

| Node | Markup | A crawler |
|---|---|---|
| `link` | `<a href="/products/p-1">` | follows it |
| `action` | `<button data-trigger=…>` | never sees it |

Googlebot runs JavaScript, but it does not click buttons. **Navigation that
should be indexed belongs in `link` nodes** — including a table's
`cellTemplate` — while `action` stays for mutations. Get this wrong and every
page renders beautifully and none of them links to another.

The rest is the ordinary checklist, and none of it lives in `UiPage`: the model
describes the UI, not how it is indexed. Derive a `<title>` and
`<meta description>` per URL, set a query-free `<link rel="canonical">` so
`?q=…` views are not indexed as pages of their own, and return a real `404`
rather than a `200` carrying an apology — a search engine that indexes a
soft-404 keeps it.

You do **not** need the no-JS action rendering the JVM side does (`GET` as a
real anchor, `DELETE` as a form). That is for people browsing without
JavaScript; no crawler submits forms.

## What you need — and what you don't

- **Needed:** produce the JSON tree (the `type` discriminator plus each node's
  fields) and return it as `application/json`. The browser `SuiRenderer` does
  the rest.
- **Not needed:** the Java library, an ORM, or any framework. The shape is the
  contract — Go, Python, Rust and a static `.json` file all work the same way.
- **Optional, and Node-only among the non-JVM backends:** rendering the page
  server-side, because the renderer is an npm package. A Go or Python backend
  stays on the SPA path, or shells out to Node for the render.
- **Still JVM-only:** the Handlebars templates and the no-JS action rendering
  that turns a `GET` into an anchor and a `DELETE` into a form. Node renders the
  same nodes, but its buttons need the bus.

## The client is identical

The `index.html` shell and `app.js` from [Build an app](./building-an-app.md)
work unchanged against a Node backend — they only ever see JSON.

For a [UI island](./ui-island.md) there is no bus in the loop, so have the route
return a **bare node** and `renderer.mount(node)` it — `mount()` takes a node,
not a `UiPage` envelope.

## Runnable demo

A complete version lives in `demo/mc-sui-shop-node-demo` — TypeScript and Vite
over a pure Node.js / Express server that holds products in memory and serves
the list (search, per-row delete, detail dialog) both ways: as `UiPage` JSON for
the bus, and server-rendered for a browser navigation, with per-page title,
description and canonical.

```bash
# build the core client bundle once, then run the Node demo
cd core/mc-semantic-ui-core && npm install && npm run build
cd ../../demo/mc-sui-shop-node-demo && npm install && npm run dev
# open http://localhost:3000
```

`npm run dev` runs Express with Vite as middleware — one process, one port, hot
reload. `npm start` builds the bundle and serves it instead. To see what a
crawler sees, ask for HTML and read the response:

```bash
curl -s -H "Accept: text/html" http://localhost:3000/products/p-1
```

It's the cross-language counterpart to the Java [shop demo](./shop-demo.md).

## Next

- **[Build an app](./building-an-app.md)** — the shell, the routes, the patterns,
  and what is / isn't available off the JVM.
- The building blocks: **[Node vocabulary](./node-vocabulary.md)**,
  **[Triggers & actions](./triggers.md)**, **[Forms & validation](./forms.md)**.
