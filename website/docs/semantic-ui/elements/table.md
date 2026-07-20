---
title: Table
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `table` — rows and columns of data

**`UiTable`** renders a list of records as a grid: [`column`](./column.md)
definitions describe the header and the data lookup, [`row`](./row.md) nodes
carry the values. On top of that it adds the things a data grid always ends up
needing — header actions, per-row actions, row selection and pagination.

Reach for a table when the records share a shape and the user compares them
across fields. When each item is a heading plus a bit of prose, use
[`list`](./list.md); for a single record's fields, use [`detail`](./detail.md).

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=table"
  title="Live: UiTable"
  loading="lazy"
  style={{width: '100%', height: '360px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — tick the checkboxes (`selectMode: MULTI`, "Gadget" pre-checked and
highlighted), click a row's pencil or bin to see the `{id}` placeholder already
resolved to that row's id, and use Next to see `{page}` substituted in the
pagination trigger.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id`, the patch target, and the prefix of the selection form-name. |
| `title` | `String` | Optional heading in the header bar. |
| `columns` | `List<UiColumn>` | Column definitions, left to right. See [`column`](./column.md). |
| `rows` | `List<UiRow>` | The data rows. Empty renders a single "No rows." cell. See [`row`](./row.md). |
| `actions` | `List<UiAction>` | Table-level buttons in the header bar (New, Export, …). The bar renders when there is a title **or** at least one action. |
| `rowActions` | `List<UiAction>` | One shared action template rendered into a trailing cell of *every* row. `{id}` in the trigger `url` is replaced with the row's id. |
| `selectMode` | `NONE` · `SINGLE` · `MULTI` | Row-selection controls. Defaults to `NONE`. |
| `selectedRowIds` | `List<String>` | Row ids whose radio/checkbox renders pre-checked. |
| `selectedRowId` | `String` | Row id rendered as *highlighted* (`sui-row--selected`). Independent of the checkboxes. |
| `pagination` | `UiTable.Pagination` | Page footer. Absent = no footer. |
| `stackOnMobile` | `boolean` | Defaults to `false`. `true` collapses the table to stacked `Label: value` cards on narrow screens. |
| `sortTrigger` | `UiTrigger` | Trigger template for sortable headers. `{column}` and `{direction}` in its `url` are substituted per header. Null = sort client-side. |
| `sortColumn` | `String` | The `dataKey` currently sorted by — drives the header indicator and `aria-sort`. |
| `sortDirection` | `ASC` · `DESC` | Direction of `sortColumn`. Defaults to `ASC`. |
| `maxHeight` | `String` | CSS length (`"420px"`, `"60vh"`). Caps the row area: rows scroll, the header row stays pinned. |
| `cssClass` | `String` | Extra CSS class on the wrapping `<div>`. |

### `UiTable.Pagination`

| Field | Type | Meaning |
|---|---|---|
| `page` | `int` | Current page, 1-based. |
| `size` | `int` | Rows per page — used to compute the page count from `total`. |
| `total` | `long` | Total number of rows across all pages. |
| `pageTrigger` | `UiTrigger` | Trigger template for Prev/Next. The literal `{page}` in its `url` is replaced with the target page number. **Null renders the footer read-only** — the buttons are there but disabled. |

### `UiTable.SelectMode`

| Value | Renders |
|---|---|
| `NONE` | No selection column. The table is read-only. |
| `SINGLE` | A leading radio column. |
| `MULTI` | A leading checkbox column. |

For `SINGLE` and `MULTI` every input shares the form-name
`"<table.id>__selection"` and carries the row id as its value, so a surrounding
[`form`](./form.md) submits the chosen ids under that one key.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiTable.of("demo-table", "Products")
    .column(UiColumn.of("name",  "Name"))
    .column(UiColumn.of("price", "Price"))
    .column(UiColumn.of("stock", "Stock"))
    .row(Map.of("id", "p1", "name", "Widget", "price", "€ 19.00", "stock", "128"))
    .row(Map.of("id", "p2", "name", "Gadget", "price", "€ 49.00", "stock", "12"))
    .row(Map.of("id", "p3", "name", "Gizmo",  "price", "€ 99.00", "stock", "0"))
    .action(UiAction.primary("t-add", "New").icon("add")
        .onClick(UiTrigger.go("/products/new")))
    .rowAction(UiAction.secondary("row-edit", "Edit")
        .onClick(UiTrigger.api("GET", "/products/{id}/edit")))
    .selectMode(UiTable.SelectMode.MULTI)
    .selectedRowIds(List.of("p2"))
    .selectedRow("p2")
    .stackOnMobile(true)
    .paginate(1, 3, 57, UiTrigger.api("GET", "/products?page={page}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "table", "id": "demo-table", "title": "Products",
  "selectMode": "MULTI", "selectedRowIds": ["p2"], "selectedRowId": "p2",
  "stackOnMobile": true,
  "actions": [
    { "type": "action", "id": "t-add", "label": "New", "style": "PRIMARY", "icon": "add",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products/new" } }
  ],
  "rowActions": [
    { "type": "action", "id": "row-edit", "label": "Edit", "style": "SECONDARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products/{id}/edit" } }
  ],
  "columns": [
    { "type": "column", "id": "col-name",  "label": "Name",  "dataKey": "name" },
    { "type": "column", "id": "col-price", "label": "Price", "dataKey": "price" },
    { "type": "column", "id": "col-stock", "label": "Stock", "dataKey": "stock" }
  ],
  "rows": [
    { "type": "row", "id": "p1", "data": { "name": "Widget", "price": "€ 19.00", "stock": "128" } },
    { "type": "row", "id": "p2", "data": { "name": "Gadget", "price": "€ 49.00", "stock": "12" } },
    { "type": "row", "id": "p3", "data": { "name": "Gizmo",  "price": "€ 99.00", "stock": "0" } }
  ],
  "pagination": {
    "page": 1, "size": 3, "total": 57,
    "pageTrigger": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products?page={page}" }
  }
}
```

</TabItem>
</Tabs>

## Sorting and a pinned header

Mark columns [`sortable`](./column.md#sorting) and the header becomes a click
target. `maxHeight` caps the row area so long tables scroll inside themselves
with the header row pinned:

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=table-sort"
  title="Live: sortable columns and a pinned header"
  loading="lazy"
  style={{width: '100%', height: '330px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — click SKU, Name, Price or Note to sort; click again to reverse. This
table has no `sortTrigger`, so the browser is doing it. Scroll the rows and the
header stays put.*

```java
UiTable.of("products", "Products")
       .column(UiColumn.of("name",  "Name").asSortable())
       .column(UiColumn.of("price", "Price").asSortable())
       .maxHeight("320px");
```

With a `sortTrigger` the server sorts instead — the placeholders are filled in
per header, exactly like `{page}` in `pageTrigger`:

```java
       .sortTrigger(UiTrigger.go("/products?sort={column}&dir={direction}"))
       .sortedBy("price", UiTable.SortDirection.DESC)
```

## Paging

The footer is display state plus a trigger template. `{page}` in the trigger's
`url` is replaced with the target page before dispatch — the same substitution
`{column}` gets for sorting.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=table-paging"
  title="Live: paging without a backend"
  loading="lazy"
  style={{width: '100%', height: '300px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — 7 rows, 3 per page. Prev and Next work with no server: the page number
rides along in the trigger and a handler swaps the table.*

<Tabs groupId="stack">
<TabItem value="spring" label="Server-side (Spring Boot)">

```java
@GetMapping("/products")
public UiPage products(@RequestParam(defaultValue = "1") int page) {
    var slice = repo.findPage(page, 20);          // your query, your paging
    var table = UiTable.of("products", "Products")
            .column(UiColumn.of("name",  "Name"))
            .column(UiColumn.of("price", "Price"));
    slice.forEach(p -> table.row(Map.of("id", p.getId(), "name", p.getName(),
                                        "price", money(p.getPriceCents()))));
    // {page} is substituted per button — Prev gets page-1, Next page+1.
    table.paginate(page, 20, repo.count(), UiTrigger.go("/products?page={page}"));
    return UiPage.of("/products", table);
}
```

The browser sends a normal request, the controller returns the next page, the
bus swaps the tree. Nothing client-side to write.

</TabItem>
<TabItem value="client" label="Client-side (no backend)">

```js
const SIZE = 3;
const pageTrigger = { behavior: "INVOKE", handler: "goPage", url: "{page}" };

function tableFor(page) {
  const start = (page - 1) * SIZE;
  return {
    type: "table", id: "paged", title: "Products",
    columns: [ /* … */ ],
    rows: ALL.slice(start, start + SIZE).map((d, i) => ({
      type: "row", id: `r${start + i}`, data: d
    })),
    pagination: { page, size: SIZE, total: ALL.length, pageTrigger }
  };
}

// The page number arrives as the substituted `url`.
bus.registerClientHandler("goPage", (ctx) => ({
  patches: [ { op: "REPLACE", targetId: "paged", node: tableFor(Number(ctx.trigger.url)) } ]
}));

renderer.mount(tableFor(1));
```

Same model, same footer — only *who* produces the next page differs. Swapping
the `INVOKE` trigger for `UiTrigger.go("/products?page={page}")` turns this into
the server version above without touching anything else.

</TabItem>
</Tabs>

:::tip Patch the table, not the page
`REPLACE` on the table's own id swaps just that subtree. The rest of the screen —
filters, sidebar, scroll position — stays exactly where it was.
:::

## Notes

**Client-side sorting sees only the rows on screen.** Without a `sortTrigger`
the browser reorders the rows it has: numeric when both values parse as numbers,
otherwise a locale-aware string compare, blanks last in both directions. That is
right for a fully loaded table and misleading for a paginated one — pair
`pagination` with a `sortTrigger`.

**`maxHeight` takes any CSS length.** `"420px"` for a fixed area, `"60vh"` to
scale with the viewport, `"100%"` inside a sized parent. Without it the table
grows with its content and the page scrolls, which is usually what you want for
short tables.

**Row actions are one template, not one per row.** You declare a single
`UiAction`; the renderer clones it per row, substitutes `{id}` in the trigger
`url`, and suffixes the button's DOM id with `__<row.id>` so ids stay unique.
That means the action's `label`, `confirm` and `style` are the same for every
row — anything row-dependent belongs in a column
[`cellTemplate`](./column.md) instead.

**`selectedRowId` and `selectedRowIds` are different things.** The singular
field only adds the `sui-row--selected` highlight class; it does not tick
anything. The plural field pre-checks the radio/checkbox inputs. Set both when
you want a pre-selected row that also looks selected.

**The table never slices.** It renders exactly the rows you hand it.
`page`/`size`/`total` are display state for the footer, and Prev/Next fire your
`pageTrigger` with the target page substituted for `{page}`. Without a
`pageTrigger` the buttons render disabled — the footer is then informational
only. See [Paging](#paging) below.

**Header actions no longer need a title.** The header bar renders when there is
a title **or** at least one action;
put the buttons in the surrounding [`stack`](./stack.md) instead.

**Wide tables and small screens.** `stackOnMobile` is the built-in answer —
each row becomes a card of `Column: value` lines, using each column's `label`
as the `data-label`. See [responsive layout](../responsive.md).

## See also

- **[`column`](./column.md)** — headers, data keys and custom cell rendering.
- **[`row`](./row.md)** — row identity and the cell data map.
- **[`list`](./list.md)** — when the records are not a grid.
- **[`action`](./action.md)** — what goes into `actions` and `rowActions`.
- **[Triggers & actions](../triggers.md)** — the `{id}` / `{page}` substitution contract.
- **[Responsive layout](../responsive.md)** — `stackOnMobile` in context.
