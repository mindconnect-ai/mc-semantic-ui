---
title: Triggers & actions
sidebar_position: 4
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Triggers &amp; actions

:::tip Looking for worked examples?
This page is the **reference** — every behavior, field by field. For complete
recipes that show *both* sides of the round-trip (delete a row, submit a form
with validation errors, search, no-backend handlers), plus a live demo you can
click, see the **[Triggers cookbook](./triggers-cookbook.md)**.
:::

:::info One tree, three ways
The examples below build the same node/trigger tree three ways — the **Java**
builder, the **JSON** it serialises to (the wire format), and the equivalent
**JavaScript** object literal you'd write in a backend-free client app. Pick the
tab that matches your stack; the tree is identical. The choice syncs across the
page.
:::

A **`UiTrigger`** is the declarative description of *what happens when a UI
event fires* — a button click, a list-item click, a field change, a page
button. One small value carries everything the client needs: which endpoint to
call (or which local function), how, with what payload, and what to do with the
result.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.primary("save", "Save")
        .onClick(UiTrigger.api("POST", "/api/products", "product-form"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
  "onClick": {
    "behavior": "APPLY_RESPONSE", "method": "POST",
    "url": "/api/products", "payload": "product-form"
  }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "save", label: "Save", style: "PRIMARY",
  onClick: {
    behavior: "APPLY_RESPONSE", method: "POST",
    url: "/api/products", payload: "product-form",
  },
}
```

</TabItem>
</Tabs>

The renderer serialises that trigger into a `data-trigger='{…}'` attribute; the
browser-side `SuiEventBus` reads it, runs the matching **behaviour**, and
applies whatever comes back. Event sources (button, list item, field…) never
need to know about the variants — they just carry a trigger.

**On the client** you don't dispatch triggers by hand: mount a tree and the
`SuiEventBus` fires the triggers on it — a click runs a button's `onClick`, a
change runs a field's `onChange`, a drop runs an upload's `onUpload`:

```js
import { createDefaultRenderer } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const root = document.getElementById("app");
const renderer = createDefaultRenderer().attach(root);
const bus = new SuiEventBus(renderer, root);

renderer.mount(page);          // page contains the actions/fields shown below
// INVOKE triggers call a handler you register by name:
bus.registerClientHandler("products.detail", (ctx) => ({ /* UiPage | UiPatch */ }));
```

The **JavaScript** tabs below show the trigger objects that live on a node's
`onClick` / `onChange` / `onUpload` inside that mounted tree.

## Where triggers live

The same `UiTrigger` value is reused across every event source:

| Source                      | Field         | Fires on          |
|-----------------------------|---------------|-------------------|
| `UiAction`                  | `onClick`     | click / submit    |
| `UiListItem`                | `onClick`     | click             |
| `UiTree.Node`               | `onClick`     | click             |
| `UiField`                   | `onChange`    | value change      |
| `UiUpload`                  | `onUpload`    | file drop / pick  |
| `UiTable` / `UiList` paging | `pageTrigger` | page-button click |
| `UiTable` `rowActions`      | `onClick`     | click (per row)   |
| **any node**                | `onClick` · `onDblClick` · `onHover` · `onLeave` · `onChange` · `onInput` | the matching DOM event |

## Events on any node

Those six trigger fields are declared on `UiNode`, so **every** node type has
them — a `stack`, a `text`, or a type you invented. `UiAction.onClick` and
`UiField.onChange` are not special cases; they are the same inherited fields,
which is why there is only ever one place a trigger can live.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
var row = UiStack.of(name, price);
row.setOnClick(UiTrigger.go("/orders/42"));
row.setOnHover(UiTrigger.toast("Preview"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "stack", "id": "row", "children": [ /* … */ ],
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/orders/42" },
  "onHover": { "behavior": "PATCH", "patch": { "patches": [], "toasts": [
    { "level": "INFO", "message": "Preview" } ] } } }
```

</TabItem>
</Tabs>

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=node-events"
  title="Live: events on any node"
  loading="lazy"
  style={{width: '100%', height: '320px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — a `stack`, a `text` and a `field`, none of them actions. Click the card,
hover it, double-click the text, change the select.*

| Field | DOM event | Notes |
|---|---|---|
| `onClick` | `click` | The innermost element wins — a button or link inside still handles its own click. |
| `onDblClick` | `dblclick` | |
| `onHover` | pointer enters | Delegated via `mouseover`; moves **between children don't re-fire**. |
| `onLeave` | pointer leaves | Same filtering as `onHover`. |
| `onChange` | `change` | A value was committed. |
| `onInput` | `input` | Fires as the user types. |

Each one renders as `data-sui-on-<event>='<trigger JSON>'` and is delegated by
the event bus from the root — so it costs nothing until something happens, and
behaves identically in SSR and SPA output.

:::note One exception, and why
An action, a link and a form field carry their click/change in
`data-trigger` / `data-change-trigger` instead. Those attributes do more than
listen: they drive the **no-JS fallback** — a GET action renders as a real
`<a href>`, a DELETE as a `<form>` with `_method`. Same field, same model; only
the attribute the renderer chooses differs, so a trigger never lands on an
element twice.
:::

:::tip Make a whole row clickable
This is the clean answer to "the row should open the record": put `onClick` on
the row's `stack` instead of wrapping everything in an action.

The container is the **fallback**, not an interceptor — a click is offered to
every built-in handler first (action, link, tab, menu), and only reaches the
container when none of them was responsible. So a Delete button inside a
clickable row still deletes, and the row still opens when you click the empty
space next to it.
:::

## Anatomy of a trigger

```java
public class UiTrigger {
    private String    url;      // target endpoint (required except INVOKE / PATCH)
    private String    method;   // GET (default) / POST / PUT / DELETE
    private String    payload;  // id of a form-like node whose fields become the body
    private Behavior  behavior; // what to do with the response (default APPLY_RESPONSE)
    private String    handler;  // name of a client-side JS handler (INVOKE only)
    private UiPatch   patch;    // inline patch applied as-is (PATCH only)
}
```

The TypeScript mirror (`model.ts`) is identical; the `behavior` union is open
(`string & {}`) so apps can register their own.

## The hybrid model

Every trigger renders **twice** from one source, so it works with or without
JavaScript:

- **SSR / no JS** — a `UiAction` becomes a native `<a href>` (GET) or a
  `<form method=… action=…>` (POST/PUT/DELETE, tunnelled via a hidden
  `_method` field). The browser handles it natively.
- **SPA / bus loaded** — the same element carries `data-trigger='{…}'`; the
  `SuiEventBus` intercepts the click/submit, fetches, and Idiomorph-diffs the
  response into the DOM.

A page upgrades from SSR to SPA just by including the bootstrap script — no
controller change. See [Rendering modes](./rendering-modes.md).

:::note
Two behaviours — `INVOKE` and `PATCH` — are **JavaScript-only** by nature
(there is no server call, so there is no no-JS fallback). Their `data-trigger`
only does something once the `SuiEventBus` is loaded.
:::

---

## `APPLY_RESPONSE` — navigate / patch (default)

The workhorse. The client fetches the URL, then branches on the response
shape: a **`UiPage`** (has `node` / `navigate`) triggers a full swap +
`pushState`; a **`UiPatch`** (has `patches`) is applied in place.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Plain navigation — GET, render the returned UiPage/UiPatch.
UiAction.link("open", "Open").onClick(UiTrigger.go("/admin/products/42"));

// Generic API call with a form body.
UiAction.primary("save", "Save")
        .onClick(UiTrigger.api("POST", "/api/products", "product-form"));

// DELETE with confirmation (returns a UiPatch that removes the row).
UiAction.danger("delete", "Delete")
        .confirm("Delete this product?")
        .onClick(UiTrigger.api("DELETE", "/api/products/42"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
[
  { "type": "action", "id": "open", "label": "Open", "appearance": "LINK",
    "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/admin/products/42" } },
  { "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
    "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST",
                 "url": "/api/products", "payload": "product-form" } },
  { "type": "action", "id": "delete", "label": "Delete", "style": "DANGER",
    "confirm": "Delete this product?",
    "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/api/products/42" } }
]
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
[
  { type: "action", id: "open", label: "Open", appearance: "LINK",
    onClick: { behavior: "APPLY_RESPONSE", method: "GET", url: "/admin/products/42" } },
  { type: "action", id: "save", label: "Save", style: "PRIMARY",
    onClick: { behavior: "APPLY_RESPONSE", method: "POST",
               url: "/api/products", payload: "product-form" } },
  { type: "action", id: "delete", label: "Delete", style: "DANGER",
    confirm: "Delete this product?",
    onClick: { behavior: "APPLY_RESPONSE", method: "DELETE", url: "/api/products/42" } },
]
```

</TabItem>
</Tabs>

The server can return any of three shapes from the same endpoint:

```java
// full page
return UiPage.of("/admin/products", table);
// a single partial patch
return UiPatch.of().patch(UiPatch.Operation.replace("product-table", newTable));
// or several patches (a JSON array), applied in order — each envelope may
// carry its own toasts / dialog
return List.of(
    UiPatch.of().patch(UiPatch.Operation.replace("product-table", newTable)),
    UiPatch.of().toast(UiToast.success("Saved.")));
```

The client tells the shapes apart structurally: an object with `node`/`navigate`
is a `UiPage`, an object with a `patches` array is one `UiPatch`, and a
top-level **array** is multiple patches. `go(...)`, `api(...)` and
`.dispatch(...)` on `UiAction` are all thin wrappers around this behaviour.

## `STREAM` — Server-Sent Events

POSTs and reads an SSE response; each `event: patch` frame is applied as a
`UiPatch` the moment it arrives. Used for streaming chat / agent turns — the
stream survives navigation and reconnects on F5.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.primary("send", "Send")
        .onClick(UiTrigger.stream("POST", "/api/chat", "chat-form"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "send", "label": "Send", "style": "PRIMARY",
  "onClick": { "behavior": "STREAM", "method": "POST",
               "url": "/api/chat", "payload": "chat-form" }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "send", label: "Send", style: "PRIMARY",
  onClick: { behavior: "STREAM", method: "POST",
             url: "/api/chat", payload: "chat-form" },
}
```

</TabItem>
</Tabs>

Append tokens to a message node with an `APPEND` patch per batch:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiPatch.of().patch(UiPatch.Operation.append("messages", tokenNode));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "patches": [ { "op": "APPEND", "targetId": "messages", "node": { "…": "…" } } ] }
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{ patches: [ { op: "APPEND", targetId: "messages", node: { /* … */ } } ] }
```

</TabItem>
</Tabs>

## `DOWNLOAD` — save a file

Fetches the URL as a blob and triggers the browser save dialog; the filename
comes from the response's `Content-Disposition` header.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.secondary("export", "Export .xlsx")
        .onClick(UiTrigger.download("/api/products/export.xlsx"));
// or the shortcut: .download("/api/products/export.xlsx", "products.xlsx")
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "export", "label": "Export .xlsx", "style": "SECONDARY",
  "onClick": { "behavior": "DOWNLOAD", "method": "GET", "url": "/api/products/export.xlsx" }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "export", label: "Export .xlsx", style: "SECONDARY",
  onClick: { behavior: "DOWNLOAD", method: "GET", url: "/api/products/export.xlsx" },
}
```

</TabItem>
</Tabs>

## `OPEN_IN_TAB` — view a blob

Fetches the URL as a blob and opens it in a new tab via a blob URL — handy for
authenticated PDFs/images that a plain `target="_blank"` link couldn't reach.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.link("view", "View invoice")
        .onClick(UiTrigger.openInTab("/api/invoices/42.pdf"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "view", "label": "View invoice", "appearance": "LINK",
  "onClick": { "behavior": "OPEN_IN_TAB", "method": "GET", "url": "/api/invoices/42.pdf" }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "view", label: "View invoice", appearance: "LINK",
  onClick: { behavior: "OPEN_IN_TAB", method: "GET", url: "/api/invoices/42.pdf" },
}
```

</TabItem>
</Tabs>

## `INVOKE` — call a local JS function

No server call: the trigger names a **client-side handler** registered on the
bus. The handler receives the same context a server call would (trigger,
collected payload, source element, owning bus) and returns a `UiPage` /
`UiPatch` — which the bus applies through the normal response path. This lets a
whole screen run in the browser with no backend.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.secondary("view", "Details")
        .onClick(UiTrigger.invoke("products.detail"));
// with a payload source:
UiTrigger.invoke("products.search", "product-search");
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "view", "label": "Details", "style": "SECONDARY",
  "onClick": { "behavior": "INVOKE", "handler": "products.detail", "url": "42" }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "view", label: "Details", style: "SECONDARY",
  onClick: { behavior: "INVOKE", handler: "products.detail", url: "42" },
}
```

</TabItem>
</Tabs>

On the client, register the handler by name:

```js
bus.registerClientHandler("products.detail", (ctx) => {
    const p = byId(ctx.trigger.url);          // e.g. id carried in `url`
    return { patches: [], dialog: detailDialog(p) };   // a UiPatch
});
```

Returning `void`/`null` means "I applied my own changes already" (e.g. the
handler called `ctx.bus.applyPatch(...)` itself). See the
[client-only shop demo](./shop-demo.md#client-only-variant) for search, delete,
reset and a detail dialog built entirely this way.

## `PATCH` — apply an inline patch (no round-trip)

The leanest option: the trigger **carries the finished `UiPatch` inline**. No
server, no handler — the bus just applies `trigger.patch`. Ideal for static,
known-ahead UI logic: a list row that fills a detail pane, a button that opens
a fixed dialog, a toggle that reveals a section.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiPatch fill = UiPatch.of()
        .patch(UiPatch.Operation.replace("catalog-detail", productDetail(p)));

UiAction.link("pick", p.getName())
        .onClick(UiTrigger.patch(fill));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "action", "id": "pick", "label": "Office chair", "appearance": "LINK",
  "onClick": {
    "behavior": "PATCH",
    "patch": { "patches": [
      { "op": "REPLACE", "targetId": "catalog-detail",
        "node": { "type": "detail", "…": "…" } }
    ] }
  }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "action", id: "pick", label: "Office chair", appearance: "LINK",
  onClick: {
    behavior: "PATCH",
    patch: { patches: [
      { op: "REPLACE", targetId: "catalog-detail",
        node: { type: "detail", /* … */ } },
    ] },
  },
}
```

</TabItem>
</Tabs>

The trigger carries **one `UiPatch`**, and a `UiPatch` already holds a *list* of
operations — so a single click can touch several targets at once. `patch(...)`
takes the operations directly (varargs or a `List`) so you rarely build the
envelope by hand:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// Fill the detail pane AND highlight the picked row in one click.
UiAction.link("pick", p.getName()).onClick(UiTrigger.patch(
        UiPatch.Operation.replace("catalog-detail", productDetail(p)),
        UiPatch.Operation.replace("catalog-list",   highlightedList(p))));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "behavior": "PATCH", "patch": { "patches": [
    { "op": "REPLACE", "targetId": "catalog-detail", "node": { "…": "…" } },
    { "op": "REPLACE", "targetId": "catalog-list",   "node": { "…": "…" } }
] } }
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{ behavior: "PATCH", patch: { patches: [
    { op: "REPLACE", targetId: "catalog-detail", node: { /* … */ } },
    { op: "REPLACE", targetId: "catalog-list",   node: { /* … */ } },
] } }
```

</TabItem>
</Tabs>

Because the patch is baked per element, build such lists **item-by-item** (a
`UiList`) rather than through a shared table `cellTemplate` — templates apply a
per-row id suffix that would rename a stable `REPLACE` target.

Use `PATCH` when the update is fully known at render time; use `INVOKE` when it
depends on runtime state a handler must compute.

## `UPLOAD` — send files

POSTs the selected files to `url` as `multipart/form-data` and applies the
response as a `UiPage` / `UiPatch`. Fired by a **`UiUpload`** drag-and-drop zone
or by a **`FILE`** field's change. The multipart part name is the node id (or
`UiUpload.name`).

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// A drag-and-drop drop zone (its own UiNode):
UiUpload.of("product-image", "Product image")
        .accept("image/*")
        .hint("PNG or JPG, max 5 MB")
        .uploadTo("/api/products/42/image");     // = onUpload(UiTrigger.upload(...))

// Or a single inline file field, uploading on selection:
UiField.file("avatar", "Avatar")
        .accept("image/*")
        .onChange(UiTrigger.upload("/api/avatar"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
[
  { "type": "upload", "id": "product-image", "label": "Product image",
    "accept": "image/*", "hint": "PNG or JPG, max 5 MB",
    "onUpload": { "behavior": "UPLOAD", "method": "POST", "url": "/api/products/42/image" } },
  { "type": "field", "id": "avatar", "label": "Avatar", "fieldType": "FILE",
    "editable": true, "accept": "image/*",
    "onChange": { "behavior": "UPLOAD", "method": "POST", "url": "/api/avatar" } }
]
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
[
  { type: "upload", id: "product-image", label: "Product image",
    accept: "image/*", hint: "PNG or JPG, max 5 MB",
    onUpload: { behavior: "UPLOAD", method: "POST", url: "/api/products/42/image" } },
  { type: "field", id: "avatar", label: "Avatar", fieldType: "FILE",
    editable: true, accept: "image/*",
    onChange: { behavior: "UPLOAD", method: "POST", url: "/api/avatar" } },
]
```

</TabItem>
</Tabs>

The server receives a normal multipart request and returns the next tree —
e.g. a `UiPatch` that swaps in the uploaded image or a success toast. The
[file explorer demo](./file-explorer-demo.md) wires this up end to end.

:::tip Client-only uploads
Point the upload at an **`INVOKE`** handler instead, and the bus hands it the
raw `File` objects via `ctx.files` — no network. Handy for an in-browser image
preview:

```js
bus.registerClientHandler("image.preview", (ctx) => {
    const src = URL.createObjectURL(ctx.files[0]);
    return { patches: [{ op: "REPLACE", targetId: "preview",
                         node: { type: "image", id: "preview", src } }] };
});
```
:::

---

## Field `onChange` triggers

A `UiField` can fire a trigger when its value changes — so a single control can
drive UI logic without submitting the whole form. Common case: a checkbox that
enables/disables another field.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("delivery-form", null)
    .field(UiField.bool("diff-address", "Ship to a different address?", false)
            .asEditable()
            .onChange(UiTrigger.invoke("delivery.toggle", "delivery-form")))
    .field(UiField.text("delivery-address", "Address", "")); // target of the patch
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "form", "id": "delivery-form",
  "fields": [
    { "type": "field", "id": "diff-address", "label": "Ship to a different address?",
      "fieldType": "BOOLEAN", "value": false, "editable": true,
      "onChange": { "behavior": "INVOKE", "handler": "delivery.toggle", "payload": "delivery-form" } },
    { "type": "field", "id": "delivery-address", "label": "Address", "fieldType": "TEXT", "value": "" }
  ]
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  type: "form", id: "delivery-form",
  fields: [
    { type: "field", id: "diff-address", label: "Ship to a different address?",
      fieldType: "BOOLEAN", value: false, editable: true,
      onChange: { behavior: "INVOKE", handler: "delivery.toggle", payload: "delivery-form" } },
    { type: "field", id: "delivery-address", label: "Address", fieldType: "TEXT", value: "" },
  ],
}
```

</TabItem>
</Tabs>

The handler on the client reads the checkbox state and patches the target field:

```js
bus.registerClientHandler("delivery.toggle", (ctx) => {
    const enabled = ctx.payload["diff-address"] === true;
    return { patches: [{ op: "REPLACE", targetId: "delivery-address",
                         node: addressField(enabled) }] };
});
```

The control carries a **`data-change-trigger`** attribute (distinct from the
click-owned `data-trigger`, so the control still toggles/commits natively on
click). The bus dispatches it on `change`, folding in the surrounding form's
values as payload. An `onChange` trigger can use any behaviour — `INVOKE` for a
state-dependent update, `PATCH` for a fixed reveal, or `APPLY_RESPONSE` to ask
the server.

:::tip
`onChange` differs from `submitOnChange`: `submitOnChange` re-submits the whole
form (server round-trip); `onChange` fires one targeted trigger.
:::

## Payload collection

When a trigger names a `payload` (the id of a form-like node), the bus walks
that node's named `<input>` / `<select>` / `<textarea>` controls and serialises
them to JSON. Semantic types (`NUMBER`, `CURRENCY`, `BOOLEAN`…) come from the
`data-sui-type` attribute, so a currency stays a number, a checkbox a boolean.

- **GET / HEAD** — the payload is folded into the query string
  (`/products?q=chair`), matching native `<form method="GET">`.
- **body verbs** — the payload is sent as a JSON request body.

If an action inside a `<form data-sui="form">` omits `payload`, the bus infers
it from the surrounding form id — so a bare `.dispatch("GET", "/products")`
search still picks up the form's `?q=…`.

## Placeholder substitution

Row actions, cell templates and pagination share a trigger template across many
rows/pages. The renderer substitutes placeholders per render:

- **`{id}`** (and any row-data key) in **row-action** and **cell-template**
  trigger URLs → the row's value.
- **`{page}`** in a **pagination** trigger URL → the target page number.

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// One row-action, correct target per row:
UiAction.danger("delete", "Delete")
        .onClick(UiTrigger.api("DELETE", "/api/products/{id}"));

// Pagination:
table.paginate(page, size, total, UiTrigger.go("/admin/products?page={page}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "rowActions": [
    { "type": "action", "id": "delete", "label": "Delete", "style": "DANGER",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/api/products/{id}" } }
  ],
  "pagination": {
    "page": 1, "size": 20, "total": 137,
    "pageTrigger": { "behavior": "APPLY_RESPONSE", "method": "GET",
                     "url": "/admin/products?page={page}" }
  }
}
```

</TabItem>
<TabItem value="js" label="JavaScript">

```js
{
  rowActions: [
    { type: "action", id: "delete", label: "Delete", style: "DANGER",
      onClick: { behavior: "APPLY_RESPONSE", method: "DELETE", url: "/api/products/{id}" } },
  ],
  pagination: {
    page: 1, size: 20, total: 137,
    pageTrigger: { behavior: "APPLY_RESPONSE", method: "GET",
                   url: "/admin/products?page={page}" },
  },
}
```

</TabItem>
</Tabs>

Cell templates substitute **every** string field of the cloned subtree, and
suffix nested ids with `__<rowId>` to keep the DOM unique.

## Custom behaviours

The four network behaviours, `INVOKE` and `PATCH` are just the built-ins. Apps
can register their own:

```js
// A behaviour that copies the trigger's url to the clipboard.
bus.registerBehavior("COPY", (ctx) => navigator.clipboard.writeText(ctx.url));
```

```json
{ "behavior": "COPY", "url": "https://example.com/share/42" }
```

The `behavior` field is an open string on both sides, so emitting a custom value
from Java (`trigger.setBehavior(...)` via a string, or a custom enum on your
side) and registering the matching handler is all it takes. `registerBehavior`
is the low-level hook; `registerClientHandler` is the sugar on top of the
built-in `INVOKE` dispatcher.

## Quick reference

| Behaviour        | Java factory                          | Does                                   | Backend? |
|------------------|---------------------------------------|----------------------------------------|:--------:|
| `APPLY_RESPONSE` | `go` / `api` / `dispatch`             | fetch → apply `UiPage` or `UiPatch`    | yes      |
| `STREAM`         | `stream`                              | SSE → apply `UiPatch` per frame        | yes      |
| `DOWNLOAD`       | `download`                            | fetch blob → save dialog               | yes      |
| `OPEN_IN_TAB`    | `openInTab` / `openBlob`              | fetch blob → open in new tab           | yes      |
| `INVOKE`         | `invoke(handler[, payload])`          | call a registered JS handler           | **no**   |
| `PATCH`          | `patch(op… / list / uiPatch)`         | apply the inline patch (1+ operations) | **no**   |
| `UPLOAD`         | `upload([method,] url)`               | POST files as multipart/form-data      | yes\*    |

\* `UPLOAD` needs a backend to receive the files; point the upload's trigger at
an `INVOKE` handler instead to process the `File` objects client-side.

## See also

- [Rendering modes](./rendering-modes.md) — SSR / SPA / patch flow
- [Building an app](./building-an-app.md) — triggers in a full CRUD screen
- [Node.js quickstart](./quickstart-node.md) — the trigger JSON is language-agnostic
- [Shop demo](./shop-demo.md) — server-driven and client-only variants
