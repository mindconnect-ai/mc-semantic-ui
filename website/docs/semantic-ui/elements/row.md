---
title: Row
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `row` — one record in a table

**`UiRow`** is a single row of a [`table`](./table.md). It holds two things: a
`data` map of cell values keyed by the columns' `dataKey`s, and — inherited from
`UiNode` — an `id` that is the row's stable identity. That id is what selection,
row highlighting and the `{id}` placeholder in row-action triggers all resolve
against.

Most server code never writes a `UiRow` literal: `UiTable.row(Map.of(...))`
packs a plain map into one. Rows are modelled as nodes anyway so the visual
editor can add, delete and select them like any other tree child.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=row"
  title="Live: UiRow"
  loading="lazy"
  style={{width: '100%', height: '260px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — a row cannot render alone, so this is a minimal table built to show what
a row carries. The first column prints each row's own id; `r2` is both
highlighted (`selectedRowId`) and checked, `r3` is only checked
(`selectedRowIds`); click "Who am I?" to see `{id}` resolved per row. Row `r3`
also carries a data key no column asks for — it is simply not rendered.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Row identity. Rendered as the `<tr id>`, matched against the table's `selectedRowId` / `selectedRowIds`, and substituted for `{id}` in row-action triggers. |
| `data` | `Map<String, Object>` | Cell values, keyed by column `dataKey`. A `LinkedHashMap`, so insertion order is preserved (though the *columns* decide render order). |
| `title` | `String` | Inherited from `UiNode`. Unused by the table renderer. |
| `cssClass` | `String` | Inherited. Not applied to the `<tr>`; the renderer only adds `sui-row--selected`. |

### Where the id comes from

`UiRow.of(map)` copies the map into its own `LinkedHashMap` (so immutable
`Map.of(...)` inputs work and later `put()` calls do not throw) and, if the map
contains an `"id"` entry, **promotes that value to the node id**. Both renderers
then read `row.id ?? row.data["id"]`, so either placement works — but relying on
the promotion keeps one obvious source of truth.

Values are rendered with `String` escaping; a `null` value renders as an empty
cell, and keys no column declares are ignored.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The usual way — the table wraps the map for you and promotes "id".
UiTable.of("users", "Users")
    .column(UiColumn.of("user", "User"))
    .column(UiColumn.of("role", "Role"))
    .row(Map.of("id", "r1", "user", "ada",   "role", "admin"))
    .row(Map.of("id", "r2", "user", "linus", "role", "editor"));

// Explicit UiRow — same result, useful when building incrementally.
UiRow row = UiRow.of(Map.of("id", "r3"))
        .put("user", "grace")
        .put("role", "viewer");

UiTable.of("users", "Users")
    .row(row)
    .selectMode(UiTable.SelectMode.MULTI)
    .selectedRowIds(List.of("r2", "r3"))
    .selectedRow("r2")
    .rowAction(UiAction.secondary("r-who", "Who am I?")
        .onClick(UiTrigger.api("GET", "/rows/{id}")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "row", "id": "r1", "data": { "user": "ada", "role": "admin" } }

{ "type": "row", "id": "r2", "data": { "id": "r2", "user": "linus", "role": "editor" } }
```

</TabItem>
</Tabs>

## Notes

**Give every row an id.** Without one the `<tr>` has no DOM id, `{id}` in a
row-action trigger substitutes to the empty string, and selection values come
back blank. Any row the user can act on needs a real, stable id — the database
key, not the list index.

**`data` keys must match column `dataKey`s.** There is no error for a mismatch:
an unmatched column renders an empty cell and an unmatched data key is dropped.
When cells come up blank, compare the two lists first.

**Formatting belongs on the server.** Cell values are escaped and printed
as-is — no number, date or currency formatting happens in the renderer. Put
already-formatted strings in `data`, or move the presentation into the column's
[`cellTemplate`](./column.md).

**Row nodes are patch targets.** Because the `<tr>` carries the row id, a
[patch](../triggers.md) can `REPLACE` or `REMOVE` a single row without re-sending
the table. The table also serialises its own model into `data-node`, which is how
a single-row patch stays consistent with the rest of the grid.

## See also

- **[`table`](./table.md)** — selection, row actions and pagination.
- **[`column`](./column.md)** — the `dataKey` contract and custom cell rendering.
- **[`action`](./action.md)** — what `rowActions` are made of.
- **[Triggers & actions](../triggers.md)** — `{id}` substitution and row-level patches.
- **[Forms](../forms.md)** — submitting table selection as part of a form.
