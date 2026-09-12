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
| `trailing` | `UiAction` | Action rendered on the control's row, right of the input — a Browse… next to a path field, an Encrypt next to a key field. Editable fields only. |
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
| `expanded` | `boolean` | `SELECT` / `MULTISELECT` only: show every option at once — radio buttons for `SELECT`, checkboxes for `MULTISELECT`. Set with `.asRadio()` / `.asCheckboxes()`. The submitted value keeps its shape. |
| `orderable` | `boolean` | Expanded `MULTISELECT` only: checked options lead the list with move-up/down buttons, and the list is submitted in the order shown. Set with `.orderable()`, which implies `.asCheckboxes()`. The buttons need the SPA EventBus or JavaFX. |
| `cssClass` | `String` | Extra CSS class on the field wrapper. |

### Field types

| `fieldType` | Renders as | Extra fields that apply |
|---|---|---|
| `TEXT` | `<input type="text">` | `placeholder`, `icon` |
| `TEXTAREA` | `<textarea rows="4">` | `submitOnEnter` |
| `PASSWORD` | `<input type="password">` + eye toggle | `placeholder` — the built-in toggle flips the input to plain text and back; the value never leaves the field |
| `NUMBER` | `<input type="number">` | `min`, `max`, `step` |
| `CURRENCY` | `<input type="number">` | `min`, `max`, `step` (use `"0.01"`) |
| `PERCENT` | `<input type="number">` | `min`, `max`, `step` |
| `DATE` | `<input type="date">` | `min`, `max`, `step` — `yyyy-MM-dd` |
| `DATETIME` | `<input type="datetime-local">` | `min`, `max`, `step` — `yyyy-MM-ddTHH:mm` |
| `BOOLEAN` | `<input type="checkbox">` | `value` (truthy = checked) |
| `SELECT` | `<select>`, or radio buttons when `expanded` | `options` |
| `MULTISELECT` | `<select multiple>`, or checkboxes when `expanded` | `options`, `orderable`; `value` may be a list or a comma-separated string |
| `FILE` | `<input type="file">` | `accept`, `multiple` |
| `REFERENCE` | `<input type="text">` | `placeholder`, `icon` — a semantic marker for a foreign-key value; the renderer treats it like `TEXT` |
| `HIDDEN` | `<input type="hidden">` — no wrapper, no label | `value` only — submitted whether or not the field is `editable` |

`PASSWORD` builds the reveal toggle in: the eye button (wired by the event
bus) switches the input between `password` and `text`, so an admin can check
what is in the field before saving. Factory: `UiField.password(id, label,
value)`.

`HIDDEN` carries a value the user never sees — a record id, a version for
optimistic locking, the context the server needs back on submit. Nothing is
drawn: the input stands alone with the field id as both DOM `id` and `name`,
and a [`detail`](./detail.md) leaves it out. Factory: `UiField.hidden(id,
value)`. This is a different thing from `.hidden()` on any node, which only
takes a visible field out of the layout for a while.

### Radio buttons and checkboxes

A choice with few options often reads better with every option on screen.
`.asRadio()` turns a `SELECT` into a group of radio buttons, `.asCheckboxes()`
a `MULTISELECT` into a group of checkboxes. The value keeps its shape — one
string, or a list of strings — so a handler that reads a dropdown reads the
group the same way.

One difference to plan for: a radio group can have nothing chosen. When
`value` is null or matches no option, no radio is checked and the field
submits `null` (a native form post sends no key at all). A dropdown in the
browser never gets there, because the browser selects its first option when
none is marked. Set
a `value` if the server needs one, or treat `null` as "not chosen". Likewise a
checkbox group with nothing checked submits `[]` over the SPA, and no key in a
native form post.

Which options start checked or selected follows one rule on every renderer,
for dropdowns and groups alike: for a `MULTISELECT` the option's value is in
`value` (a list or array, or a comma-separated string with each part trimmed —
`"a,"` is `a` and the empty value; blank means none), for a `SELECT` it equals
`value` as text. Values are compared as JavaScript prints them, so `0` selects
`"0"`, `2.0` selects `"2"`, and `null` selects nothing — not even an option
whose value is `""`. A `null` entry in `options` is skipped; an option without
a value is drawn and submits `""`.

`.orderable()` goes one step further for a list whose order matters — a
preference ranking, fallback models tried top to bottom. The checked options
come first, in the order of `value`, the rest follow in option order; each
checked row carries move-up and move-down buttons, and the list comes back in
the order shown. Checking an option appends it to the end of the checked ones;
unchecking returns it to its place among the unchecked ones — exactly where a
re-render from the server puts it, so a form that answers each change with a
fresh render does not shuffle rows under the user's cursor. Reordering counts
as a change: `onChange` and `submitOnChange` fire once the move clicks pause,
not once per step, and not at all when a tick or a submit reports the order
first. The move buttons need the SPA EventBus (or the JavaFX client) and show
only inside the part of the page a bus drives; on a page rendered without one
they are not shown, and the order is the one the server rendered.

`submitOnChange` and `onChange` work with every type but `HIDDEN`; `icon` with
every single-line control — not with `HIDDEN` or a radio / checkbox group. `CURRENCY` and
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

// Every option on screen: radio buttons, checkboxes, a ranked list.
UiField.select("size", "Size", "m", sizes).asEditable().asRadio();
UiField.multiselect("tags", "Tags", List.of("new"), tags).asEditable().asCheckboxes();
UiField.multiselect("fallbacks", "Fallback models", List.of("sonnet", "opus"), models)
       .asEditable().orderable();

UiField.bool("active", "Active", true).asEditable()
       .onChange(UiTrigger.invoke("toggleActive"));

UiField.file("avatar", "Avatar").accept("image/*").multiple()
       .onChange(UiTrigger.upload("/api/avatar"));

UiField.reference("owner", "Owner", "u-42").asEditable();

// Submitted with the form, never shown:
UiField.hidden("orderId", 4711);

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

{ "type": "field", "id": "size", "label": "Size", "fieldType": "SELECT",
  "editable": true, "expanded": true, "value": "m",
  "options": [ { "value": "s", "label": "Small" }, { "value": "m", "label": "Medium" } ] }

{ "type": "field", "id": "fallbacks", "label": "Fallback models", "fieldType": "MULTISELECT",
  "editable": true, "expanded": true, "orderable": true, "value": ["sonnet", "opus"],
  "options": [ { "value": "opus", "label": "Claude Opus" },
               { "value": "sonnet", "label": "Claude Sonnet" },
               { "value": "haiku", "label": "Claude Haiku" } ] }

{ "type": "field", "id": "orderId", "fieldType": "HIDDEN", "value": 4711 }

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
sets it for you, and a `HIDDEN` field ignores the flag — it always submits.

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
