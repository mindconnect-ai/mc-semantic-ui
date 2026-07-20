---
title: 'Quickstart: JavaScript client only'
---

# Quickstart — JavaScript client only

No backend, no build step, no npm. Load the runtime from a `<script>`, describe a
`UiNode` tree as a plain object, and render it. This is exactly what the
[widget showcase ↗](pathname:///widget-demo/) does.

## One file, no backend

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet"
        href="https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/sui.css">
</head>
<body>
  <main id="app"></main>

  <script type="module">
    import { SuiRenderer, installDefaultHandlers }
      from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/renderer.js";

    const host = document.getElementById("app");
    const renderer = installDefaultHandlers(new SuiRenderer(host));

    // Your UI, as data:
    renderer.mount({
      type: "table", id: "products", title: "Products", stackOnMobile: true,
      columns: [
        { type: "column", id: "c-sku",  label: "SKU",   dataKey: "sku"  },
        { type: "column", id: "c-name", label: "Name",  dataKey: "name" },
        { type: "column", id: "c-price",label: "Price", dataKey: "price"}
      ],
      rows: [
        { type: "row", id: "p1", data: { sku: "A-1", name: "Widget", price: "€ 19" } },
        { type: "row", id: "p2", data: { sku: "B-2", name: "Gadget", price: "€ 49" } }
      ]
    });
  </script>
</body>
</html>
```

Open the file in a browser — a styled, responsive table, zero build.

## Make it interactive

`mount()` renders a static tree. To make buttons, row actions and form submits
live, attach a `SuiEventBus` to the same host:

```js
import { SuiEventBus }
  from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/eventbus.js";

const bus = new SuiEventBus(renderer, host);
```

Now a node's `onClick` trigger fires: a `PATCH` trigger updates the DOM
client-side; a `fetch` trigger calls a URL. You decide what the data is and how
triggers behave — all in the page.

## Next

- **[CDN assets for HTML clients](./cdn-assets.md)** — the exact URLs, the
  `latest` vs pinned-tag shapes, and versioning.
- **[Embed as a UI island](./ui-island.md)** — mount a tree into one `<div>` on
  an existing page instead of owning the whole document.
- The building blocks: **[Node vocabulary](./node-vocabulary.md)**,
  **[Triggers & actions](./triggers.md)**, **[Forms & validation](./forms.md)**.
