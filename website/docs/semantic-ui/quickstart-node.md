---
title: 'Quickstart: Node.js backend'
---

# Quickstart — Node.js backend + browser client

The wire format is **plain JSON**, so there is **no semantic-ui code on the
server** — your Node backend just returns the `UiPage` shape, and the browser
renderer paints it. (SSR to HTML is JVM-only; a Node backend uses the SPA path.)

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

:::note Why Spring Boot doesn't need this
With SSR enabled the Java side answers the document request with real HTML from
the same controller — see [server-side rendering](./server-side-rendering.md#reloading-a-deep-url).
Node has no server renderer, so the shell is what answers instead.
:::

## What you need — and what you don't

- **Needed:** produce the JSON tree (the `type` discriminator plus each node's
  fields) and return it as `application/json`. The browser `SuiRenderer` does
  the rest.
- **Not needed:** the Java library, an ORM, or any framework. The shape is the
  contract — Go, Python, Rust and a static `.json` file all work the same way.
- **Not available outside the JVM:** server-rendered HTML. That path uses the
  Java Handlebars renderer, which is why the reload section above exists.

## The client is identical

The `index.html` shell and `app.js` from [Build an app](./building-an-app.md)
work unchanged against a Node backend — they only ever see JSON.

For a [UI island](./ui-island.md) there is no bus in the loop, so have the route
return a **bare node** and `renderer.mount(node)` it — `mount()` takes a node,
not a `UiPage` envelope.

## Runnable demo

A complete version lives in `demo/mc-sui-node-demo` — a pure Node.js / Express
server that holds products in memory and serves the list (search, per-row
delete, detail dialog) as `UiPage` JSON, reusing the unchanged `SuiRenderer`:

```bash
# build the core client bundle once, then run the Node demo
cd core/mc-semantic-ui-core && npm install && npm run build
cd ../demo/mc-sui-node-demo && npm install && npm start
# open http://localhost:3000
```

It's the cross-language counterpart to the Java [shop demo](./shop-demo.md).

## Next

- **[Build an app](./building-an-app.md)** — the shell, the routes, the patterns,
  and what is / isn't available off the JVM.
- The building blocks: **[Node vocabulary](./node-vocabulary.md)**,
  **[Triggers & actions](./triggers.md)**, **[Forms & validation](./forms.md)**.
