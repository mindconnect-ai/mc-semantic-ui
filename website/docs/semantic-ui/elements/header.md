---
title: Header
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `header` — application chrome

**`UiHeader`** is the top bar of an application: a brand on the left, an optional
current-user widget on the right, and room for a few extra widgets in between. It is
deliberately small and opinionated — a reusable chrome primitive, not a generic
top-bar builder.

Its intended place is the very first child of a page's top-level stack, above the
[`section`](./section.md) that holds the content. The typical layout is
header → menu/tabs → body.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=header"
  title="Live: UiHeader"
  loading="lazy"
  style={{width: '100%', height: '190px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — one `header` node: logo plus brand text (a link), two `action` nodes in
`extras`, and the user widget with its CSS-drawn initials avatar. Click any of them.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` on the `<header>` (omitted when null). |
| `brand` | `String` | Brand text on the left. Plain string; HTML is escaped. |
| `brandHref` | `String` | Link target for the brand. Set = anchor, null = inert text. |
| `brandLogo` | `String` | Logo image URL rendered before the brand text, inside the same anchor/span. |
| `user` | `UiHeader.User` | Current-user widget on the right. `null` hides it entirely. |
| `menuToggle` | `String` | Id of a [`menu`](./menu.md) to control. Set = a hamburger button at the far left. |
| `extras` | `List<UiNode>` | Extra widgets between brand and user. Any nodes; the renderer recurses. |
| `extrasOverflow` | `WRAP` · `MENU` | What happens when the extras don't fit. `WRAP` (default) grows a second line; `MENU` keeps one row and collapses the rest into a `⋯` dropdown. |
| `title` | `String` | Inherited from `UiNode`; unused by this renderer. |
| `cssClass` | `String` | Extra CSS class on the `<header>`. |

`UiHeader.User` is a small nested type:

| Field | Type | Meaning |
|---|---|---|
| `name` | `String` | Display name shown next to the avatar (also the `title` tooltip). |
| `initials` | `String` | 1–2 characters drawn inside the avatar circle. Not truncated — pass exactly what you want. |
| `profileHref` | `String` | Navigation target when the widget is clicked. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiHeader.of("Shop Admin")
        .brandHref("/")
        .brandLogo("/img/logo.svg")
        .user(UiHeader.User.of("Ada Lovelace", "AL", "/profile"))
        .extra(UiAction.primary("new-order", "New order").icon("add")
                       .onClick(UiTrigger.go("/orders/new")));

// Move the hamburger out of the sidebar and into the top bar:
UiHeader.of("Shop Admin").menuToggle("main-menu");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "header", "id": "app-header",
  "brand": "Shop Admin",
  "brandHref": "/",
  "brandLogo": "/img/logo.svg",
  "menuToggle": "main-menu",
  "extras": [
    { "type": "action", "id": "new-order", "label": "New order", "icon": "add",
      "style": "PRIMARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/orders/new" } }
  ],
  "user": { "name": "Ada Lovelace", "initials": "AL", "profileHref": "/profile" } }
```

</TabItem>
</Tabs>

## When the room runs out

A header has three greedy parts — brand, extras, user widget — and a phone gives
it about 380 pixels. Rather than squeezing all three, the stylesheet gives up the
least informative pixels first: **the user's name disappears below 768px** (the
avatar still identifies them and stays tappable), gaps and padding tighten, and
a long brand name truncates with an ellipsis.

That leaves the extras, and there you have a choice — the same one
[`section`](./section.md) offers for tabs:

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=header-responsive"
  title="Live: header overflow"
  loading="lazy"
  style={{width: '100%', height: '260px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — two headers with identical extras. Narrow your window: the first grows a
second line, the second keeps one row and moves the rest behind `⋯`.*

| | Behaviour | Needs JS |
|---|---|---|
| **`WRAP`** (default) | The bar grows taller; extras wrap onto another line. | no |
| **`MENU`** | One row, overflow collapses into a `⋯` dropdown. | yes |

```java
UiHeader.of("Acme Admin")
        .extrasOverflow(UiHeader.ExtrasOverflow.MENU);
```

`MENU` is a **progressive enhancement** and needs no wiring: the event bus runs
the shared [overflow behaviour](../overflow.md) on every mount. Without
JavaScript the extras simply wrap — nothing is ever unreachable.

## Notes

**It is chrome, not content.** The header belongs once per page, at the top of the
page-level stack. Nothing about it is layout-generic — if you need a second bar, a
toolbar or a breadcrumb strip, compose plain [`stack`](./stack.md)s instead of
stretching this node.

**`extras` is the extension point.** Each entry goes back through the renderer, so a
theme picker, a language switcher built from a [`form`](./form.md) with a
`submitOnChange` select, or a node type of your own all drop straight in. They are
laid out side-by-side in a flex container, before the user widget.

**`menuToggle` pairs the header with a drawer menu.** Setting it to a
[`menu`](./menu.md) node's id renders a hamburger at the far left that cycles that
menu's state — the same `data-menu-toggle` hook the menu's own button uses. This is
the usual arrangement for an admin shell with an overlay menu.

**The avatar needs no asset.** It is a CSS circle with `initials` inside — no image
request, no JavaScript. `brandLogo`, on the other hand, is a real `<img>`; its `alt`
is the brand text.

**Everything is escaped.** `brand`, `initials` and `name` are plain strings that
survive escaping unchanged — accents and symbols are fine, raw HTML is not. Use
`extras` when you need markup.

## See also

- **[`menu`](./menu.md)** — the sidebar/drawer the `menuToggle` hamburger controls.
- **[`action`](./action.md)** — the usual passenger in `extras`.
- **[`section`](./section.md)** — the content that sits below the header.
- **[Triggers & actions](../triggers.md)** — how the brand and user links navigate.
