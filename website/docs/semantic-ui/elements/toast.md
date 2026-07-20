---
title: Toast
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `toast` — transient notification

**`UiToast`** is a short, self-dismissing message anchored to a corner of the
viewport. Toasts stack, and each one carries a level that drives its colour and
its status glyph.

**`UiToast` is not a `UiNode`.** It has no `type` discriminator and you never
put it in the tree. Toasts ride *alongside* the content, on the response
envelope: `UiPage.toasts` for a full render, `UiPatch.toasts` for a partial
one. That is deliberate — it lets the server attach feedback to any response
without touching the UI tree at all.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=toast"
  title="Live: UiToast"
  loading="lazy"
  style={{width: '100%', height: '220px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — one button per level. Try the titled toast, and the sticky one that
only goes away when you close it.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `level` | `INFO` · `SUCCESS` · `WARN` · `ERROR` | Severity. Drives the `.sui-toast--…` modifier and the status glyph. Treated as `INFO` when absent. |
| `message` | `String` | The body text. Required; single line in practice — long text is truncated by CSS. |
| `title` | `String` | Optional bold line above the message. Omit for a body-only toast. |
| `durationMs` | `int` | Auto-dismiss timeout. Defaults to `4000` (`UiToast.DEFAULT_DURATION_MS`). Any non-positive value means sticky — it stays until the user closes it. |

There is no `id` or `cssClass`: a toast is not a node and nothing addresses it
after it is shown.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Attached to a full page render:
UiPage.of("/products", table).toast(UiToast.success("Product saved"));

// Attached to a patch — the common case: a small update plus feedback.
UiPatch.of()
    .patch(UiPatch.Operation.replace("row-42", updatedRow))
    .toast(UiToast.warn("Stock is low"));

// The four level factories, plus the fluent modifiers:
UiToast.info("Saved to drafts");
UiToast.success("Changes published");
UiToast.warn("Storage almost full");
UiToast.error("Upload failed").title("product-list.csv").sticky();
UiToast.of(UiToast.Level.INFO, "Heads up", "Indexing takes a minute").durationMs(8000);

// Purely client-side — no server round-trip at all:
UiAction.secondary("copy", "Copy link").onClick(UiTrigger.toast("Link copied"));
UiAction.secondary("pub", "Publish")
        .onClick(UiTrigger.toast(UiToast.success("Published")));
```

</TabItem>
<TabItem value="json" label="JSON">

On the wire a toast is just an entry in the `toasts` array of a page or a patch:

```json
{
  "patches": [ { "op": "REPLACE", "targetId": "row-42", "node": { } } ],
  "toasts": [
    { "level": "SUCCESS", "message": "Product saved" },
    { "level": "ERROR", "title": "Upload failed", "message": "File too large", "durationMs": 0 }
  ]
}
```

A client-side-only toast is a `PATCH` trigger whose patch carries no DOM
operations:

```json
{ "type": "action", "id": "copy", "label": "Copy link", "style": "SECONDARY",
  "onClick": { "behavior": "PATCH",
               "patch": { "patches": [], "toasts": [ { "level": "INFO", "message": "Link copied" } ] } } }
```

</TabItem>
</Tabs>

## Notes

**Three delivery paths, one shape.** A toast reaches the user on a page render
(`UiPage.toasts`), on a patch response (`UiPatch.toasts`), or entirely
client-side via `UiTrigger.toast(...)` — which is sugar for a `PATCH` trigger
carrying an empty patch and one toast, so nothing is fetched. Use the last one
for feedback the server has no opinion about ("Link copied").

**Toasts survive page swaps.** The SPA appends them to a persistent body-level
`#sui-toast-container` that sits outside `#sui-root`, so a toast raised by the
response that navigated you somewhere is still visible on the new page. The SSR
converter emits the same container as a sibling of the root plus a tiny inline
script for the auto-dismiss, so no-JS pages get toasts too.

**`durationMs: 0` means sticky.** Anything the user must acknowledge — a failed
upload, a partial save — should not vanish after four seconds. `UiToast.sticky()`
is the shorthand; the close button is always present regardless.

**Errors toast themselves.** When a dispatch fails (network error or a non-ok
status) the bus's default error handler raises a red toast through this same
container. Override it with `bus.setOnError(...)` if you want different
behaviour — you do not need to hand-write error toasts for failed requests.

**Level is not decoration.** It picks the glyph as well as the colour
(`INFO` → `info`, `SUCCESS` → `success`, `WARN` → `warning`, `ERROR` → `error`
from the [icon set](./icon.md)), which is what makes the state readable without
relying on colour alone.

## See also

- **[`dialog`](./dialog.md)** — the modal counterpart, for overlays with content.
- **[`action`](./action.md)** — where most toast triggers hang.
- **[Triggers & actions](../triggers.md)** — `UiTrigger.toast(...)` and the patch envelope.
- **[Triggers cookbook](../triggers-cookbook.md)** — save-then-confirm recipes.
