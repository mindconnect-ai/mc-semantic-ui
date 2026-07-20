---
title: How it works
sidebar_position: 2
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# How it works

Five steps, each one building on the last. By step 3 you have a working,
interactive app **with no backend at all** — the backend only shows up in step 4,
when you actually need it.

| Step | You add | You still don't need |
|---|---|---|
| **1** | A screen, as data | a server, a build step |
| **2** | The shell that draws it | a framework, a bundler |
| **3** | Interaction, client-side | a server |
| **4** | A backend that emits the same JSON | a frontend codebase |
| **5** | A node type of your own | a fork |

## Step 1 — Describe a screen

A screen is a tree of typed nodes. Nothing renders it yet — this is just data,
and it's the only thing you author:

```js
const productsTable = {
  type: "table", id: "products", title: "Products",
  columns: [
    { type: "column", id: "c-sku",   label: "SKU",   dataKey: "sku"   },
    { type: "column", id: "c-name",  label: "Name",  dataKey: "name"  },
    { type: "column", id: "c-price", label: "Price", dataKey: "price" }
  ],
  rows: [
    { type: "row", id: "p1", data: { sku: "A-1", name: "Widget",    price: "€ 19.00" } },
    { type: "row", id: "p2", data: { sku: "B-2", name: "Gadget",    price: "€ 49.00" } },
    { type: "row", id: "p3", data: { sku: "C-3", name: "Doohickey", price: "€ 7.50"  } }
  ]
};
```

Every node has a `type` (which renderer draws it) and an `id` (how you address it
later — for patches, for form values, for tests). See the
[node vocabulary](./node-vocabulary.md) for the full set.

## Step 2 — Draw it

The shell is one HTML file: a stylesheet, a host element, a renderer. No npm, no
bundler, no framework.

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <link rel="stylesheet" href="/sui/sui.css">
</head>
<body>
  <main id="app"></main>

  <script type="module">
    import { createDefaultRenderer } from "/sui/renderer.js";

    const host = document.getElementById("app");
    const renderer = createDefaultRenderer().attach(host);

    renderer.mount(productsTable);   // the tree from step 1
  </script>
</body>
</html>
```

Open the file. You have a styled, responsive table.

`createDefaultRenderer()` registers a render function for every built-in node
type, `attach(host)` binds it to a DOM element, `mount(node)` draws a tree into
it. That is the entire renderer API for a static screen.

The rates differ, which is the thing to internalise: you **create and attach
once**, at startup, and **mount every time the screen changes**. The
[renderer reference](./runtime.md) covers the rest of the lifecycle — including
the two mistakes everyone makes once.

:::tip Where do the files come from?
`/sui/sui.css` and `/sui/renderer.js` are the published runtime — load them from
a CDN, or copy them next to your HTML. See [CDN assets](./cdn-assets.md).
:::

## Step 3 — Make it interactive — still no backend

`mount()` draws a static tree. To make things *happen*, add the **event bus** and
give a node a **trigger**.

The bus watches the DOM for triggers and runs them. A trigger with behavior
`INVOKE` calls a function you registered in the page, so the whole interaction
stays client-side:

```js
import { createDefaultRenderer } from "/sui/renderer.js";
import { SuiEventBus }           from "/sui/eventbus.js";

const host     = document.getElementById("app");
const renderer = createDefaultRenderer().attach(host);
const bus      = new SuiEventBus(renderer, host);

let cartCents = 0;

// A handler returns a UiPatch — a surgical update to the live tree.
bus.registerClientHandler("addToCart", () => {
  cartCents += 1900;
  return {
    patches: [
      { op: "REPLACE", targetId: "cart-total",
        node: { type: "text", id: "cart-total", text: `€ ${(cartCents / 100).toFixed(2)}` } }
    ],
    toasts: [ { level: "SUCCESS", message: "Added to cart" } ]
  };
});

renderer.mount({
  type: "stack", id: "shop", gap: 12, children: [
    { type: "text", id: "cart-total", text: "€ 0.00" },
    { type: "action", id: "add", label: "Add Widget", style: "PRIMARY",
      onClick: { behavior: "INVOKE", handler: "addToCart" } }
  ]
});
```

That code is running right here — plus a second handler to reset it. No backend,
no build step, nothing on the page but the runtime:

<iframe
  src="/mc-semantic-ui/embed/cart.html"
  title="Live: a client-side UiPatch"
  loading="lazy"
  style={{width: '100%', height: '150px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

Click the button: the text node swaps, a toast appears, and **nothing else on the
page re-renders**. No virtual DOM, no reconciliation — you named the node you
wanted to change.

Two payload shapes cover everything:

- **`UiPage`** — a whole screen. Return it when the user navigates.
- **`UiPatch`** — one or more operations (`REPLACE`, `APPEND`, `CLEAR`,
  `REMOVE`) that target nodes by `id`, plus optional toasts.

A client handler can return either. So can a server — which is the whole point of
the next step.

:::info Steps 1–3 are already a deployable app
A static file, no server process. The
[widget showcase ↗](pathname:///widget-demo/) and the
[visual editor ↗](pathname:///editor/) are both built exactly this way.
:::

## Step 4 — Move the logic to a backend

Nothing about steps 1–3 changes. You swap *who produces the tree*: instead of a
literal in the page, a URL returns it. The trigger behavior changes from `INVOKE`
(call a local function) to `APPLY_RESPONSE` (fetch a URL, apply what comes back):

```js
{ type: "action", id: "add", label: "Add Widget", style: "PRIMARY",
  onClick: { behavior: "APPLY_RESPONSE", method: "POST", url: "/cart/add" } }
```

Boot from a URL instead of a literal, and the bus takes over routing:

```js
bus.start("/products");   // fetches the UiPage and mounts it
```

The server returns the identical wire format. Java gets a typed builder API;
anywhere else you emit the same JSON by hand:

<Tabs groupId="stack">
<TabItem value="spring" label="Java / Spring Boot">

```java
@GetMapping("/products")
public UiPage products() {
    var table = UiTable.of("products", "Products")
            .column(UiTableColumn.of("c-sku",   "SKU",   "sku"))
            .column(UiTableColumn.of("c-name",  "Name",  "name"))
            .column(UiTableColumn.of("c-price", "Price", "price"));
    repo.findAll().forEach(p -> table.row(UiTableRow.of(p.getId(), Map.of(
            "sku", p.getSku(), "name", p.getName(), "price", money(p.getPriceCents())))));
    return UiPage.of("/products", table);
}

@PostMapping("/cart/add")
public UiPatch addToCart() {
    cart.add();
    return UiPatch.of()
            .patch(UiPatch.Operation.replace("cart-total", UiText.of("cart-total", cart.total())))
            .toast(UiToast.success("Added to cart"));
}
```

</TabItem>
<TabItem value="node" label="Node.js / Express">

```js
app.get("/products", async (req, res) => {
  const rows = await db.findProducts();
  res.json({
    type: "page", navigate: "/products",
    node: {
      type: "table", id: "products", title: "Products",
      columns: [
        { type: "column", id: "c-sku",   label: "SKU",   dataKey: "sku"   },
        { type: "column", id: "c-name",  label: "Name",  dataKey: "name"  },
        { type: "column", id: "c-price", label: "Price", dataKey: "price" }
      ],
      rows: rows.map(r => ({ type: "row", id: r.id, data: r }))
    }
  });
});

app.post("/cart/add", (req, res) => {
  const total = cart.add();
  res.json({
    patches: [ { op: "REPLACE", targetId: "cart-total",
                 node: { type: "text", id: "cart-total", text: total } } ],
    toasts:  [ { level: "SUCCESS", message: "Added to cart" } ]
  });
});
```

</TabItem>
</Tabs>

Live below — and this one really is fetching over HTTP. Click **Open** on a row:
the browser issues a `GET`, gets a `UiPage` back, and replaces the screen;
**Back to products** fetches the first page again:

<iframe
  src="/mc-semantic-ui/embed/backend.html"
  title="Live: UiPages fetched over HTTP"
  loading="lazy"
  style={{width: '100%', height: '420px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

The entire page-local script is three lines — a renderer, a bus, and
`bus.navigate("api/products.json")`. Every screen you see came off the wire.

:::note What's serving this
Static `.json` files, because a docs site has no application server. That is
itself the point: the browser side has no idea whether a Spring controller, an
Express route or a file on disk produced the JSON — the wire format is the only
contract. Swap in a real backend and **nothing on this page changes**.
:::

Same tree, same markup, same CSS — the only difference is who assembles it.
That's why validation rules, permissions and formatting live in exactly one
place: next to your data. The browser never learns your domain.

Each stack has a quickstart: **[Spring Boot](./quickstart-spring-boot.md)**,
**[Node.js](./quickstart-node.md)**, **[client-only](./quickstart-client.md)**.

## Step 5 — Add a node type of your own

The vocabulary is not a closed set. When a screen needs something the built-ins
don't cover, register a render function for a new `type` — one function returning
an HTML string:

```js
const renderer = createDefaultRenderer().attach(host);

// A "rating" node: { type: "rating", id: "r1", stars: 4 }
renderer.register("rating", (node) => {
  const filled = "★".repeat(node.stars) + "☆".repeat(5 - node.stars);
  return `<span class="my-rating" id="${node.id}">${filled}</span>`;
});

renderer.mount({ type: "rating", id: "r1", stars: 4 });
```

That is the entire extension mechanism. Your node is now a first-class citizen:
it can sit anywhere in a tree, it can be a patch target by `id`, and a backend
can emit it.

A handler receives the renderer as its second argument, so a container node can
recurse into its children:

```js
renderer.register("card", (node, r) =>
  `<div class="my-card" id="${node.id}">
     <h3>${node.title}</h3>
     ${node.children.map(child => r.render(child)).join("")}
   </div>`);
```

Both of those are running below — a custom `card` containing a built-in `text`
and a custom `rating`. **Rate it** fires a `REPLACE` patch that targets the
`rating` node by `id`, which is the proof that a node you invented is a
first-class citizen of the tree:

<iframe
  src="/mc-semantic-ui/embed/custom-node.html"
  title="Live: custom node types"
  loading="lazy"
  style={{width: '100%', height: '220px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

:::tip Always recurse via `r.render(child)`
Never call another handler directly. Going through the renderer is what lets an
application override any node type and have that override apply everywhere in
the tree.
:::

`register()` also **replaces** a built-in: register your own `"table"` and every
table in every screen uses it — including the ones your backend emits. The
[diagram extension](./diagram-extension.md) is the same mechanism at full size —
one `register()` call for a `diagram` node, backed by a custom element that
paints SVG.

<div class="sui-cta">

## Or skip the typing

Every step above can be done in the **visual editor** instead — it runs in your
browser and edits exactly the tree from step 1, with a live preview next to it.

When you like what you see, **export a runnable app**: static HTML, a Spring Boot
Maven project, or an Express app, downloaded as a zip. What comes out is ordinary
semantic-ui code — a controller per page, or a literal in an HTML file — so it is
a working project, not a saved editor file that needs the editor to run.

That makes it the fastest way to check your understanding of this page: build a
screen, export it, and compare the generated code with the five steps above.

<div class="sui-cta-actions">
  <a class="button button--primary button--lg" href="/mc-semantic-ui/editor/">Open the visual editor ↗</a>
  <a class="button button--secondary button--lg" href="/mc-semantic-ui/semantic-ui/editor">How the editor works</a>
</div>

</div>

## What's next

You now have the whole model. Two things this page deliberately skipped:

- **[Server-side rendering](./server-side-rendering.md)** — on the JVM the very
  same `UiPage` also renders to finished HTML through Handlebars templates, so a
  page works with JavaScript disabled and upgrades to the SPA when the bootstrap
  script loads. Nothing in steps 1–5 changes; SSR is an extra output of the same
  tree.
- **[Rendering modes](./rendering-modes.md)** — how SSR, SPA and patch updates
  relate, and how the DOM is morphed so focus and scroll survive a swap.

Then the building blocks: **[Triggers & actions](./triggers.md)** and the
**[Triggers cookbook](./triggers-cookbook.md)** for interaction patterns,
**[Forms & validation](./forms.md)** for input, and the
**[node vocabulary](./node-vocabulary.md)** for the elements themselves.
