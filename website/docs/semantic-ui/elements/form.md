---
title: Form
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `form` — inputs, actions and one submitted object

**`UiForm`** is the editable counterpart to [`detail`](./detail.md): a `<form>`
element holding [`field`](./field.md) nodes, a footer of
[`action`](./action.md)s and optional [`link`](./link.md)s. It is the node a
[trigger's `payload`](../triggers.md) points at — name a form id there and the
client collects every named control inside it and sends them as one JSON object.

A form has two bodies. `fields` is the plain vertical list, which covers most
screens. `content` takes arbitrary nodes — a [`stack`](./stack.md) for columns,
a [`section`](./section.md) for tabs, [`fieldgroup`](./fieldgroup.md)s — for
when the layout matters. Both submit together, because the payload is collected
from the DOM, not from the `fields` list.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=form"
  title="Live: UiForm"
  loading="lazy"
  style={{width: '100%', height: '660px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — press "Save" to see the form-level banner plus a per-field error on
every input; "Cancel" clears them again. Neither button calls a server: both
run a client handler that returns a patch.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id, the DOM `id` of the `<form>` — and the value you put in a trigger's `payload`. |
| `title` | `String` | Rendered as an `<h2>` above the fields. Omit for an untitled form. |
| `fields` | `List<UiField>` | The flat, vertical list of inputs. Empty by default. |
| `content` | `List<UiNode>` | Rich body rendered *after* `fields` — any node tree, for columns, tabs or groups. |
| `actions` | `List<UiAction>` | Footer buttons, and menus of them (`UiAction.menu(…)`, [below](#a-menu-in-the-button-bar)), in the order added. The first `PRIMARY` button (else the first button, never a menu) supplies the native `method`/`action` fallback. |
| `actionsOverflow` | `WRAP` · `MENU` | What the button bar does when its buttons do not fit one row. `WRAP` (default) takes another line; `MENU` keeps one row and folds the buttons that do not fit, from the end, into a trailing "⋯" menu — see [responsive](../responsive.md#button-bars-wrap-or-a--more-menu). |
| `links` | `List<UiLink>` | Footer links, rendered next to the actions. |
| `formError` | `String` | Form-level error banner above the fields, `role="alert"`. For cross-field or save failures. |
| `reloadOnSubmit` | `boolean` | `true` makes submit a native full-page navigation instead of an event-bus fetch. |
| `cssClass` | `String` | Extra CSS class on the `<form>`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("product-form", "New product")
      .field(UiField.text("name", "Name", null).asRequired().asEditable()
                    .placeholder("e.g. Widget"))
      .field(UiField.number("price", "Price", 19.0).asEditable().step("0.01"))
      .field(UiField.select("category", "Category", "tools", List.of(
                 UiField.Option.of("tools", "Tools"),
                 UiField.Option.of("toys",  "Toys"))).asEditable())
      .field(UiField.bool("active", "Active", true).asEditable())
      // The payload id is this form's id — the client collects it on submit.
      .action(UiAction.primary("save", "Save")
                      .onClick(UiTrigger.api("POST", "/products", "product-form")))
      .action(UiAction.secondary("cancel", "Cancel").onClick(UiTrigger.go("/products")))
      .link(UiLink.of("ref", "/help/products", "Need help?"));

// Redisplaying a rejected submit:
form.error("Please fix the errors below.");

// A two-column body instead of the flat list:
UiForm.of("customer-form", "Customer")
      .content(UiFieldGroup.of("contact", "Contact")
                           .field(UiField.text("email", "E-mail", null).asEditable()))
      .content(UiStack.of(left, right).direction(UiStack.Direction.HORIZONTAL).gap(16));

// Theme switch and other changes that live outside #sui-root:
UiForm.of("theme-form", null).reloadOnSubmit();
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "product-form", "title": "New product",
  "formError": "Please fix the errors below.",
  "fields": [
    { "type": "field", "id": "name", "label": "Name", "fieldType": "TEXT",
      "editable": true, "required": true, "placeholder": "e.g. Widget",
      "validationError": "Name is required." },
    { "type": "field", "id": "price", "label": "Price", "fieldType": "NUMBER",
      "editable": true, "value": 19.0, "step": "0.01" },
    { "type": "field", "id": "active", "label": "Active", "fieldType": "BOOLEAN",
      "editable": true, "value": true }
  ],
  "actions": [
    { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST",
                   "url": "/products", "payload": "product-form" } },
    { "type": "action", "id": "cancel", "label": "Cancel", "style": "SECONDARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/products" } }
  ],
  "links": [ { "type": "link", "id": "help", "rel": "ref",
               "href": "/help/products", "label": "Need help?" } ]
}
```

</TabItem>
</Tabs>

## Notes

**The payload is the form id, not the field list.** A trigger with
`payload: "product-form"` makes the client walk *every* named control inside
that `<form>` element. Fields nested in `content`, inside a
[`fieldgroup`](./fieldgroup.md), or on a hidden tab all ride along — layout
never changes the submitted shape.

**Two levels of error.** `formError` is the banner for cross-field and
save-level problems; per-input messages belong on
[`UiField.validationError`](./field.md). The usual server flow is: reject the
submit, set both, return the same form as the response — the renderer swaps it
in place.

**Only editable fields are inputs.** `UiField.editable` defaults to `false`,
which renders a plain value span with no `name` — so it is not submitted. A
form of non-editable fields is a read-only card; use [`detail`](./detail.md)
when that is the intent.

**`reloadOnSubmit` is an escape hatch.** Normally the event bus intercepts the
submit, fetches JSON and patches the page. Set this flag only when the effect
lives outside the mounted `#sui-root` subtree — swapping the stylesheet in
`<head>`, or replacing the SPA bootstrap with an SSR response.

**Actions double as the no-JS fallback.** The renderer copies `method` and
`action` onto the `<form>` from the primary action's trigger, tunnelling
`PUT`/`DELETE` through a hidden `_method` input. A form without actions has no
native submit target.

## A menu in the button bar

Several related actions — a mail composer's AI tools — fit behind one button,
so the bar stays short: `Send · Attach · ✨ AI ▾ · Check`.

```java
UiForm.of("compose", "New mail")
    .field(UiField.text("subject", "Subject", null).asEditable())
    .field(UiField.richtext("body", "Message", null).asEditable())
    .action(UiAction.primary("send", "Send").onClick(UiTrigger.api("POST", "/mail/send", "compose")))
    .action(UiAction.secondary("attach", "Attach").icon("paperclip").onClick(/* … */))
    .action(UiAction.menu("ai", "AI",
                UiMenuItem.of("draft", "Draft with AI").icon("wand-sparkles")
                        .onClick(UiTrigger.api("POST", "/ai/draft", "compose")),
                UiMenuItem.divider(),
                UiMenuItem.heading("Quick actions"),
                UiMenuItem.of("shorten", "Shorten").icon("scissors")
                        .onClick(UiTrigger.api("POST", "/ai/shorten")),
                UiMenuItem.of("translate", "Translate").disabled("No translation service"))
            .icon("sparkles"))
    .action(UiAction.secondary("check", "Check").onClick(/* … */));
```

- **It looks like its neighbours.** `UiAction.menu` is a `UiActionMenu`, an
  action with `items`, so it has the button's `style` (secondary by default),
  `icon`, `label` and `enabled`/`disabledReason`, plus a small caret.
- **Its entries send the form**, like any button of the form. They send it
  when the trigger names the form as `payload`, and also, inside the button
  bar, when it names nothing. The fields are collected through the form's id,
  not from where the menu happens to be: the open popover is fixed to the
  window.
- **It is never the submit.** The form's native `method`/`action` come from a
  button, never from a menu. A form whose only action is a menu has no native
  submit.
- **It stays in the window.** It opens above the button when there is no room
  below it, such as a button bar fixed to the bottom of a dialog. It is kept
  inside the window sideways. When neither side has room, it takes the larger
  side and scrolls.
- **Keyboard.** <kbd>Enter</kbd>/<kbd>Space</kbd> open the menu on its first
  entry, and <kbd>↓</kbd>/<kbd>↑</kbd> on the button open it on the first or
  last entry. Inside, the arrows move and wrap, skipping headings, dividers
  and disabled entries; <kbd>Home</kbd>/<kbd>End</kbd> jump to the ends.
  <kbd>→</kbd>/<kbd>←</kbd> enter and leave a submenu. <kbd>Esc</kbd> closes
  the menu and returns to the button, <kbd>Tab</kbd> closes it and moves on,
  and choosing an entry closes it.
- **Where else.** A menu can go wherever an action goes (`UiDetail`,
  `UiList`, a table's header actions), and it renders the same way there. The
  entries work only with the event bus: without JS the menu still opens, but
  its buttons do nothing.

## See also

- **[Forms](../forms.md)** — the conceptual guide: layout, validation round-trips, patterns.
- **[`field`](./field.md)** — the inputs themselves and every `FieldType`.
- **[`fieldgroup`](./fieldgroup.md)** — titled `<fieldset>` grouping inside `content`.
- **[`upload`](./upload.md)** — drag-and-drop file intake.
- **[`action`](./action.md)** — the footer buttons.
- **[Triggers & actions](../triggers.md)** — how `payload` and the behaviours work.
