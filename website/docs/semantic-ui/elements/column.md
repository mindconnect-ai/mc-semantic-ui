---
title: Column
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `column` — one column of a table

**`UiColumn`** describes a single column of a [`table`](./table.md): the header
`label`, the `dataKey` used to look up each cell's value in the row's data map,
and — when plain text is not enough — a `cellTemplate` that turns every cell
into a rendered node.

A column is itself a `UiNode`, which is why it has an `id` and shows up in the
visual editor's tree like everything else. Keep `id` and `dataKey` separate: the
id is the DOM/editor identity, the data key is the contract with the row data.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=column"
  title="Live: UiColumn"
  loading="lazy"
  style={{width: '100%', height: '260px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — a column cannot render alone, so this is a minimal table built to show
what its columns do. "SKU" is a plain `dataKey` column, "Name" uses a
`cellTemplate` of a link with `{sku}`/`{name}` placeholders, and the last column
templates a button whose trigger url resolves to the row's own id.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — rendered on the `<th>` so the editor can select the column. `UiColumn.of(...)` generates `col-<dataKey>-<nonce>`. |
| `label` | `String` | Header text. Also becomes each cell's `data-label`, which is what the table's `stackOnMobile` card layout displays. |
| `dataKey` | `String` | Key into the row's `data` map. **Falls back to `id`** when null. |
| `cellTemplate` | `UiNode` | Optional. Replaces plain-text cell rendering — see below. |
| `sortable` | `boolean` | Renders the header as a clickable sort control. Defaults to `false`. See below. |
| `title` | `String` | Inherited from `UiNode`. Not used for the header — set `label`. |
| `cssClass` | `String` | Inherited. Not applied by the table renderer. |

## Sorting

Marking a column `sortable` turns its header into a button with a direction
indicator and an `aria-sort` state. What happens on click depends on the
**table**, not the column:

| `UiTable.sortTrigger` | Behaviour |
|---|---|
| set | The trigger is dispatched with `{column}` and `{direction}` substituted — the **server** sorts and returns the page. |
| not set | The **browser** reorders the rows it already has, best-effort. |

The client-side fallback compares numerically when both values parse as finite
numbers, otherwise with a locale-aware string compare; blank cells sort last in
both directions. It re-renders from the table's own embedded model, so the
header indicator and `aria-sort` stay in sync.

:::warning Paginated tables need a sort trigger
Client-side sorting only ever sees the rows currently in the DOM. On a paginated
table that is one page of many, so sorting it reorders *that page* and looks
convincing while being wrong. Whenever a table has `pagination`, give it a
`sortTrigger` too.
:::

```java
UiTable.of("products", "Products")
       .column(UiColumn.of("name",  "Name").asSortable())
       .column(UiColumn.of("price", "Price").asSortable())
       // Server-side: omit this line to sort in the browser instead.
       .sortTrigger(UiTrigger.go("/products?sort={column}&dir={direction}"))
       .sortedBy("name", UiTable.SortDirection.ASC);
```

### `cellTemplate`

When set, the template node is deep-cloned for every row and then:

1. every string field, recursively through all descendants, is run through
   `{key}` substitution against the row's data map, with `{id}` resolving to the
   row's id;
2. unknown keys are left in place (`{typo}` stays visible) so a broken
   placeholder is obvious rather than silent;
3. every `id` in the cloned subtree is suffixed with `__<row.id>` to keep DOM
   ids unique across rows.

Any node type is allowed. [`text`](./text.md) is the minimal substituted-string
case, [`link`](./link.md) turns the cell into navigation, and a
[`stack`](./stack.md) of [`action`](./action.md)s gives per-row inline controls
that — unlike the table's shared `rowActions` — can differ in label and style
per row's data.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Primary factory — assigns a generated, tree-unique id.
UiColumn.of("name", "Name");

// Presentation hints (model-level only, see the note above):
UiColumn.of("price", "Price").asSortable().asFilterable();

// A link cell: every string is {dataKey}-substituted per row.
UiColumn.of("name", "Name")
        .withCellTemplate(UiLink.of("c-name", "/products/{id}", "{name}"));

// An action cell.
UiColumn.of("id", "")
        .withCellTemplate(UiAction.secondary("c-open", "Open {sku}")
                .icon("show")
                .onClick(UiTrigger.go("/products/{id}")));

// text()/date()/number() still exist but are plain aliases for of() —
// the old cell-type hint was never read by any renderer.
UiColumn.number("stock", "Stock");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "column", "id": "col-name", "label": "Name", "dataKey": "name",
  "sortable": true }

{ "type": "column", "id": "col-name", "label": "Name", "dataKey": "name",
  "cellTemplate": { "type": "link", "id": "c-name", "rel": "c-name",
                    "href": "/products/{id}", "label": "{name}" } }

{ "type": "column", "id": "col-open", "label": "", "dataKey": "id",
  "cellTemplate": { "type": "action", "id": "c-open", "label": "Open {sku}",
                    "style": "SECONDARY", "icon": "show",
                    "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET",
                                 "url": "/products/{id}" } } }
```

</TabItem>
</Tabs>

## Notes

**`dataKey` defaults to `id`.** Handy for throwaway tables, but the generated id
from `UiColumn.of(...)` carries a `col-…` prefix and a nonce, so the fallback
only works for columns you built by hand with a matching id. Set `dataKey`
explicitly in anything long-lived.

**Two columns may share a `dataKey`.** That is why `of()` mints a nonce-suffixed
id — one column showing the raw value, another showing the same value through a
`cellTemplate`, both addressable independently in the editor.

**Template ids must be stable, not unique.** Give the template node a plain id
(`c-name`); the renderer appends the row suffix itself. Hand-suffixing produces
`c-name__p1__p1`.

**Column count drives the empty state.** An empty table renders one cell
spanning the columns, so a table with zero columns shows nothing useful — always
declare columns even when `rows` is empty.

**Header text lives in `label`, not `title`.** `UiColumn` inherits `title` from
`UiNode`, but the renderer only reads `label`; a column with only a `title`
renders a blank header.

## See also

- **[`table`](./table.md)** — the container, selection, actions and pagination.
- **[`row`](./row.md)** — the other half of the `dataKey` contract.
- **[`link`](./link.md)** / **[`action`](./action.md)** / **[`text`](./text.md)** — the usual `cellTemplate` payloads.
- **[Triggers & actions](../triggers.md)** — what a templated `onClick` can do.
- **[Responsive layout](../responsive.md)** — `label` doubles as the mobile card's `data-label`.
