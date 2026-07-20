---
title: Detail
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `detail` — one record, read-only

**`UiDetail`** shows a single record as a definition list: label on the left,
value on the right, actions and links underneath. It is the natural counterpart
to [`form`](./form.md) — and deliberately so, because it reuses the very same
`UiField` nodes for its rows. A detail and a form describe the same data with
the same vocabulary.

Reach for `UiDetail` when the screen is *one* thing: a product, an order, a
user. For many rows of the same shape use [`table`](./table.md); for a
collection of items with differing shapes use [`list`](./list.md).

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=detail"
  title="Live: UiDetail"
  loading="lazy"
  style={{width: '100%', height: '490px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — a read-only record with an action row and a link. "Note" has no value,
so the renderer prints an em dash placeholder. "Delete" asks for confirmation
before it fires.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id and DOM `id`. The usual patch target — replace the whole record in one operation. |
| `title` | `String` | Rendered as the `<h2>` above the rows. Omitted entirely when absent. |
| `fields` | `List<UiField>` | The rows. Rendered read-only as `<dt>`/`<dd>` pairs — only `label` and `value` are used. Defaults to an empty list. |
| `actions` | `List<UiAction>` | Buttons in the footer (Edit, Delete, …). Defaults to an empty list. |
| `links` | `List<UiLink>` | Plain navigation links in the same footer row, after the actions. Defaults to an empty list. |
| `cssClass` | `String` | Extra CSS class added next to `sui-detail`. |

A field with a `null` value renders as `<span class="sui-empty">—</span>` rather
than an empty cell, so the row stays visible and the grid stays aligned.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiDetail.of("product-detail", "Product")
    .field(UiField.text("d-sku",    "SKU",      p.getSku()))
    .field(UiField.text("d-name",   "Name",     p.getName()))
    .field(UiField.number("d-price","Price",    p.getPrice()))
    .field(UiField.number("d-stock","In stock", p.getStock()))
    .field(UiField.bool("d-active", "Active",   p.isActive()))
    .action(UiAction.primary("d-edit", "Edit").icon("edit")
            .onClick(UiTrigger.go("/products/" + p.getId() + "/edit")))
    .action(UiAction.danger("d-del", "Delete").icon("delete")
            .confirm("Delete this product?")
            .onClick(UiTrigger.api("DELETE", "/products/" + p.getId())))
    .link(UiLink.of("ref", "/products/" + p.getId() + "/history", "View history"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "detail", "id": "product-detail", "title": "Product",
  "fields": [
    { "type": "field", "id": "d-sku",    "label": "SKU",      "fieldType": "TEXT",    "value": "WID-1024" },
    { "type": "field", "id": "d-name",   "label": "Name",     "fieldType": "TEXT",    "value": "Gadget" },
    { "type": "field", "id": "d-price",  "label": "Price",    "fieldType": "NUMBER",  "value": 49.0 },
    { "type": "field", "id": "d-active", "label": "Active",   "fieldType": "BOOLEAN", "value": true }
  ],
  "actions": [
    { "type": "action", "id": "d-edit", "label": "Edit", "style": "PRIMARY", "icon": "edit",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products/42/edit" } }
  ],
  "links": [
    { "type": "link", "id": "d-history", "rel": "ref",
      "href": "/products/42/history", "label": "View history" }
  ] }
```

</TabItem>
</Tabs>

## Notes

**View and edit are the same model.** Because a detail's rows *are* `UiField`s,
a screen can flip from read-only to editable by returning a `UiForm` with the
same field ids in place of the `UiDetail` — one `REPLACE` patch, no duplicated
labels, no second mapping layer on the server. Keeping the ids stable is what
makes the swap free.

**Only `label` and `value` reach the DOM.** The detail renderer ignores
`fieldType`, `editable`, `required`, `placeholder`, `hint` and the rest — it
prints text. So format for humans on the server: a price becomes `"€ 49.00"`, a
date becomes the string you want the user to read. Do not expect the detail to
localise a number for you.

**It is a first-class patch target.** Fill a detail from a click in a
neighbouring list or table without reloading the page: return a `UiPatch` that
REPLACEs the `product-detail` node with the newly selected record. The list's
scroll position and any open disclosure survive. See the
[triggers cookbook](../triggers-cookbook.md).

**Actions and links share one footer.** They render in a single row —
`actions` first, then `links` — so put commands in `actions` and navigation in
`links` rather than styling a link as a button. Several related commands
collapse nicely into a [`menu-button`](./menu-button.md).

## See also

- **[`form`](./form.md)** — the editable counterpart, same `UiField` rows.
- **[`field`](./field.md)** — what a row actually is.
- **[`list`](./list.md)** / **[`table`](./table.md)** — many records instead of one.
- **[Triggers cookbook](../triggers-cookbook.md)** — patching a detail from a selection.
