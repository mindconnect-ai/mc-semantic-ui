---
title: App shell
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `app-shell` — the application frame

Header on top, navigation down the side, content filling the rest — the layout
nearly every back-office application shares. It is one node:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAppShell.of("shell")
    .header(UiHeader.of("Acme Admin")
                    .user(UiHeader.User.of("Ada Lovelace", "AL", "/me")))
    .menu(UiMenu.of("nav", "Acme",
            UiMenuItem.of("dash", "Dashboard").icon("grid").selected(true)
                      .onClick(UiTrigger.go("/dashboard")),
            UiMenuItem.group("cat", "Catalog",
                    UiMenuItem.of("prod", "Products").icon("document")
                              .onClick(UiTrigger.go("/products")),
                    UiMenuItem.of("cust", "Customers").icon("document")
                              .onClick(UiTrigger.go("/customers"))
            ).icon("folder").open(true),
            UiMenuItem.of("ord", "Orders").icon("download").badge("12")
                      .onClick(UiTrigger.go("/orders")))
        .mode(UiMenu.Mode.RESPONSIVE))
    .content(productsTable)
    .footer(UiText.of("foot", "Acme Admin · v2.4.0"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "app-shell", "id": "shell",
  "header": { "type": "header", "id": "hdr", "brand": "Acme Admin",
              "user": { "name": "Ada Lovelace", "initials": "AL" } },
  "menu": { "type": "menu", "id": "nav", "title": "Acme", "mode": "RESPONSIVE",
            "items": [ /* menu-item nodes */ ] },
  "content": { "type": "stack", "id": "page", "children": [ /* the page */ ] },
  "footer": { "type": "text", "id": "foot", "text": "Acme Admin · v2.4.0" } }
```

</TabItem>
</Tabs>

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=app-shell"
  title="Live: the app shell"
  loading="lazy"
  style={{width: '100%', height: '330px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — click a menu entry: only the content panel changes. Click the burger to
collapse the sidebar. The frame is narrow, so the menu behaves as it would on a
phone; widen the window and it flips between expanded and rail instead.*

## What the node does for you

Three slots — `header`, `menu`, `content` — and two couplings that are easy to
get wrong by hand:

- **The burger is wired to the menu.** The header renders one toggle pointed at
  the menu's id, and the menu's own toggle is switched off. Compose this by hand
  and forgetting either half gives you two burgers or none.
- **The layout rules ship in `sui.css`.** The body is a positioning context so an
  overlay drawer stays inside the shell instead of escaping to the viewport, and
  the content has `min-width: 0` so a wide table can't push the sidebar
  off-screen. Neither is obvious, and both fail in ways that look like a
  styling bug.
- **It fills the window.** Header and footer keep their height, the body takes
  the rest, and the page scrolls *inside* the content area rather than moving the
  whole shell — so the sidebar always reaches the bottom. That is the default in
  every comparable layout (Ant Design's `Layout`, Vuetify's `v-app`, the usual
  Bootstrap admin template). Set `fillViewport: false` when the shell is embedded
  in a larger page.

Your nodes are never mutated — the renderer works on copies, so the same `UiMenu`
object can be reused across pages.

## Navigating without re-rendering the chrome

The content container has a predictable id: **`<shell-id>-content`**, and it is a
**slot** — a `REPLACE` aimed at it fills the container instead of deleting it, so
the shell's layout survives. That makes "replace the page, keep the navigation" a
one-operation patch:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
var shell = UiAppShell.of("shell");   // built once, wherever you assemble pages

return UiPatch.of()
        .patch(UiPatch.Operation.replace(shell.contentId(), productsTable))
        .toast(UiToast.success("Loaded"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "patches": [
    { "op": "REPLACE", "targetId": "shell-content",
      "node": { "type": "table", "id": "products", "title": "Products" } }
  ] }
```

</TabItem>
</Tabs>

The alternative is to return the whole shell on every navigation. That is
perfectly fine and simpler to reason about: the morpher reuses the header and
menu DOM because their ids match, so nothing flickers. Use the patch when the
sidebar is expensive to build or holds state you'd rather not recompute.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Shell id. The content container becomes `<id>-content`. |
| `header` | `UiHeader` | Top bar. Its `menuToggle` is set for you. |
| `menu` | `UiMenu` | Side navigation. Its `toggle` is forced to `false`. |
| `content` | `UiNode` | The page. Any node. |
| `footer` | `UiNode` | Optional bar across the bottom, outside the scroll area. |
| `fillViewport` | `boolean` | Default `true` — the shell fills the window and the content scrolls inside it. `false` takes the container's height instead. |
| `cssClass` | `String` | Extra class on the shell wrapper. |

A `menu` with `side: RIGHT` is rendered after the content, so the sidebar sits on
the right in `PUSH` mode too.

## Which menu mode?

`UiMenu.mode` decides how the sidebar behaves when toggled:

| Mode | Behaviour | Use for |
|---|---|---|
| `PUSH` | The sidebar shares the row; content reflows wider as it collapses. | Desktop-only tools. |
| `OVERLAY` | The sidebar floats over the content with a backdrop. Content never moves. | Narrow layouts, or when reflow is distracting. |
| `RESPONSIVE` | Expanded ⇄ rail on a wide screen; an overlay drawer below 768px. | **The default choice** for anything that has to work on a phone. |

## Building it by hand

The node is a convenience, not a requirement — it emits ordinary nodes, and you
can compose the same thing yourself when you need a layout it doesn't cover (a
second sidebar, a split content area, a shell inside a shell):

```
stack (VERTICAL)              ← needs min-height: 100dvh
├── header                    ← menuToggle: "<menu id>"
├── stack (HORIZONTAL)        ← needs position: relative, flex: 1, min-height: 0
│   ├── menu                  ← toggle: false
│   └── stack                 ← needs flex: 1, min-width: 0, overflow: auto
└── footer                    ← optional
```

You then own the layout CSS. The rules the node applies are
`.sui-shell`, `.sui-shell-body` and `.sui-shell-content` in `sui.css` — worth
reading before writing your own.

## See also

- **[`menu`](./menu.md)** — states, modes, nesting, badges.
- **[`header`](./header.md)** — brand, extras, user widget.
- **[Mobile & responsive](../responsive.md)** — how the shell behaves on a phone.
- **[Triggers cookbook](../triggers-cookbook.md)** — more patch-in-place recipes.
