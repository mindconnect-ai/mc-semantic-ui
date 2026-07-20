---
title: Stack
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `stack` — plain composition

**`UiStack`** is the lego brick of the vocabulary: a box that renders its
`children` one after another, vertically by default. No tab bar, no panel
switching, no chrome of its own — it is what you would reach for a `<div>` for.

It is also the most-used node in semantic-ui. A page body is a vertical stack;
a button bar is a horizontal one; almost every screen is stacks nested inside
stacks with the interesting nodes at the leaves.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=stack"
  title="Live: UiStack"
  loading="lazy"
  style={{width: '100%', height: '340px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — the same three buttons, first `VERTICAL`, then `HORIZONTAL`. Nothing
changes but `direction`. The third row repeats it with `gap: 24`.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `children` | `List<UiNode>` | The nodes to lay out, in order. Defaults to an empty list. |
| `direction` | `VERTICAL` · `HORIZONTAL` | Layout direction. `VERTICAL` when unset. |
| `gap` | `Integer` | Space between children in px, emitted as an inline `gap` style. Unset falls back to the stylesheet's token default. |
| `title` | `String` | Inherited from `UiNode`. Not rendered by the stack renderer. |
| `cssClass` | `String` | Extra CSS class on the wrapper `<div>`. |

The rendered element is `<div class="sui-stack sui-stack--vertical">` (or
`--horizontal`), so direction is styleable from the host stylesheet too.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// a page body — children in one call
UiStack.of(searchForm, productTable).gap(12);

// a button row
UiStack.of(
        UiAction.primary("save", "Save"),
        UiAction.secondary("cancel", "Cancel"))
    .direction(UiStack.Direction.HORIZONTAL)
    .gap(8);

// an empty stack with an id, filled fluently
UiStack.of("product-body")
    .child(header)
    .child(productTable)
    .gap(16);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "stack", "id": "actions", "direction": "HORIZONTAL", "gap": 8,
  "children": [
    { "type": "action", "id": "save",   "label": "Save",   "style": "PRIMARY" },
    { "type": "action", "id": "cancel", "label": "Cancel", "style": "SECONDARY" }
  ] }

{ "type": "stack", "id": "product-body", "gap": 16,
  "children": [
    { "type": "header", "id": "h", "text": "Products" },
    { "type": "table",  "id": "product-table", "columns": [], "rows": [] }
  ] }
```

</TabItem>
</Tabs>

`direction` and `gap` are both optional — a bare
`{ "type": "stack", "id": "x", "children": [...] }` is a valid vertical stack.

## Notes

**Stack or section?** A stack shows everything at once; a
[`section`](./section.md) makes its children compete for one viewport with the
user picking which to see. If you would not put a tab label on it, it is a
stack.

**Give stacks ids you can patch.** A stack's `id` is its DOM `id`, which makes
it the natural target for `REPLACE` / `APPEND` / `CLEAR` patches. Wrapping a
volatile region in a stack purely so a patch has something to aim at is a normal
move.

**`gap` is a number, not a token.** It is written straight into an inline
`style="gap: Npx"`. Leave it unset to inherit the theme's spacing and keep the
model free of pixel decisions; set it when a specific row genuinely needs to be
tighter or looser than the default.

**Horizontal stacks wrap, they do not scroll.** For a row that must stay on one
line, or for column-proportional layouts, use the grid nodes (`row` / `column`)
rather than fighting a horizontal stack. See [Responsive](../responsive.md).

## See also

- **[`section`](./section.md)** — the tabbed and collapsible container.
- **[`page`](./page.md)** — the envelope a stack usually arrives in.
- **[Rendering modes](../rendering-modes.md)** — SSR, SPA and editor from one tree.
- **[Responsive](../responsive.md)** — how layout nodes behave at narrow widths.
