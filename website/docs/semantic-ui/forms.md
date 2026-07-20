---
title: Forms & validation
sidebar_position: 5
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Forms &amp; validation

A `UiForm` renders a real `<form>` with fields, a rich body, an action footer,
and optional link footer. It submits through a [trigger](./triggers.md) — and
the payload is collected by walking **every named control inside the `<form>`
element**, so however you lay the fields out (columns, groups, tabs), the whole
form still travels as one object.

:::info One tree, three ways
The trees below are shown as the **Java** builder, the **JSON** wire form, and
the equivalent **JavaScript** object literal (for a backend-free client). The
tab choice syncs across the page.
:::

**On the client**, you build the same trees as plain objects and hand them to
the renderer — the `SuiEventBus` then wires clicks, submits and field changes to
their triggers:

```js
import { createDefaultRenderer } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const root = document.getElementById("app");
const renderer = createDefaultRenderer().attach(root);
const bus = new SuiEventBus(renderer, root);

renderer.mount(productForm);   // productForm = the JS object in the tab below
// For INVOKE / client-side submits, register handlers by name:
bus.registerClientHandler("products.save", (ctx) => { /* ctx.payload = form values */ });
```

That setup is written once; the `renderer.mount(...)` argument is any of the JS
trees below. See [Building an app](./building-an-app.md) for the full page shell.

## A basic form

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("product-form", "Edit product")
    .field(UiField.text("sku",  "SKU",   p.getSku()).asEditable().asRequired())
    .field(UiField.text("name", "Name",  p.getName()).asEditable())
    .field(UiField.number("price", "Price", p.getPrice()).min("0").step("0.01"))
    .field(UiField.select("status", "Status", p.getStatus(), List.of(
            UiField.Option.of("active",   "Active"),
            UiField.Option.of("inactive", "Inactive"))).asEditable())
    .action(UiAction.primary("save", "Save").dispatch("POST", "/api/products", "product-form"))
    .link(UiLink.of("back", "/admin/products", "← Back"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "product-form", "title": "Edit product",
  "fields": [
    { "type": "field", "id": "sku",   "label": "SKU",   "fieldType": "TEXT",   "value": "CH-01", "editable": true, "required": true },
    { "type": "field", "id": "name",  "label": "Name",  "fieldType": "TEXT",   "value": "Chair", "editable": true },
    { "type": "field", "id": "price", "label": "Price", "fieldType": "NUMBER", "value": 249, "min": "0", "step": "0.01" },
    { "type": "field", "id": "status", "label": "Status", "fieldType": "SELECT", "value": "active", "editable": true,
      "options": [ { "value": "active", "label": "Active" }, { "value": "inactive", "label": "Inactive" } ] }
  ],
  "actions": [
    { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/api/products", "payload": "product-form" } }
  ],
  "links": [ { "type": "link", "id": "back", "href": "/admin/products", "label": "← Back" } ]
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
const productForm = {
  type: "form", id: "product-form", title: "Edit product",
  fields: [
    { type: "field", id: "sku",   label: "SKU",   fieldType: "TEXT",   value: "CH-01", editable: true, required: true },
    { type: "field", id: "name",  label: "Name",  fieldType: "TEXT",   value: "Chair", editable: true },
    { type: "field", id: "price", label: "Price", fieldType: "NUMBER", value: 249, min: "0", step: "0.01" },
    { type: "field", id: "status", label: "Status", fieldType: "SELECT", value: "active", editable: true,
      options: [ { value: "active", label: "Active" }, { value: "inactive", label: "Inactive" } ] },
  ],
  actions: [
    { type: "action", id: "save", label: "Save", style: "PRIMARY",
      onClick: { behavior: "APPLY_RESPONSE", method: "POST", url: "/api/products", payload: "product-form" } },
  ],
  links: [ { type: "link", id: "back", href: "/admin/products", label: "← Back" } ],
};

renderer.mount(productForm);   // the SuiEventBus dispatches Save on submit
```

</TabItem>
</Tabs>

### How submission works

The submit action carries a trigger whose `payload` names the form
(`.dispatch(method, url, "product-form")`). On submit the client:

1. finds the element with that id (the `<form>`),
2. reads every named `<input>` / `<select>` / `<textarea>` under it into a JSON
   object keyed by field `id`,
3. sends it — folded into the **query string** for `GET`, or as a **JSON body**
   for `POST` / `PUT` / `DELETE`.

Field types survive the trip: a `data-sui-type` attribute keeps `NUMBER`,
`CURRENCY`, `BOOLEAN`, … distinct, so a checkbox arrives as a boolean and a
number as a number.

If the submit action omits the payload id, the bus infers it from the
surrounding form — so a bare `.dispatch("GET", "/search")` still picks up the
form's values.

## Instant fields

A field can act on its own without a submit button:

- `submitOnEnter()` — on a `TEXTAREA`, Enter submits (Shift+Enter = newline).
- `submitOnChange()` — any value change re-submits the whole form (e.g. a
  theme/status picker).
- `onChange(trigger)` — fire one targeted [trigger](./triggers.md#field-onchange-triggers)
  on change, e.g. a checkbox that enables another field. Prefer this over
  `submitOnChange` when you don't want a full form round-trip.

## Structuring a form

`UiForm.fields` is the flat, vertical case. For anything richer, drop layout
nodes into **`UiForm.content`** — it holds any `UiNode` and renders inside the
`<form>` after the flat fields. Put the actual inputs as standalone `UiField`s
inside the layout. Because submission collects by walking the DOM, the layout
never affects the payload.

### Columns

A horizontal `UiStack` with the `sui-cols` helper class becomes equal-width,
top-aligned columns:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("profile", "Profile")
    .content(UiStack.of("cols").direction(UiStack.Direction.HORIZONTAL)
            .child(UiStack.of("c1")
                    .child(UiField.text("first", "First name", "").asEditable())
                    .child(UiField.text("email", "Email", "").asEditable()))
            .child(UiStack.of("c2")
                    .child(UiField.text("last",  "Last name", "").asEditable())
                    .child(UiField.text("phone", "Phone", "").asEditable()))
            .withCssClass("sui-cols"))
    .action(UiAction.primary("save", "Save").dispatch("POST", "/profile", "profile"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "profile", "title": "Profile", "fields": [],
  "content": [
    { "type": "stack", "id": "cols", "direction": "HORIZONTAL", "cssClass": "sui-cols", "children": [
      { "type": "stack", "id": "c1", "children": [
        { "type": "field", "id": "first", "label": "First name", "fieldType": "TEXT", "value": "", "editable": true },
        { "type": "field", "id": "email", "label": "Email",      "fieldType": "TEXT", "value": "", "editable": true }
      ] },
      { "type": "stack", "id": "c2", "children": [
        { "type": "field", "id": "last",  "label": "Last name", "fieldType": "TEXT", "value": "", "editable": true },
        { "type": "field", "id": "phone", "label": "Phone",     "fieldType": "TEXT", "value": "", "editable": true }
      ] }
    ] }
  ],
  "actions": [
    { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/profile", "payload": "profile" } }
  ]
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "form", id: "profile", title: "Profile", fields: [],
  content: [
    { type: "stack", id: "cols", direction: "HORIZONTAL", cssClass: "sui-cols", children: [
      { type: "stack", id: "c1", children: [
        { type: "field", id: "first", label: "First name", fieldType: "TEXT", value: "", editable: true },
        { type: "field", id: "email", label: "Email",      fieldType: "TEXT", value: "", editable: true },
      ] },
      { type: "stack", id: "c2", children: [
        { type: "field", id: "last",  label: "Last name", fieldType: "TEXT", value: "", editable: true },
        { type: "field", id: "phone", label: "Phone",     fieldType: "TEXT", value: "", editable: true },
      ] },
    ] },
  ],
  actions: [
    { type: "action", id: "save", label: "Save", style: "PRIMARY",
      onClick: { behavior: "APPLY_RESPONSE", method: "POST", url: "/profile", payload: "profile" } },
  ],
}
```

</TabItem>
</Tabs>

### Groups

`UiFieldGroup` renders a native `<fieldset><legend>` — a titled box whose
heading is announced for every field inside (better than a styled `<div>` for
accessibility), with less ceremony:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiFieldGroup.of("shipping", "Shipping address")
    .hint("Where should we send it?")
    .field(UiField.text("street", "Street", ""))
    .content(UiStack.of("row").direction(UiStack.Direction.HORIZONTAL)
            .child(UiField.text("zip",  "ZIP", ""))
            .child(UiField.text("city", "City", ""))
            .withCssClass("sui-cols"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "fieldgroup", "id": "shipping", "title": "Shipping address",
  "hint": "Where should we send it?",
  "content": [
    { "type": "field", "id": "street", "label": "Street", "fieldType": "TEXT", "value": "" },
    { "type": "stack", "id": "row", "direction": "HORIZONTAL", "cssClass": "sui-cols", "children": [
      { "type": "field", "id": "zip",  "label": "ZIP",  "fieldType": "TEXT", "value": "" },
      { "type": "field", "id": "city", "label": "City", "fieldType": "TEXT", "value": "" }
    ] }
  ]
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "fieldgroup", id: "shipping", title: "Shipping address",
  hint: "Where should we send it?",
  content: [
    { type: "field", id: "street", label: "Street", fieldType: "TEXT", value: "" },
    { type: "stack", id: "row", direction: "HORIZONTAL", cssClass: "sui-cols", children: [
      { type: "field", id: "zip",  label: "ZIP",  fieldType: "TEXT", value: "" },
      { type: "field", id: "city", label: "City", fieldType: "TEXT", value: "" },
    ] },
  ],
}
```

</TabItem>
</Tabs>

A group is transparent to submission — the fields inside keep their `name` and
ride along in the single form payload.

### Tabs

Put a `UiSection` (tab bar) in the content to split a long form. Inactive tabs
are only **hidden**, not removed — so the submit still collects the fields on
every tab. The tab switch is client-side (no round-trip):

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("account", "Account")
    .content(UiSection.of("tabs", null)
        .section("contact", "Contact", UiFieldGroup.of("c", "Contact")
                .field(UiField.text("email", "Email", "").asEditable()))
        .section("address", "Address", UiFieldGroup.of("a", "Address")
                .field(UiField.text("street", "Street", "").asEditable())))
    .action(UiAction.primary("save", "Save").dispatch("POST", "/account", "account"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "account", "title": "Account", "fields": [],
  "content": [
    { "type": "section", "id": "tabs", "sections": [
      { "type": "section-entry", "id": "contact", "title": "Contact", "content":
        { "type": "fieldgroup", "id": "c", "title": "Contact", "content": [
          { "type": "field", "id": "email", "label": "Email", "fieldType": "TEXT", "value": "", "editable": true }
        ] } },
      { "type": "section-entry", "id": "address", "title": "Address", "content":
        { "type": "fieldgroup", "id": "a", "title": "Address", "content": [
          { "type": "field", "id": "street", "label": "Street", "fieldType": "TEXT", "value": "", "editable": true }
        ] } }
    ] }
  ],
  "actions": [
    { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/account", "payload": "account" } }
  ]
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "form", id: "account", title: "Account", fields: [],
  content: [
    { type: "section", id: "tabs", sections: [
      { type: "section-entry", id: "contact", title: "Contact", content:
        { type: "fieldgroup", id: "c", title: "Contact", content: [
          { type: "field", id: "email", label: "Email", fieldType: "TEXT", value: "", editable: true },
        ] } },
      { type: "section-entry", id: "address", title: "Address", content:
        { type: "fieldgroup", id: "a", title: "Address", content: [
          { type: "field", id: "street", label: "Street", fieldType: "TEXT", value: "", editable: true },
        ] } },
    ] },
  ],
  actions: [
    { type: "action", id: "save", label: "Save", style: "PRIMARY",
      onClick: { behavior: "APPLY_RESPONSE", method: "POST", url: "/account", payload: "account" } },
  ],
}
```

</TabItem>
</Tabs>

The [client-only shop demo](./shop-demo.md#client-only-variant) has a live form
combining tabs, columns and a group that submits as one payload.

## Validation

Validation is **server-driven** and needs no special machinery — the field
model already carries the error. On an invalid submit, re-render the form (as a
`UiPatch` so the rest of the page stays put) with the message on the offending
field via `UiField.error(...)`, and optionally a form-level banner via
`UiForm.error(...)`. Re-render with the values the user typed so nothing is lost.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
@PostMapping("/register")
public Object register(@RequestBody Registration r) {
    var errors = validate(r);                       // your own checks
    if (!errors.isEmpty()) {
        return UiPatch.of().patch(UiPatch.Operation.replace("register-form",
            UiForm.of("register-form", "Register")
                .error("Please fix the errors below.")            // form-level banner
                .field(UiField.text("email", "Email", r.email()).asEditable()
                        .error(errors.get("email")))              // per-field message
                .field(UiField.text("name", "Name", r.name()).asEditable())
                .action(UiAction.primary("save", "Save")
                        .dispatch("POST", "/register", "register-form"))));
    }
    // …persist, then return the next page
}
```

</TabItem>
<TabItem value="json" label="JSON">

The `UiPatch` the server sends back on an invalid submit — `formError` is the
form-level banner, `validationError` the per-field message:

```json
{ "patches": [ { "op": "REPLACE", "targetId": "register-form", "node": {
  "type": "form", "id": "register-form", "title": "Register",
  "formError": "Please fix the errors below.",
  "fields": [
    { "type": "field", "id": "email", "label": "Email", "fieldType": "TEXT",
      "value": "nope", "editable": true, "validationError": "Enter a valid email." },
    { "type": "field", "id": "name",  "label": "Name",  "fieldType": "TEXT", "value": "Ada", "editable": true }
  ],
  "actions": [ { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
    "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/register", "payload": "register-form" } } ]
} } ] }
```

</TabItem>
<TabItem value="js" label="JavaScript">

The same flow with no backend — an `INVOKE` handler validates `ctx.payload` and
returns the errored form as a patch:

```js
bus.registerClientHandler("register.submit", (ctx) => {
    const r = ctx.payload;
    const errors = validate(r);                     // your own checks
    if (Object.keys(errors).length) {
        return { patches: [{ op: "REPLACE", targetId: "register-form", node: {
            type: "form", id: "register-form", title: "Register",
            formError: "Please fix the errors below.",
            fields: [
                { type: "field", id: "email", label: "Email", fieldType: "TEXT",
                  value: r.email, editable: true, validationError: errors.email },
                { type: "field", id: "name", label: "Name", fieldType: "TEXT",
                  value: r.name, editable: true },
            ],
            actions: [{ type: "action", id: "save", label: "Save", style: "PRIMARY",
                onClick: { behavior: "INVOKE", handler: "register.submit", payload: "register-form" } }],
        } }] };
    }
    // …store, then return the next page/patch
});
```

</TabItem>
</Tabs>

The renderer marks the field wrapper with `sui-field--error` (red border +
label), prints the message in a `.sui-error` span, and shows the
`.sui-form-error` banner — identical in SSR and SPA:

```html
<div class="sui-field sui-field--error" id="email">
  <label for="email__input">Email</label>
  <input id="email__input" name="email" value="nope">
  <span class="sui-error">Enter a valid email.</span>
</div>
```

Native HTML constraints (`asRequired()`, and `min` / `max` / `step` on number
and date fields) give the browser a first, JS-free line of validation; the
server-side pass is the authority. The
[file explorer demo](./file-explorer-demo.md) validates its "new folder" form
this way — an empty, invalid, or duplicate name comes back as a field error
with the typed value preserved.

## See also

- [Triggers &amp; actions](./triggers.md) — submit, `onChange`, and the payload rules
- [Node vocabulary](./node-vocabulary.md) — every field type and node
- [Building an app](./building-an-app.md) — forms in a full CRUD screen
