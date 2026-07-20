---
title: Menu item
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `menu-item` — one entry in a menu

**`UiMenuItem`** is a single entry in a [`menu`](./menu.md) sidebar or a
[`menu-button`](./menu-button.md) popover — a leaf link, or a group that nests
further entries. Like a tree node, every item is a full `UiNode` (type
`menu-item`), so a patch can `REPLACE` one entry without re-rendering the menu
around it.

Crucially, **`UiMenuItem extends UiAction`**. An item *is* a clickable action: it
inherits `label`, `icon`, `onClick`, `confirm`, `enabled` / `disabledReason`,
`style`, `appearance` and `loading` unchanged, and adds only the menu-specific
fields on top. The ones the menu renderers act on are `label`, `icon`, `onClick`
and `confirm` — a confirmation dialog before a destructive click works here
exactly as it does on a button, with no extra machinery.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=menu-item"
  title="Live: UiMenuItem"
  loading="lazy"
  style={{width: '100%', height: '320px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — an item cannot render on its own, so this is one small `menu` plus one
`menu-button`. The menu shows `selected`, `badge`, a nested group and a plain
`href` leaf; open the "Popover items" dropdown for a `divider` and a `danger`
entry whose inherited `confirm` asks before firing.*

## Fields

### Inherited from `UiAction`

These are not redeclared on `UiMenuItem` — they are the same fields, with the
same semantics, as on [`action`](./action.md).

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — the DOM `id` and the patch target for this single entry. |
| `label` | `String` | The entry's text. |
| `icon` | `String` | Icon token shown before the label — and the *only* thing visible in the rail. |
| `onClick` | `UiTrigger` | What happens on click; dispatches through the event bus. `null` = inert. |
| `confirm` | `String` | Confirmation text. The bus asks before firing `onClick`. |
| `enabled` | `boolean` | Present on the model; the menu renderers do not currently dim the entry — use `onClick: null` for an inert entry. |
| `disabledReason` | `String` | Present on the model; not rendered by the menu renderers. |
| `loading` | `boolean` | Present on the model; not rendered by the menu renderers. |
| `style` | `PRIMARY` · `SECONDARY` · `DANGER` | Inherited colour scheme; menu entries take their colour from `danger` instead. |
| `appearance` | `BUTTON` · `LINK` · `ICON` | Inherited DOM shape; a sidebar entry always renders as an `<a>`, a popover entry as a `<button>`. |
| `cssClass` | `String` | Extra CSS class on the entry. |

### Own fields

| Field | Type | Meaning |
|---|---|---|
| `href` | `String` | Navigation target for a leaf — and the no-JS fallback when `onClick` is set. |
| `selected` | `boolean` | Marks the current entry; rendered active (`aria-current`). |
| `badge` | `String` | Short trailing count or status (`"12"`, `"new"`); shrinks to a dot on the icon in the rail. |
| `open` | `boolean` | For a group: render it initially expanded. |
| `danger` | `boolean` | Destructive entry (Delete, Remove) — rendered in the danger colour. |
| `divider` | `boolean` | This entry is a non-interactive separator line; its other fields are ignored. |
| `children` | `List<UiMenuItem>` | Nested entries. Non-empty makes this item a collapsible group / fly-out. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// A leaf link (the no-JS-friendly form):
UiMenuItem.link("prod", "Products", "/products").icon("document").badge("12");

// A leaf that dispatches instead of navigating:
UiMenuItem.of("reload", "Reload").icon("add")
        .onClick(UiTrigger.api("POST", "/reload"));

// A group that nests further entries, rendered initially open:
UiMenuItem.group("catalog", "Catalog",
        UiMenuItem.link("prod", "Products",  "/products").icon("document"),
        UiMenuItem.link("cust", "Customers", "/customers").icon("show")
).icon("folder").open(true);

// Inherited UiAction behaviour — confirm works exactly as on a button:
UiMenuItem.of("del", "Delete").icon("delete").danger(true)
        .confirm("Delete this product?")
        .onClick(UiTrigger.api("DELETE", "/products/42"));

// A separator (chiefly inside a menu-button popover):
UiMenuItem.divider();
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "menu-item", "id": "prod", "label": "Products", "icon": "document",
  "href": "/products", "badge": "12", "selected": true }

{ "type": "menu-item", "id": "catalog", "label": "Catalog", "icon": "folder", "open": true,
  "children": [
    { "type": "menu-item", "id": "cust", "label": "Customers", "icon": "show",
      "href": "/customers" }
  ] }

{ "type": "menu-item", "id": "del", "label": "Delete", "icon": "delete", "danger": true,
  "confirm": "Delete this product?",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/products/42" } }

{ "type": "menu-item", "id": "sep", "divider": true }
```

</TabItem>
</Tabs>

## Notes

**The inheritance is the point.** `UiMenuItem extends UiAction` deliberately, so
there is exactly one clickable vocabulary in the framework. A menu entry needs no
special-cased confirm dialog and no parallel trigger field — the renderer emits
the same `data-confirm` attribute, and the bus treats the entry like any other
action. If you know [`action`](./action.md), you already know most of
`menu-item`. (Not every inherited field is *painted* by the menu renderers — see
the table above — but the model and the click semantics are shared.)

**`href` and `onClick` are complementary, not exclusive.** A leaf with only
`href` is a real link that navigates with no JS at all. Add an `onClick` and the
bus intercepts the click and dispatches instead, keeping `href` as the
progressive-enhancement fallback — the entry still works if scripting is off.

**Groups behave differently per menu state.** An item with `children` renders as
a native `<details>` disclosure that expands inline while the menu is expanded;
collapse the menu to its rail and the same children become a hover fly-out.
`open` only sets the initial disclosure state.

**`danger` and `divider` are popover grammar.** They matter most inside a
[`menu-button`](./menu-button.md): a divider separates a benign group of entries
from the destructive one, and `danger` colours that last entry. Pair `danger`
with an inherited `confirm` — the visual warning and the actual guard rail are
two different things.

**Patch one entry, not the menu.** Because each item carries its own `id`,
flipping the active page is a single `REPLACE` of the outgoing and incoming
entries. Re-rendering the whole menu would reset scroll position and open groups.

## See also

- **[`action`](./action.md)** — the parent type; every inherited field is documented there.
- **[`menu`](./menu.md)** — the sidebar these entries live in.
- **[`menu-button`](./menu-button.md)** — the popover that reuses the same entries.
- **[`tree`](./tree.md)** — the other nestable, per-node-patchable structure.
- **[Triggers & actions](../triggers.md)** — everything an `onClick` can do.
