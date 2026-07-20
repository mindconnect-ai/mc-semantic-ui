---
title: Field group
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `fieldgroup` — a titled set of related fields

**`UiFieldGroup`** wraps a handful of nodes in a native
`<fieldset><legend>…</legend>`, so the group heading is announced together with
every field inside it. That is the whole point: it buys real grouping semantics
for less ceremony than a styled [`stack`](./stack.md) with a heading node.

It holds any nodes, not just [`field`](./field.md)s — a nested stack for columns
works fine. Drop it into a [`form`](./form.md)'s `content` list (groups are not
valid in `fields`, which is typed to `UiField`).

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=fieldgroup"
  title="Live: UiFieldGroup"
  loading="lazy"
  style={{width: '100%', height: '640px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — two groups, one of them containing a two-column stack. Press "Save":
the toast lists the collected payload keys, showing that the grouping has no
effect on the submitted object.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id and the `<fieldset>`'s DOM id. Omitted from the DOM when absent. |
| `title` | `String` | The `<legend>`. Omit it and no legend is rendered. |
| `hint` | `String` | Small helper text under the legend, above the content. |
| `content` | `List<UiNode>` | The grouped nodes — fields, or any layout node. Empty by default. |
| `cssClass` | `String` | Extra CSS class on the `<fieldset>`. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("customer-form", "Customer")
      .content(UiFieldGroup.of("contact", "Contact")
                   .hint("How we reach this customer.")
                   .field(UiField.text("email", "E-mail", null).asRequired().asEditable())
                   .field(UiField.text("phone", "Phone", null).asEditable()))
      .content(UiFieldGroup.of("address", "Shipping address")
                   // any node works, not just fields
                   .content(UiStack.of(
                        UiField.text("zip",  "ZIP",  "10115").asEditable(),
                        UiField.text("city", "City", "Berlin").asEditable())
                       .direction(UiStack.Direction.HORIZONTAL).gap(12))
                   .field(UiField.select("country", "Country", "de", List.of(
                        UiField.Option.of("de", "Germany"),
                        UiField.Option.of("at", "Austria"))).asEditable()))
      .action(UiAction.primary("save", "Save")
                  .onClick(UiTrigger.api("POST", "/customers", "customer-form")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "customer-form", "title": "Customer",
  "content": [
    { "type": "fieldgroup", "id": "contact", "title": "Contact",
      "hint": "How we reach this customer.",
      "content": [
        { "type": "field", "id": "email", "label": "E-mail", "fieldType": "TEXT",
          "editable": true, "required": true },
        { "type": "field", "id": "phone", "label": "Phone", "fieldType": "TEXT",
          "editable": true }
      ] },
    { "type": "fieldgroup", "id": "address", "title": "Shipping address",
      "content": [
        { "type": "stack", "id": "addr-row", "direction": "HORIZONTAL", "gap": 12,
          "children": [
            { "type": "field", "id": "zip",  "label": "ZIP",  "fieldType": "TEXT",
              "editable": true, "value": "10115" },
            { "type": "field", "id": "city", "label": "City", "fieldType": "TEXT",
              "editable": true, "value": "Berlin" }
          ] }
      ] }
  ]
}
```

</TabItem>
</Tabs>

## Notes

**Transparent to submission.** A group changes the DOM structure, never the
payload. Fields keep their own `name`, and the client collects every named
control in the surrounding `<form>` — so `{"email": …, "zip": …, "city": …}`
comes out flat, whichever group each field sat in.

**Use `content`, not `fields`, on the form.** `UiForm.fields` is a
`List<UiField>` and cannot hold a group. `UiForm.content(node)` takes any node
and is rendered after the flat fields, so a form can mix a short flat list with
grouped sections below.

**A group is not a section.** `fieldgroup` is a labelled box that is always
visible; [`section`](./section.md) is what you want for collapsible or tabbed
bodies. They nest happily — a section entry holding a group is a common shape
for long settings pages.

**Nothing is patch-special about groups.** Patch the *field* ids to update
individual inputs; replace the group id only when the whole set changes shape
(different fields, different legend).

## See also

- **[`form`](./form.md)** — where groups live.
- **[`field`](./field.md)** — the inputs inside.
- **[`stack`](./stack.md)** — plain layout without the `<fieldset>` semantics.
- **[Forms](../forms.md)** — layout and validation patterns end to end.
- **[Triggers & actions](../triggers.md)** — submitting the collected payload.
