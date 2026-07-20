---
title: Embed as a UI island
sidebar_position: 7
---

# Embed as a UI island

You don't need a full SPA shell to use semantic-ui. Drop a single `<div>` into
**any existing page** — rendered by your CMS, framework, or template engine —
and mount one `UiNode` tree into it. The host page's layout is untouched.

## Minimal example

```html
<link rel="stylesheet" href="/sui/sui.css">

<h1>My existing page</h1>
<p>Content rendered by whatever already builds this page.</p>

<div id="product-table"></div>

<script type="module">
  import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";

  const host = document.getElementById("product-table");
  const renderer = installDefaultHandlers(new SuiRenderer(host));

  const node = await fetch("/api/products", {
      headers: { Accept: "application/json" }
  }).then(r => r.json());

  renderer.mount(node);
</script>
```

:::note An island endpoint returns a **node**, not a `UiPage`
`renderer.mount()` renders a single node — there is no `page` handler in the
renderer, so mounting a `UiPage` envelope would just dump its JSON. Have the
island endpoint return the bare node (a `table`, `form`, `detail`, …), or unwrap
it yourself with `renderer.mount(page.node)`. Full-page apps don't hit this: the
[event bus](./building-an-app.md) unwraps the envelope for you.
:::

That's it — the `<div>` becomes a live UiNode island. Use as many islands on a
page as you want; each gets its own `SuiRenderer` bound to its own host element.

The `/sui/*` URLs above assume a backend that serves the runtime (any Spring
Boot app with `mc-semantic-ui-core` on the classpath). If the host page has no
such backend, load the files from the public docs site instead — see
[CDN assets for HTML clients](./cdn-assets.md).

## Island vs. full app

| | UI island | [Full SPA app](./building-an-app.md) |
|---|---|---|
| Host page | any existing page | a static `index.html` shell you own |
| What renders | one tree into one `<div>` | every screen, full-page |
| Event bus | optional | yes — clicks, forms, navigation, history |
| Use when | adding a widget to an existing site | building the whole UI with semantic-ui |

## Add interactivity

`mount()` alone gives you a rendered, static island. To make triggers
(buttons, row actions, form submits) live inside the island, attach a
`SuiEventBus` to the same host — the same wiring as the
[full app](./building-an-app.md#3-the-entry-script--appjs):

```js
import { SuiEventBus } from "/sui/eventbus.js";

const bus = new SuiEventBus(renderer, host)
    .setHistoryEnabled(false);   // ← don't touch the host page's address bar
```

Now `data-trigger` elements in the island fetch and apply updates just like in a
full SPA, but scoped to your `<div>`.

:::warning Turn history off in an island
By default the bus pushes every navigation into `history.pushState` — correct
for a full-page app, wrong for a widget embedded in someone else's page (it
would hijack the host's URL and its back button). `setHistoryEnabled(false)`
keeps the island self-contained. With several islands on one page, give each its
own bus and turn history off on all of them.
:::
