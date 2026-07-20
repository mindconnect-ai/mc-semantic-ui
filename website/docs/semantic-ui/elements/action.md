---
title: Action
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `action` — buttons and links

**`UiAction`** is anything clickable: a button, a link, an icon-only control. It
answers two independent questions — *what it is in the DOM* (`appearance`) and
*what colour it wears* (`style`) — and carries the click behaviour in a single
[`onClick` trigger](../triggers.md).

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=action"
  title="Live: UiAction"
  loading="lazy"
  style={{width: '100%', height: '210px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — every button here is one `UiAction`. "Delete" asks for confirmation
first; "Publish" is disabled with a reason; "Importing…" is busy by model state.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `label` | `String` | Button text. For `appearance: ICON` it becomes the accessible name. |
| `style` | `PRIMARY` · `SECONDARY` · `DANGER` | Colour scheme. Ignored by `LINK`. |
| `appearance` | `BUTTON` · `LINK` · `ICON` | DOM shape. Defaults to `BUTTON` when absent. |
| `icon` | `String` | Icon token rendered before the label — or *as* the control for `ICON`. See [icons](./icon.md). |
| `enabled` | `boolean` | Defaults to `true`. `false` renders disabled. |
| `disabledReason` | `String` | Tooltip explaining *why* it's disabled. |
| `confirm` | `String` | Confirmation text. The bus asks before firing `onClick`. |
| `loading` | `boolean` | Declarative busy state: spinner + disabled. |
| `onClick` | `UiTrigger` | What happens on click. `null` = inert. |
| `cssClass` | `String` | Extra CSS class on the element. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The factories set style + appearance together:
UiAction.primary("save", "Save")
        .onClick(UiTrigger.api("POST", "/products", "product-form"));

UiAction.secondary("cancel", "Cancel")
        .onClick(UiTrigger.go("/products"));

UiAction.danger("delete", "Delete")
        .confirm("Delete this product?")
        .onClick(UiTrigger.api("DELETE", "/products/42"));

// A link, an icon button, a disabled button:
UiAction.link("details", "View details").onClick(UiTrigger.go("/products/42"));
UiAction.secondary("edit", "Edit").icon("edit").appearance(UiAction.Appearance.ICON);
UiAction.primary("publish", "Publish").disabled("Fill in the required fields first");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST",
               "url": "/products", "payload": "product-form" } }

{ "type": "action", "id": "delete", "label": "Delete", "style": "DANGER",
  "confirm": "Delete this product?",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/products/42" } }

{ "type": "action", "id": "edit", "label": "Edit", "icon": "edit",
  "appearance": "ICON", "style": "SECONDARY",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products/42/edit" } }
```

</TabItem>
</Tabs>

## Notes

**`confirm` costs nothing.** The event bus intercepts the click and asks before
dispatching — no dialog node, no handler, no state. Reach for
[`dialog`](./dialog.md) only when the overlay needs *content*.

**`enabled: false` deserves a reason.** `disabledReason` becomes the tooltip; a
disabled control with no explanation is a dead end for the user.

**`loading` vs. automatic busy state.** The bus already marks the clicked element
busy for the duration of its own request — leave `loading` alone for that. Set it
explicitly only when the *server* owns the state and pushes `loading: true` in a
patch.

**Actions live in containers.** [`form`](./form.md), [`table`](./table.md)
(`actions` and per-row `rowActions`), [`detail`](./detail.md) and
[`list`](./list.md) items all take a list of actions — that's where most of them
belong, rather than loose in a stack.

## See also

- **[Triggers & actions](../triggers.md)** — every behaviour an `onClick` can have.
- **[Triggers cookbook](../triggers-cookbook.md)** — working recipes.
- **[`link`](./link.md)** — plain navigation without button semantics.
- **[`menu-button`](./menu-button.md)** — several actions behind one control.
