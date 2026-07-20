---
title: Tree node
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `tree-node` — one row of a tree

**`UiTreeNode`** is a single row inside a [`tree`](./tree.md) — and, because it is a
full `UiNode` with its own id rather than a nested value object, an individually
addressable one. It carries a label, an icon, an optional click
[trigger](../triggers.md), nested `children`, and — the interesting part — a
`labelNode` and a `content` slot that each take **any other node**.

That is what turns a tree from a list of strings into a real component: a row's title
can be a stack with a badge and a context menu, and an expanded row can hold a detail
panel, a form or a chart.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=tree-node"
  title="Live: UiTreeNode"
  loading="lazy"
  style={{width: '100%', height: '420px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — a tree node cannot render on its own, so this is a small `tree` showing one
node's features. Row 2's `labelNode` is a stack holding a menu-button (open the "⋮"
menu); row 3 expands to reveal a `detail` node in its `content`.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — the DOM `id`, the `data-id`, and the patch target for this row. |
| `label` | `String` | Plain-text label. Stays the accessible fallback when `labelNode` is set. |
| `labelNode` | `UiNode` | Rich row title rendered *instead of* the `label` text. Any node. |
| `icon` | `String` | Leading icon token shown before the label. See [icons](./icon.md). |
| `onClick` | `UiTrigger` | Fired when the label is clicked. `null` = static, non-clickable label. |
| `content` | `UiNode` | Arbitrary body rendered inside the row when expanded, above the children. |
| `children` | `List<UiTreeNode>` | Child rows. Defaults to an empty list. |
| `open` | `boolean` | *Initial* expanded state. Defaults to `false`. User toggles win afterwards. |
| `selected` | `boolean` | Defaults to `false`. `true` renders the row highlighted. |
| `title` | `String` | Inherited from `UiNode`; unused by this renderer — use `label`. |
| `cssClass` | `String` | Extra CSS class on the `<li>`. |

A node with `children` **or** `content` is *expandable* and renders as a native
`<details>` disclosure. A node with neither is a leaf and renders as a plain row.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// A leaf row: icon, label, click.
UiTreeNode.of("t-app", "app.ts").icon("document")
          .onClick(UiTrigger.go("/files/app.ts"));

// href(…) is shorthand for .onClick(UiTrigger.go(…)).
UiTreeNode.of("t-pom", "pom.xml").icon("document").href("/files/pom.xml");

// A rich label: the row title is a stack of the name plus a per-row context menu.
UiTreeNode.of("cf-report", "report.pdf").icon("document")
    .labelNode(UiStack.of(
        UiText.of("report.pdf"),
        UiMenuButton.of("cf-report-menu",
            UiMenuItem.of("cf-report-ren", "Rename").icon("edit")
                      .onClick(UiTrigger.toast("Rename report.pdf")),
            UiMenuItem.of("cf-report-dl", "Download").icon("download")
                      .onClick(UiTrigger.toast("Downloading report.pdf")),
            UiMenuItem.divider(),
            UiMenuItem.of("cf-report-del", "Delete").icon("delete").danger(true)
                      .onClick(UiTrigger.toast(UiToast.error("Deleted report.pdf")))
        )).direction(UiStack.Direction.HORIZONTAL).gap(8));

// A rich body: the expanded row shows a detail panel, then its children.
UiTreeNode.of("r-order", "Order #1024").icon("document").open(true)
    .content(UiDetail.of("r-order-detail", null)
        .field(UiField.text("r-cust", "Customer", "Grace Hopper"))
        .field(UiField.number("r-total", "Total", 249.0)))
    .child(UiTreeNode.of("r-line-1", "1 × Keyboard").icon("document"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "tree-node", "id": "t-app", "label": "app.ts", "icon": "document",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/files/app.ts" } }

{ "type": "tree-node", "id": "cf-report", "label": "report.pdf", "icon": "document",
  "labelNode": {
    "type": "stack", "id": "cf-report-lbl", "direction": "HORIZONTAL", "gap": 8,
    "children": [
      { "type": "text", "id": "cf-report-name", "text": "report.pdf" },
      { "type": "menu-button", "id": "cf-report-menu", "align": "END", "items": [
        { "type": "menu-item", "id": "cf-report-ren", "label": "Rename", "icon": "edit" },
        { "type": "menu-item", "id": "cf-report-sep", "divider": true },
        { "type": "menu-item", "id": "cf-report-del", "label": "Delete",
          "icon": "delete", "danger": true }
      ] }
    ] } }

{ "type": "tree-node", "id": "r-order", "label": "Order #1024", "icon": "document",
  "open": true,
  "content": { "type": "detail", "id": "r-order-detail", "fields": [
    { "type": "field", "id": "r-cust", "label": "Customer", "fieldType": "TEXT",
      "value": "Grace Hopper" } ] },
  "children": [
    { "type": "tree-node", "id": "r-line-1", "label": "1 × Keyboard", "icon": "document" } ] }
```

</TabItem>
</Tabs>

## Notes

**`labelNode` takes *any* node — that is the escape hatch.** The row title is
rendered through the same renderer registry as everything else, so a stack, a badge,
a progress bar or a [`menu-button`](./menu-button.md) all work. A per-row context
menu is exactly this: `labelNode` = stack of the name plus a menu-button aligned to
`END`. Keep `label` filled in as the plain-text fallback.

**`content` vs. `children`.** `children` are more tree rows; `content` is one
arbitrary component rendered above them inside the disclosure. Both make the node
expandable, and a node can have both. Use `content` when a row needs a detail panel
or a chart rather than more hierarchy.

**Patch one row, not the tree.** Because each node has its own DOM id, a `REPLACE`
patch on that id re-renders just that row and its subtree, and `REMOVE` drops it.
This is how you lazily load children on click: the `onClick` trigger returns a patch
that replaces the clicked node with a version that has `children` filled in.

**`open` is a starting value only.** The disclosure carries
`data-sui-client-collapse`, so the user's manual expand/collapse survives re-renders
and streaming patches. Sending a new `open` value in a patch will not force a row
open once the user has touched it.

## See also

- **[`tree`](./tree.md)** — the container these rows live in.
- **[`menu-button`](./menu-button.md)** — the usual passenger in a `labelNode`.
- **[`detail`](./detail.md)** — the usual passenger in `content`.
- **[Triggers & actions](../triggers.md)** — what `onClick` can do.
