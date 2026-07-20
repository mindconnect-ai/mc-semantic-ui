---
title: Dialog
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `dialog` — modal overlay

**`UiDialog`** is a modal overlay whose body is *any node* — a form, a table, a
detail view, a stack of text. It is a real `UiNode` (`type: "dialog"`), so the
visual editor can select and edit it and a [patch](../triggers.md) can open or
close it like any other part of the tree.

The node renders itself as a fixed-position `.sui-dialog-host`, so its place in
the tree does not affect where it appears on screen. In practice it lives in a
body-level host, `#sui-dialogs`, that the event bus creates on start-up.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=dialog"
  title="Live: UiDialog"
  loading="lazy"
  style={{width: '100%', height: '200px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — open the dialog, then close it three ways: the ×, a click on the
backdrop, or the "Delete" button inside it (which removes it by id and raises a
toast).*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id. Also the DOM id of the overlay — a `REMOVE` on it closes the dialog. |
| `title` | `String` | Heading rendered in the dialog header. Also becomes the `aria-label` of the `role="dialog"` box. |
| `node` | `UiNode` | The dialog's body. Anything that renders as a node. |
| `closeHref` | `String` | URL the close control navigates to. When set, the × is a real `<a href>` (works with no JavaScript); `null` renders a `<button>` and the SPA just removes the overlay in place. |
| `cssClass` | `String` | Extra CSS class on the overlay element. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The body is a normal tree — here an edit form.
var dialog = UiDialog.of("Edit product", "/products",
    UiForm.of("edit", null)
        .field(UiField.text("name", "Name", product.getName()))
        .action(UiAction.primary("save", "Save")
                .onClick(UiTrigger.api("POST", "/products/" + product.getId(), "edit"))));
dialog.setId("edit-42");

// 1. Open it with the page it belongs to:
return UiPage.of("/products", productTable()).dialog(dialog);

// 2. Or open it without re-rendering the page underneath:
return UiPatch.of().patch(UiPatch.Operation.append("sui-dialogs", dialog));

// Close it from your own button — REMOVE by id:
UiAction.primary("confirm", "Delete")
        .onClick(UiTrigger.patch(UiPatch.of()
                .patch(UiPatch.Operation.remove("edit-42"))
                .toast(UiToast.success("Product deleted"))));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "dialog", "id": "edit-42", "title": "Edit product",
  "closeHref": "/products",
  "node": { "type": "form", "id": "edit", "fields": [], "actions": [] } }
```

Opening one with a patch — an `APPEND` into the dialog host:

```json
{ "patches": [
  { "op": "APPEND", "targetId": "sui-dialogs",
    "node": { "type": "dialog", "id": "confirm", "title": "Delete product?",
              "node": { "type": "text", "id": "t", "text": "This cannot be undone." } } }
] }
```

Closing it from inside, with feedback:

```json
{ "patches": [ { "op": "REMOVE", "targetId": "confirm" } ],
  "toasts":  [ { "level": "SUCCESS", "message": "Deleted" } ] }
```

</TabItem>
</Tabs>

## Notes

**Two ways in, and they behave differently.** Putting a dialog in
`UiPage.dialogs` ties it to the page: every full page render *replaces* the
host's contents, so the dialog appears with the page and is gone once you
navigate away. `APPEND`-ing it to `sui-dialogs` from a patch opens it *without*
touching the page underneath — the URL, scroll position and form state all
survive. Prefer the patch for "open this over what the user is already looking
at", and the page for a URL-addressable overlay (`/products/42/edit`).

**Closing needs no code.** The × and the backdrop carry a
`data-sui-dialog-close` marker; the bus intercepts the click and removes the
overlay in place. You only write a patch when *your own* control closes it —
typically `REMOVE` by id plus a toast after a successful save.

**For a plain "are you sure?", don't build a dialog.** Put
[`confirm`](./action.md) on the action instead: the bus asks before dispatching
the trigger, and you write no node, no handler and no close path. Reach for
`UiDialog` when the overlay needs *content*.

**Dialogs stack.** Each one is its own `.sui-dialog-host` carrying its own id
inside the shared host, so several can be open at once and a `REMOVE` closes
exactly one. Give every dialog a stable `id` — without one, nothing can address
it.

**`closeHref` is the no-JS path.** With SSR and no JavaScript, the × has to go
*somewhere*; `closeHref` is that destination, and the dialog is simply absent
from the page you land on. Leave it `null` for SPA-only dialogs.

## See also

- **[`toast`](./toast.md)** — the other thing that appears over the page.
- **[`action`](./action.md)** — `confirm`, the cheaper alternative.
- **[Triggers & actions](../triggers.md)** — patch operations in full.
- **[Triggers cookbook](../triggers-cookbook.md)** — working open/close recipes.
