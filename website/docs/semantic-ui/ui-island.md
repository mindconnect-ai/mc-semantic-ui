---
title: Embed as a UI island
sidebar_position: 7
---

# Embed as a UI island

You don't need a full SPA shell to use semantic-ui. Drop a single `<div>` into
**any existing page** — rendered by your CMS, framework, or template engine —
and mount one `UiNode` tree into it. The host page's layout is untouched.

An island has two halves, and both matter: the **renderer** paints the tree, and
the **event bus** makes it respond. Mount without a bus and every button, row
action and form submit in that tree is inert — you have rendered a picture of a
UI. Unless the island really is read-only, you want both.

## Minimal example

```html
<link rel="stylesheet" href="/sui/sui.css">

<h1>My existing page</h1>
<p>Content rendered by whatever already builds this page.</p>

<div id="product-table"></div>

<script type="module">
  import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
  import { SuiEventBus } from "/sui/eventbus.js";

  const host = document.getElementById("product-table");
  const renderer = installDefaultHandlers(new SuiRenderer(host));

  // The bus listens on the host element, so everything it does stays
  // inside this <div>. History off: the address bar belongs to the host page.
  const bus = new SuiEventBus(renderer, host).setHistoryEnabled(false);

  const node = await fetch("/api/products", {
      headers: { Accept: "application/json" }
  }).then(r => r.json());

  renderer.mount(node);
</script>
```

Now `data-trigger` elements inside the island fetch and apply updates just like
in a full SPA, but scoped to your `<div>`.

:::tip Order doesn't matter
You can create the bus before or after `mount()`. It enhances whatever is
already inside the host element and watches for later changes with a
`MutationObserver`, so tab overflow, menu state and popovers get wired up either
way — including on SSR-rendered markup that was already in the page.
:::

:::note An island endpoint returns a **node**, not a `UiPage`
`renderer.mount()` renders a single node — there is no `page` handler in the
renderer, so mounting a `UiPage` envelope would just dump its JSON. Have the
island endpoint return the bare node (a `table`, `form`, `detail`, …), or unwrap
it yourself with `renderer.mount(page.node)`. Full-page apps don't hit this: the
[event bus](./building-an-app.md) unwraps the envelope for you.
:::

That's it — the `<div>` becomes a live UiNode island. Use as many islands on a
page as you want; each gets its own `SuiRenderer` **and its own bus**, bound to
its own host element.

The `/sui/*` URLs above assume a backend that serves the runtime (any Spring
Boot app with `mc-semantic-ui-core` on the classpath). If the host page has no
such backend, load the files from the public docs site instead — see
[CDN assets for HTML clients](./cdn-assets.md).

## Island vs. full app

| | UI island | [Full SPA app](./building-an-app.md) |
|---|---|---|
| Host page | any existing page | a static `index.html` shell you own |
| What renders | one tree into one `<div>` | every screen, full-page |
| Event bus | needed as soon as anything is clickable; history off | yes — clicks, forms, navigation, history |
| Use when | adding a widget to an existing site | building the whole UI with semantic-ui |

## What the bus gives the island

The bus is the same one the [full app](./building-an-app.md#3-the-entry-script--appjs)
uses; in an island you simply configure it more defensively.

### Handle triggers locally, with no backend

An island often lives on a page whose backend knows nothing about semantic-ui.
A client handler answers a trigger in the browser — return a `UiPatch` (or a
`UiPage`) and the bus applies it for you:

```js
bus.registerClientHandler("addToCart", (ctx) => {
    cart.push(ctx.payload);              // your own state
    return {                             // a UiPatch, applied by the bus
        patches: [{ op: "REPLACE", targetId: "cart-badge",
                    node: { type: "text", id: "cart-badge",
                            text: `${cart.length} items` } }],
        toasts: [{ level: "SUCCESS", message: "Added." }]
    };
});
```

The matching trigger is `UiTrigger.invoke("addToCart")` — the same node your
server would emit. Handlers may be `async`.

### Patch from the outside

The host page can drive the island directly, which is the usual way to react to
something that happens elsewhere on the page:

```js
bus.applyPatch({ patches: [{ op: "REPLACE", targetId: "product-list", node: tree }] });
```

### Keep failures inside the island

By default a failed fetch surfaces as a toast. On a host page you don't own,
route errors into your own UI instead:

```js
bus.setOnError((err) => myHostPageBanner(
    err.kind === "http"
        ? `Products unavailable (${err.response.status})`
        : `Could not reach ${err.url}`));
```

Also useful here: `setFetcher()` to reuse the host page's authenticated fetch,
and `setUrlRewriter()` when the island's API lives under a different prefix.

:::warning Turn history off in an island
By default the bus pushes every navigation into `history.pushState` — correct
for a full-page app, wrong for a widget embedded in someone else's page (it
would hijack the host's URL and its back button). `setHistoryEnabled(false)`
keeps the island self-contained. With several islands on one page, give each its
own bus and turn history off on all of them.
:::
