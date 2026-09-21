---
title: Drawer
---

# `drawer` — a panel from an edge, minimizable to a handle

**`UiDrawer`** is a panel that slides in from one edge — top, bottom, left or
right — and can be open, minimized to a small handle, or closed. It belongs to
the whole window or to a container such as a dialog. A typical use is the AI
chat of a mail composer: it comes up from the bottom over the buttons and
minimizes to a "✨ Draft with AI" handle without losing the conversation.

```java
UiDrawer.of("ai-chat", "Draft with AI", UiCustom.of("chat-widget").prop("session", sid))
    .edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER)
    .size("45%").minSize("200px").maxSize("80%").resizable()
    .icon("sparkles").badge("1")
    .onClose(UiTrigger.api("DELETE", "/chat/" + sid))
    .onStateChange(UiTrigger.api("POST", "/chat/" + sid + "/state/{state}"));
```

## Fields

| Field | Type | Meaning |
|---|---|---|
| `title` | `String` | Shown on the handle and in the header; the panel's accessible name. |
| `content` | `UiNode` | Anything — a form, a list, a plugin's [`UiCustom`](../extension-assets.md#a-node-type-without-java). |
| `icon`, `badge` | `String` | Leading icon (handle and header); a short count or status on the handle. |
| `edge` | `TOP` · `BOTTOM` · `LEFT` · `RIGHT` | Where it slides in from. Default `RIGHT`. |
| `state` | `OPEN` · `MINIMIZED` · `CLOSED` | Minimized shows only the handle; closed shows nothing. **Absent means "as it is"**: a new drawer opens, and a replaced one keeps the state the user chose. |
| `size`, `minSize`, `maxSize` | CSS length | Height for TOP/BOTTOM, width for LEFT/RIGHT (`"40%"`, `"360px"`). Anything but a length is refused. |
| `resizable` | `boolean` | The inner edge can be dragged, within `minSize`/`maxSize`. |
| `scope` | `VIEWPORT` · `CONTAINER` | The window (default), or the nearest positioned container — a dialog, or any node with the css class `sui-drawer-host` — over its content and inside its bounds. |
| `mode` | `OVERLAY` · `PUSH` | Over the content (default), or beside it: in `PUSH` it takes room in the flex row (LEFT/RIGHT) or column (TOP/BOTTOM) it stands in, and the content moves over, as it does for a push [sidebar menu](./menu.md). |
| `closable` | `boolean` | An X in the header closes it. Set by `onClose` too. |
| `onClose` | `UiTrigger` | Fired when the X closes it, so the server can clean up. |
| `onStateChange` | `UiTrigger` | Fired on every change the user makes; `{state}` in its URL becomes `OPEN`, `MINIMIZED` or `CLOSED`. |

## Notes

**Minimizing redraws nothing.** Open, minimized and closed are classes on the
one element, switched in the browser. The content is never rendered again, so
what was typed and a widget's own state (a chat's scroll position, a
connection) survive a minimize. That is the difference from sending a new
node.

**A patch keeps what the user chose.** A `REPLACE` of the drawer, or of
anything around it, keeps the drawer's current state unless the new node sets
one. Set `state` in a patch only to change it on purpose, for example
`.minimized()` once a draft has been taken over.

**Keyboard and focus.** The handle is a button, so <kbd>Enter</kbd> or
<kbd>Space</kbd> opens the drawer. Opening puts the focus on the first control
inside, or on the panel. <kbd>Esc</kbd> inside minimizes the drawer rather than
closing it, and the focus returns to the handle. An open menu inside the drawer
takes <kbd>Esc</kbd> first.

**Several at once.** Any number may be open at once, at different edges.
Minimized handles at the same edge of the same container line up side by
side. A handle stays above the open panels.

**Look.** The drawer uses the theme's tokens, so it follows dark mode as well.
The handle is as tall as a button. It slides in and folds away softly, and
not at all with `prefers-reduced-motion`. On a screen narrower than 640px, a
LEFT or RIGHT drawer comes up from the bottom instead.

**No sidepanel to replace.** mc-semantic-ui had no side-panel node. The
closest relative is the [sidebar menu](./menu.md), whose `PUSH` and `OVERLAY`
modes the drawer's `mode` follows. The menu stays what it is: navigation with
its rail state. A panel of your own content beside the page is a drawer with
`edge(RIGHT).mode(PUSH)`.

## See also

- **[`dialog`](./dialog.md)** — a modal layer; a CONTAINER drawer can live inside one.
- **[`menu`](./menu.md)** — the navigation sidebar, push or overlay.
