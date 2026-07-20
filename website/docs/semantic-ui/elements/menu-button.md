---
title: Menu button
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `menu-button` — dropdown and context menus

**`UiMenuButton`** is a button that opens a floating menu of
[`menu-item`](./menu-item.md)s anchored to itself. Where [`menu`](./menu.md) is
the persistent sidebar, a menu button is transient: it lives closed, opens on
click, and closes on outside-click, <kbd>Esc</kbd> or when an entry is chosen.

It is deliberately placeable anywhere a node can go — on its own as a toolbar
overflow ("⋮"), or dropped inside another node to become that node's context
menu, which is the standard pattern for table and tree rows.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=menu-button"
  title="Live: UiMenuButton"
  loading="lazy"
  style={{width: '100%', height: '290px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — open the kebab on the left (the context-menu shape); its "Delete" entry
asks for confirmation first. The labelled "Actions" dropdown on the right has a
nested "Move to" submenu. Click outside or press Escape to close.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `items` | `List<UiMenuItem>` | The entries shown in the popover. |
| `icon` | `String` | Trigger glyph token. Defaults to `more` (a vertical "⋮") when absent. |
| `label` | `String` | Optional trigger text; when set the trigger renders as a labelled button with a caret. |
| `variant` | `ICON` · `BUTTON` | Trigger look. Defaults to `BUTTON` when a `label` is set, else `ICON`. |
| `align` | `START` · `END` | Which edge of the trigger the popover lines up with. `START` opens rightwards; `END` (the default) right-aligns it. |
| `cssClass` | `String` | Extra CSS class on the wrapper. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The kebab: icon-only trigger, a divider, a danger entry with a confirm.
UiMenuButton.of("row-menu",
    UiMenuItem.of("ren", "Rename").icon("edit").onClick(UiTrigger.api("POST", "/rename/42")),
    UiMenuItem.of("dl",  "Download").icon("download").onClick(UiTrigger.download("/files/42")),
    UiMenuItem.divider(),
    UiMenuItem.of("del", "Delete").icon("delete").danger(true)
            .confirm("Delete this file?")
            .onClick(UiTrigger.api("DELETE", "/files/42"))
).align(UiMenuButton.Align.END);

// The labelled dropdown, with a nested submenu:
UiMenuButton.of("acts",
    UiMenuItem.of("exp", "Export CSV").icon("download").onClick(UiTrigger.go("/export")),
    UiMenuItem.group("move", "Move to",
        UiMenuItem.of("inbox",   "Inbox").onClick(UiTrigger.api("POST", "/move/inbox")),
        UiMenuItem.of("archive", "Archive").onClick(UiTrigger.api("POST", "/move/archive"))
    ).icon("folder"),
    UiMenuItem.link("help", "Help", "/help").icon("info")
).label("Actions").icon("grid");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "menu-button", "id": "row-menu", "align": "END",
  "items": [
    { "type": "menu-item", "id": "ren", "label": "Rename", "icon": "edit",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/rename/42" } },
    { "type": "menu-item", "id": "sep", "divider": true },
    { "type": "menu-item", "id": "del", "label": "Delete", "icon": "delete",
      "danger": true, "confirm": "Delete this file?",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/files/42" } }
  ] }

{ "type": "menu-button", "id": "acts", "label": "Actions", "icon": "grid", "variant": "BUTTON",
  "items": [
    { "type": "menu-item", "id": "exp", "label": "Export CSV", "icon": "download",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/export" } },
    { "type": "menu-item", "id": "move", "label": "Move to", "icon": "folder",
      "children": [
        { "type": "menu-item", "id": "inbox", "label": "Inbox",
          "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/move/inbox" } }
      ] }
  ] }
```

</TabItem>
</Tabs>

## Notes

**A menu button in a row is the standard context menu.** Because it is just a
node, drop one into a [`table`](./table.md) cell or a [`tree`](./tree.md) node's
`labelNode` and every row gains its own "⋮" of per-row actions — without widening
the row with three separate buttons:

```java
UiTreeNode.of("f1", null).labelNode(UiStack.of(
    UiText.of("report.pdf"),
    UiMenuButton.of("f1-menu",
        UiMenuItem.of("f1-ren", "Rename").icon("edit").onClick(/* … */),
        UiMenuItem.divider(),
        UiMenuItem.of("f1-del", "Delete").icon("delete").danger(true)
                .confirm("Delete report.pdf?").onClick(/* … */)
    )).direction(UiStack.Direction.HORIZONTAL).gap(8));
```

**It is never clipped.** The popover is rendered inline (so it exists in SSR
markup and stays inside the event bus's scope) but positioned with
`position: fixed` at open time. That is what makes the row pattern above work: a
scrolling table body or an `overflow: hidden` app shell cannot cut the menu off.
The bus flips it above the trigger when there is no room below, clamps it into
the viewport, and flips submenus to the other side near the right edge.

**Opening degrades gracefully.** The trigger is a native `<details>`/`<summary>`,
so with no JS at all clicking it still opens and closes the popover and each
entry's `href` still navigates. The bus takes over on top: outside-click and
<kbd>Esc</kbd> to close, closing after a choice, and dispatching each entry's
`onClick` instead of navigating.

**Danger and dividers carry the meaning.** A popover is the one place where
[`menu-item`](./menu-item.md)'s `divider` and `danger` earn their keep — separate
the destructive entry from the benign ones and colour it. Add the inherited
`confirm` to that entry: the colour warns, the confirm actually guards.

**Two shapes, one type.** No `label` means an icon-only kebab (`variant: ICON`,
default glyph `more`); setting a `label` promotes it to a labelled dropdown
button. `align: END` is right for a trailing kebab at the end of a row; use
`START` when the trigger sits at the left edge and the menu should open
rightwards.

## See also

- **[`menu-item`](./menu-item.md)** — the entries, and the `UiAction` fields they inherit.
- **[`menu`](./menu.md)** — the persistent sidebar counterpart.
- **[`action`](./action.md)** — a single control instead of several behind one.
- **[`table`](./table.md)** and **[`tree`](./tree.md)** — where per-row context menus belong.
- **[Triggers & actions](../triggers.md)** — everything an entry's `onClick` can do.
