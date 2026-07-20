---
title: Spinner
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `spinner` — busy indicator

**`UiSpinner`** is a spinning glyph, optionally with a label. Use it as a
placeholder for content that has not arrived yet — an empty list body, a card
that is still fetching — or inline next to a status line.

This is the *declarative* spinner: a real node you put in the tree and later
replace with a patch once the data arrives. It is distinct from the *automatic*
busy feedback the event bus paints on the control you just clicked, which needs
no node at all.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=spinner"
  title="Live: UiSpinner"
  loading="lazy"
  style={{width: '100%', height: '210px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — the three sizes, two labelled spinners, and (bottom) a busy button,
which is an action's `loading` state rather than a spinner node.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — the DOM id and the patch target you replace once loading finishes. |
| `size` | `SM` · `MD` · `LG` | Glyph size. Defaults to `MD` when absent. |
| `label` | `String` | Optional visible text next to the glyph (e.g. `"Loading…"`). |
| `title` | `String` | Accessible name for `role="status"`. Falls back to `label`; with neither, the spinner is `aria-hidden` (decorative). |
| `cssClass` | `String` | Extra CSS class on the element. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiSpinner.of();                                  // bare, decorative
UiSpinner.of("Loading…");                        // with a visible label
UiSpinner.of().size(UiSpinner.Size.LG);          // large
UiSpinner.of().size(UiSpinner.Size.SM).label("Importing products…");

// Placeholder now, real content later — same id, replaced by a patch:
UiPatch.of().patch(UiPatch.Operation.replace("panel", loadedTable()));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "spinner", "id": "busy", "size": "LG", "label": "Loading…" }

{ "type": "spinner", "id": "panel", "title": "Loading products" }
```

</TabItem>
</Tabs>

## Notes

**You usually don't need one.** Any control that dispatches a trigger — a
`UiAction` button, an action rendered as a link, a form submit — gets an
`is-loading` class from the bus for the duration of its request, which the CSS
paints as a small spinner and which blocks a second click. That covers "this
click is in flight" without a node. Suppress it for a page with
`bus.setLoadingPolicy("manual")`.

**Reach for a node when the *region* is loading, not the control.** A panel that
fetches on mount, a table body waiting on a slow query, a card in a dashboard —
put a `UiSpinner` there with a stable id and `REPLACE` it with the real content
when it arrives.

**And reach for `loading` when the *server* owns the state.** An action with
`loading: true` renders busy and disabled across re-renders, which is what you
want when the work outlives the request that started it. See
[`action`](./action.md).

**Give it a name or make it silent.** `title` (or the visible `label`) becomes
the accessible name behind `role="status"`; with neither, the spinner is marked
`aria-hidden` and screen readers skip it. Both are correct — pick deliberately.

**Colour and size follow the surroundings.** The glyph uses `currentColor` and
the size modifier only; set `color` on an ancestor to tint it.

## See also

- **[`progress`](./progress.md)** — when you know *how far along* the work is.
- **[`action`](./action.md)** — `loading`, the declarative busy button.
- **[Triggers & actions](../triggers.md)** — dispatch and the loading policy.
- **[Triggers cookbook](../triggers-cookbook.md)** — load-on-demand recipes.
