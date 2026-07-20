---
title: Field
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `field` — one labelled input

**`UiField`** is a single labelled value: a label, an optional hint, an optional
error, and one control whose kind is chosen by `fieldType`. It is the only node
that produces a submittable input, and it does so only when `editable` is
`true` — otherwise it renders the value as plain text.

Fields normally sit in a [`form`](./form.md)'s `fields` list or a
[`detail`](./detail.md), but they are ordinary nodes: drop them anywhere in a
layout tree and, as long as they end up inside a `<form>` element, they are
collected with the rest of the payload.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=field"
  title="Live: UiField"
  loading="lazy"
  style={{width: '100%', height: '440px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — six field types plus a read-only one ("SKU", no `editable`) and one
carrying a `validationError`. Toggling "Active" fires its `onChange` trigger,
which reports the new value without submitting anything.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id. Becomes the wrapper's DOM `id` **and** the control's `name` — so it is the key in the submitted payload. The control itself gets `<id>__input`. |
| `label` | `String` | The `<label>` text. Required in practice. |
| `fieldType` | `FieldType` | Which control to render. See the table below. |
| `value` | `Object` | Current value. Rendered as text when not `editable`. |
| `editable` | `boolean` | Defaults to `false` (read-only span). `true` renders a real input. |
| `required` | `boolean` | Appends a `*` marker to the label. |
| `placeholder` | `String` | Placeholder text. Emitted for `TEXT`, `REFERENCE` and any unrecognised type. |
| `hint` | `String` | Small helper text below the control. |
| `icon` | `String` | Leading in-field icon token. Decorative, and only rendered when `editable`. See [icons](./icon.md). |
| `validationError` | `String` | Per-field error message; also puts the wrapper into its error style. |
| `options` | `List<Option>` | Choices for `SELECT` / `MULTISELECT`. Each `Option` has `value` and `label`. |
| `min` | `String` | Lower bound, verbatim `min` attribute. Numeric and date types. |
| `max` | `String` | Upper bound, verbatim `max` attribute. |
| `step` | `String` | Step granularity, verbatim `step` attribute (e.g. `"0.01"`). |
| `submitOnEnter` | `boolean` | `TEXTAREA` only: Enter submits the surrounding form, Shift+Enter inserts a newline. |
| `submitOnChange` | `boolean` | Any change to the value submits the surrounding form. |
| `onChange` | `UiTrigger` | Trigger fired on `change`, carrying the surrounding form's values as payload. Preferred over `submitOnChange` when only part of the UI should react. |
| `accept` | `String` | `FILE` only: the HTML `accept` filter (`"image/*"`, `".pdf,.docx"`). |
| `multiple` | `boolean` | `FILE` only: allow selecting more than one file. |
| `cssClass` | `String` | Extra CSS class on the field wrapper. |

### Field types

| `fieldType` | Renders as | Extra fields that apply |
|---|---|---|
| `TEXT` | `<input type="text">` | `placeholder`, `icon` |
| `TEXTAREA` | `<textarea rows="4">` | `submitOnEnter` |
| `NUMBER` | `<input type="number">` | `min`, `max`, `step` |
| `CURRENCY` | `<input type="number">` | `min`, `max`, `step` (use `"0.01"`) |
| `PERCENT` | `<input type="number">` | `min`, `max`, `step` |
| `DATE` | `<input type="date">` | `min`, `max`, `step` — `yyyy-MM-dd` |
| `DATETIME` | `<input type="datetime-local">` | `min`, `max`, `step` — `yyyy-MM-ddTHH:mm` |
| `BOOLEAN` | `<input type="checkbox">` | `value` (truthy = checked) |
| `SELECT` | `<select>` | `options` |
| `MULTISELECT` | `<select multiple>` | `options`; `value` may be a list or a comma-separated string |
| `FILE` | `<input type="file">` | `accept`, `multiple` |
| `REFERENCE` | `<input type="text">` | `placeholder`, `icon` — a semantic marker for a foreign-key value; the renderer treats it like `TEXT` |

`icon`, `submitOnChange` and `onChange` work with every type. `CURRENCY` and
`PERCENT` are semantic labels only: the renderer emits the same number input as
`NUMBER`, so format the display value yourself for the read-only case.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The factories pick the fieldType; the fluent setters do the rest.
UiField.text("name", "Name", null).asRequired().asEditable()
       .placeholder("e.g. Widget").hint("Shown in the product list.");

UiField.textarea("desc", "Description", null).asEditable().submitOnEnter();

UiField.number("price", "Price", 19.0).asEditable().min("0").step("0.01");

UiField.date("launch", "Launch date", "2026-07-06").asEditable()
       .range("2026-01-01", "2026-12-31");

UiField.select("category", "Category", "tools", List.of(
           UiField.Option.of("tools", "Tools"),
           UiField.Option.of("toys",  "Toys"))).asEditable();

UiField.multiselect("tags", "Tags", null, List.of(
           UiField.Option.of("new", "New"),
           UiField.Option.of("sale", "Sale"))).asEditable();

UiField.bool("active", "Active", true).asEditable()
       .onChange(UiTrigger.invoke("toggleActive"));

UiField.file("avatar", "Avatar").accept("image/*").multiple()
       .onChange(UiTrigger.upload("/api/avatar"));

UiField.reference("owner", "Owner", "u-42").asEditable();

// Read-only, and rejected-submit rendering:
UiField.text("sku", "SKU", "WGT-0042");                       // editable = false
UiField.text("name", "Name", "").asEditable().error("Name is required.");
UiField.text("q", "Search", null).asEditable().editableIf(canEdit);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "field", "id": "name", "label": "Name", "fieldType": "TEXT",
  "editable": true, "required": true, "placeholder": "e.g. Widget",
  "hint": "Shown in the product list." }

{ "type": "field", "id": "price", "label": "Price", "fieldType": "NUMBER",
  "editable": true, "value": 19.0, "min": "0", "step": "0.01" }

{ "type": "field", "id": "category", "label": "Category", "fieldType": "SELECT",
  "editable": true, "value": "tools",
  "options": [ { "value": "tools", "label": "Tools" },
               { "value": "toys",  "label": "Toys" } ] }

{ "type": "field", "id": "active", "label": "Active", "fieldType": "BOOLEAN",
  "editable": true, "value": true,
  "onChange": { "behavior": "INVOKE", "handler": "toggleActive" } }

{ "type": "field", "id": "avatar", "label": "Avatar", "fieldType": "FILE",
  "editable": true, "accept": "image/*", "multiple": true,
  "onChange": { "behavior": "UPLOAD", "method": "POST", "url": "/api/avatar" } }

{ "type": "field", "id": "desc", "label": "Description", "fieldType": "TEXTAREA",
  "editable": true, "validationError": "Add at least a short description." }
```

</TabItem>
</Tabs>

## Notes

**`id` is the payload key.** The `name` attribute keeps the model id verbatim,
so `{"name": "Widget", "price": "19.0"}` falls out of a form whose fields are
`name` and `price`. The DOM id of the control is suffixed with `__input` so the
wrapper can keep the canonical id for patch targeting — patch the *field* id,
not the input id.

**`editable` defaults to `false`.** This trips people up: a field built with
`UiField.text(...)` and dropped into a form renders as static text and never
reaches the server. Call `.asEditable()` (or `.editableIf(condition)`) on every
input you actually want filled in. `UiField.file(...)` is the one factory that
sets it for you.

**`required` is a marker, not validation.** It adds the asterisk and nothing
else — no `required` attribute, no client-side check. Validate on the server and
send the message back in `validationError`, with a summary in the form's
`formError`.

**`onChange` beats `submitOnChange` for partial updates.** `submitOnChange`
posts the entire form; `onChange` dispatches its own trigger with the form's
values folded in as payload, so one field can drive a `PATCH` (no round-trip) or
an `INVOKE` handler. Both markers can be present — the bus prefers the trigger.

**File fields versus the drop zone.** A `FILE` field is a single inline picker;
pair it with `UiTrigger.upload(url)` on `onChange` to send the files as
`multipart/form-data`. When you want a drop target with a prompt and a hint,
reach for [`upload`](./upload.md) instead.

## See also

- **[`form`](./form.md)** — the container that submits these fields.
- **[`fieldgroup`](./fieldgroup.md)** — a titled `<fieldset>` around a set of fields.
- **[`upload`](./upload.md)** — drag-and-drop alternative to a `FILE` field.
- **[Forms](../forms.md)** — layout, validation and round-trip patterns.
- **[Triggers & actions](../triggers.md)** — what an `onChange` can do.
