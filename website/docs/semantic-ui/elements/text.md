---
title: Text
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `text` — a string in the tree

**`UiText`** is the simplest leaf node: a string that participates in the
`UiNode` tree. It renders as a single `<span class="sui-text">`, which means it
carries an `id` — and that is what makes it far more useful than "a paragraph".
A `UiText` is the canonical **patch target** for a value that changes: a total,
a status, a counter, a streamed token.

Reach for it whenever a piece of text needs a place in the model rather than
being baked into a label somewhere else — as a line inside a
[`stack`](./stack.md), as the `cellTemplate` of a [`table`](./table.md) column,
as the `content` of a [`list`](./list.md) item, or as the thing a patch swaps.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=text"
  title="Live: UiText"
  loading="lazy"
  style={{width: '100%', height: '190px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — "Add item" and "Reset" `REPLACE` only the `cart-total` text node. The
buttons, the labels and the surrounding stack are never re-rendered.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — becomes the `<span>` id, so it can be targeted by a patch. Optional; omit it for purely decorative text. |
| `text` | `String` | The string to display. HTML-escaped by the renderer. May contain `{dataKey}` placeholders when used as a table column's `cellTemplate`. |
| `title` | `String` | Inherited from `UiNode`. Not rendered by the text renderer. |
| `cssClass` | `String` | Extra CSS class added next to `sui-text`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Anonymous text — fine inside a stack where nothing will patch it:
UiText.of("A plain text node — the simplest leaf widget.");

// With an id — now it is addressable:
UiText.of("cart-total", "€ 49.00");

// As a patch: swap just that one value.
UiPatch.of().patch(UiPatch.Operation.replace(
        "cart-total", UiText.of("cart-total", "€ 61.50")));

// As a table cell template — {sku} is substituted per row:
UiColumn.of("sku", "SKU").withCellTemplate(UiText.of("{sku}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "text", "id": "cart-total", "text": "€ 49.00" }
```

```json
{ "patches": [
    { "op": "REPLACE", "targetId": "cart-total",
      "node": { "type": "text", "id": "cart-total", "text": "€ 61.50" } }
  ],
  "toasts": [ { "level": "INFO", "message": "Item added" } ] }
```

</TabItem>
</Tabs>

## Notes

**Keep the id stable across a replace.** A `REPLACE` looks the target up by
`targetId` and swaps the element for the rendered `node`. If the replacement
node carries a *different* id, the next patch has nothing to aim at. Always
build the new `UiText` with the same id as the one you are replacing — that is
the whole contract.

**Text is the cheapest live update you can ship.** Rather than returning a whole
`UiPage` and letting the morpher diff it, a server that only changed one number
can return a one-operation `UiPatch`. Focus, scroll position and any open
disclosure elsewhere on the page survive untouched. See the
[triggers cookbook](../triggers-cookbook.md) for the round trip.

**It escapes, always.** The renderer HTML-escapes `text`, so a value straight
out of the database cannot inject markup. If you actually want formatted prose,
that is a different node — the `markdown` node from
`mc-semantic-ui-ext-markdown`, not a `UiText` with tags in it.

**No styling knobs on purpose.** There is no `variant`, `size` or `bold` field.
Emphasis comes from where the text sits — a [`section`](./section.md) title, a
list item `description`, a [`detail`](./detail.md) row — or from `cssClass` when
a host app genuinely needs its own treatment.

## See also

- **[`list`](./list.md)** — items whose `content` is often a `UiText`.
- **[`detail`](./detail.md)** — label/value rows, when the text is record data.
- **[Triggers cookbook](../triggers-cookbook.md)** — patching a text node live.
- **[`table`](./table.md)** — `cellTemplate` and `{dataKey}` substitution.
