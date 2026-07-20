---
title: Page
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `page` — the document envelope

**`UiPage`** is not a widget. It is the envelope a controller returns: one
renderable `node` subtree plus the page-wide chrome that does not belong inside
that tree — the navigation hint for the address bar, a queue of toasts, the
dialogs that are open, and any live streams the client should re-attach to.

It extends `UiNode` (so editors, schemas and tree-walkers see it as just another
node, `type: "page"`), but the renderers treat it specially at the root of a
response. There is deliberately **no `page` handler registered on the
renderer** — the event bus unwraps a page, it never renders one.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Inherited from `UiNode`. Rarely set on a page. |
| `navigate` | `String` | The URL to push into the address bar when this page is applied. Null = leave history alone (or use the fetch's own href). |
| `node` | `UiNode` | The subtree to mount into the root host. Usually a [`stack`](./stack.md) or a [`section`](./section.md). |
| `toasts` | `List<UiToast>` | Transient messages shown in the body-level toast overlay. Null/empty = none. |
| `dialogs` | `List<UiDialog>` | Dialogs open on this page, painted into the body-level `#sui-dialogs` host. Null/empty = none (the common case). |
| `activeStreams` | `List<ActiveStream>` | SSE streams the server still considers running for this page, so the SPA can reconnect. Null/empty = none. |

### `ActiveStream`

| Field | Type | Meaning |
|---|---|---|
| `channelId` | `String` | Stream key — the same value the server emits in the `Sui-Stream-Channel` header on the original POST response. |
| `resumeUrl` | `String` | GET URL that opens a new SSE connection and replays missed events. |
| `label` | `String` | Human-readable label for status surfaces. |
| `returnHref` | `String` | Where the user can navigate to see this stream's owning page. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// a page that also sets the address bar
UiPage.of("/products", UiStack.of(searchForm, productTable));

// a page that just replaces the screen, leaving history alone
UiPage.show(productDetail);

// with feedback and an open dialog
UiPage.of("/products/42", productDetail)
      .toast(UiToast.success("Product saved"))
      .dialog(confirmDialog);

// resume support for a running stream
var page = UiPage.of("/chat/7", chatBody);
page.setActiveStreams(List.of(UiPage.ActiveStream.of(
        "chan-7", "/chat/7/stream/resume", "Assistant replying", "/chat/7")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "page",
  "navigate": "/products/42",
  "node": { "type": "stack", "id": "body", "gap": 12, "children": [] },
  "toasts": [ { "level": "SUCCESS", "message": "Product saved" } ],
  "dialogs": [],
  "activeStreams": [
    { "channelId": "chan-7", "resumeUrl": "/chat/7/stream/resume",
      "label": "Assistant replying", "returnHref": "/chat/7" }
  ] }
```

</TabItem>
</Tabs>

## How the client applies it

The bus's default response handler looks at a JSON body: if it has `patches` it
is a patch, and if it has `node` or `navigate` it is a page — which goes to
`applyPage`. From there, in order:

1. `page.node` is mounted via `renderer.mount(page.node)`.
2. `page.dialogs` repaint the `#sui-dialogs` host — a fresh screen drops the
   previous page's open dialogs.
3. `page.toasts` are shown in the toast overlay.
4. Stream attachments are reconciled, then `page.activeStreams` opens a resume
   GET for any channel the client is not already reading.
5. If history is enabled, `page.navigate` (or the fallback href) is pushed.

```js
bus.applyPage(page);          // takes a UiPage
await bus.navigate("/products");  // takes a URL: fetch, then applyPage
await bus.start("/products");     // wires popstate, then navigate
renderer.mount(page.node);        // takes a NODE — not the page
```

## Notes

**`renderer.mount()` takes a node, not a page.** This is the mistake worth
naming: hand `mount()` a `UiPage` and the renderer finds no handler for node
type `page`, logs `"SuiRenderer: no handler for node type"` and renders a
`<pre>` JSON dump of your envelope. Pass `page.node` to `mount()`, or pass the
whole page to `bus.applyPage(page)` and let the bus unwrap it.

**Three entry points, three argument types.** `applyPage(page)` takes the
envelope, `navigate(href)` and `start(href)` take a URL. `start()` is the boot
call: it installs the `popstate` listener (when history is enabled) and then
navigates to the initial href.

**Toasts and dialogs hang off the page, not the tree.** That is the point of the
envelope — adding a success message or opening a modal does not ripple through
your UI structure, and the SSR converter and the SPA bus paint the same
body-level hosts either way. Later opens and closes are `APPEND` / `REMOVE`
patches against `#sui-dialogs`.

**`navigate` is optional.** Use `UiPage.of(href, node)` when the response is a
navigation the address bar should reflect, and `UiPage.show(node)` when you are
just replacing what is on screen — a form re-render after a validation failure
should not invent a history entry.

**`activeStreams` is opt-in resume.** Most pages leave it null. Set it when a
long-running SSE stream should survive an F5, a page-navigate or a second tab:
the client opens the `resumeUrl` for any `channelId` it is not already reading
and replays from the server's ring buffer.

## See also

- **[`stack`](./stack.md)** — the usual root `node`.
- **[`section`](./section.md)** — the tabbed container.
- **[`dialog`](./dialog.md)** — what goes in `dialogs`.
- **[Rendering modes](../rendering-modes.md)** — how SSR and SPA consume the same envelope.
- **[Triggers & actions](../triggers.md)** — what produces a page response.
