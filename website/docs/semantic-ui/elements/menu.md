---
title: Menu
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `menu` — the collapsible navigation sidebar

**`UiMenu`** is the vertical navigation sidebar an admin shell puts beside its
content. It holds nestable [`menu-item`](./menu-item.md)s and toggles between
three display states with a hamburger: **expanded** (icon + label, groups open
inline), **rail** (a narrow icon-only strip, groups fly out on hover) and
**hidden** (off-canvas).

The `state` field is only the *initial* state the server renders. Once the SPA
event bus is loaded the hamburger cycles the state client-side and remembers the
choice; with no JS at all the items are still real links and the groups still
native `<details>` disclosures.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=menu"
  title="Live: UiMenu"
  loading="lazy"
  style={{width: '100%', height: '300px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — click the hamburger to cycle expanded → rail → hidden; the content panel
reflows as the sidebar narrows. In the rail, hover "Catalog" for its fly-out.
Clicking an item patches the page title.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id`, the patch target and the localStorage key for the collapse state. |
| `title` | `String` | Sidebar heading shown above the items. |
| `items` | `List<UiMenuItem>` | The top-level entries. |
| `state` | `EXPANDED` · `RAIL` · `HIDDEN` | Initial display state. Defaults to `EXPANDED`. |
| `mode` | `PUSH` · `OVERLAY` · `RESPONSIVE` | How the sidebar relates to the content beside it. Defaults to `PUSH`. |
| `side` | `LEFT` · `RIGHT` | Which edge the menu sits on. Defaults to `LEFT`. |
| `toggle` | `Boolean` | Whether to render the menu's own hamburger. Defaults to `true`; set `false` when a [`header`](./header.md) owns it. |
| `cssClass` | `String` | Extra CSS class on the `<nav>`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiMenu.of("nav", "Admin",
    UiMenuItem.link("dash", "Dashboard", "/dash").icon("grid").selected(true),
    UiMenuItem.group("catalog", "Catalog",
        UiMenuItem.link("prod", "Products",  "/products").icon("document"),
        UiMenuItem.link("cust", "Customers", "/customers").icon("show")
    ).icon("folder").open(true),
    UiMenuItem.link("orders", "Orders", "/orders").icon("download").badge("12"),
    UiMenuItem.of("reload", "Reload").icon("add")
        .onClick(UiTrigger.api("POST", "/reload"))     // dispatches instead of navigating
).state(UiMenu.State.EXPANDED)
 .mode(UiMenu.Mode.RESPONSIVE)
 .side(UiMenu.Side.LEFT);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "menu", "id": "nav", "title": "Admin",
  "state": "EXPANDED", "mode": "RESPONSIVE", "side": "LEFT",
  "items": [
    { "type": "menu-item", "id": "dash", "label": "Dashboard", "icon": "grid",
      "href": "/dash", "selected": true },
    { "type": "menu-item", "id": "catalog", "label": "Catalog", "icon": "folder",
      "open": true, "children": [
        { "type": "menu-item", "id": "prod", "label": "Products", "icon": "document",
          "href": "/products" },
        { "type": "menu-item", "id": "cust", "label": "Customers", "icon": "show",
          "href": "/customers" }
      ] },
    { "type": "menu-item", "id": "orders", "label": "Orders", "icon": "download",
      "href": "/orders", "badge": "12" },
    { "type": "menu-item", "id": "reload", "label": "Reload", "icon": "add",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/reload" } }
  ] }
```

</TabItem>
</Tabs>

## Notes

**The three states cost no round-trip.** The hamburger is handled entirely by the
event bus: it reads the menu's current state, computes the next one and swaps a
CSS class, then persists the choice in `localStorage` under the menu's id. The
bus re-applies that stored state after every render, so a user's collapse choice
survives reloads and patches without any server involvement. The server can still
drive it explicitly — render or `REPLACE` the menu with a different `state` and it
appears that way.

**`mode` decides how the content reacts.** `PUSH` (the default) gives the menu
real layout space, so the content beside it reflows wider as the menu collapses —
place the menu next to the content in a horizontal [`stack`](./stack.md).
`OVERLAY` turns it into a drawer floating over the content with a dimmed backdrop
that closes on click; its container needs `position: relative`. `RESPONSIVE` is
the usual admin-shell behaviour: push on a wide screen (the hamburger flips
expanded ⇄ rail, so the sidebar never fully vanishes), an overlay drawer below
`max-width: 768px` (closed by default). See [responsive layout](../responsive.md).

**The hamburger can live in the header.** Set `UiHeader.menuToggle` to the menu's
id and turn the menu's own toggle off with `.toggle(false)`. The whole app shell
is then just composition — a header on top, `[menu, content]` in a horizontal
stack:

```java
UiStack.of(
    UiHeader.of("Acme Admin").menuToggle("nav"),
    UiStack.of(
        UiMenu.of("nav", "Acme", items).mode(UiMenu.Mode.RESPONSIVE).toggle(false),
        contentPanel
    ).direction(UiStack.Direction.HORIZONTAL).withCssClass("app-body")
).direction(UiStack.Direction.VERTICAL);
```

```css
.app-body { position: relative; overflow: hidden; align-items: stretch; }
```

**Every item is patch-addressable.** Each entry is a full `UiNode` of type
`menu-item`, so a patch can `REPLACE` a single one — flip its `selected`, swap a
label, update a badge count — without re-rendering the sidebar and losing its
scroll position or open groups.

**Widths are CSS variables.** Override them per app rather than per node:

```css
.sui-menu { --sui-menu-w: 260px; --sui-menu-rail-w: 56px; }
```

## See also

- **[`menu-item`](./menu-item.md)** — the entries, and what they inherit from `action`.
- **[`menu-button`](./menu-button.md)** — the transient dropdown / context menu.
- **[`header`](./header.md)** — where the hamburger usually lives.
- **[Responsive layout](../responsive.md)** — how `RESPONSIVE` flips at the breakpoint.
- **[Triggers & actions](../triggers.md)** — everything an item's `onClick` can do.
