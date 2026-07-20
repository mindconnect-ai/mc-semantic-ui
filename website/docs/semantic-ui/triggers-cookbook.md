---
title: 'Triggers: cookbook'
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Triggers cookbook

[Triggers & actions](./triggers.md) is the reference — every behavior, field by
field. This page is the other half: **complete, working recipes**, each showing
*both* sides of the round-trip — the node that fires, and the code that answers.

:::tip The one rule
A node carries a trigger → the trigger calls you → **you answer with a `UiPage`
or a `UiPatch`**. Return a `UiPage` to replace the screen, a `UiPatch` to change
part of it. That's the entire protocol.
:::

## Try it — no backend at all

The buttons below are real `UiAction` nodes with `INVOKE` triggers. There is no
server: each click runs a **client handler** that returns a `UiPatch`, and the
bus applies it (and shows the toast).

<iframe
  src="/mc-semantic-ui/embed/triggers.html"
  title="Live trigger demo: client handlers returning a UiPatch"
  loading="lazy"
  style={{width: '100%', height: '190px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

That's the whole source of the demo above:

```js
const bus = new SuiEventBus(renderer, host);

let total = 0;
bus.registerClientHandler("addWidget", () => {
  total += 19;
  return {
    patches: [
      { op: "REPLACE", targetId: "cart-total",
        node: { type: "text", id: "cart-total", text: "€ " + total.toFixed(2) } }
    ],
    toasts: [ { level: "SUCCESS", message: "Widget added" } ]
  };
});

// the node that fires it
{ type: "action", id: "add-w", label: "Add Widget (€19)", style: "PRIMARY",
  onClick: { behavior: "INVOKE", handler: "addWidget" } }
```

## Recipe: delete a row, remove it in place

Confirm first, delete on the server, then send back a patch that removes just
that row — the rest of the table never re-renders.

**The node:**

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiAction.danger("delete", "Delete")
        .confirm("Delete this product?")
        .onClick(UiTrigger.api("DELETE", "/products/{id}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "action", "id": "delete", "label": "Delete", "style": "DANGER",
  "confirm": "Delete this product?",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE",
               "url": "/products/{id}" } }
```

</TabItem>
</Tabs>

`{id}` is substituted from the row the action sits in. `confirm` makes the bus
ask before firing.

**The answer:**

<Tabs groupId="stack">
<TabItem value="spring" label="Java / Spring Boot">

```java
@DeleteMapping("/products/{id}")
public UiPatch delete(@PathVariable String id) {
    productService.delete(id);
    return UiPatch.of()
            .patch(UiPatch.Operation.remove("row-" + id))
            .toast(UiToast.success("Product deleted"));
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```js
app.delete("/products/:id", (req, res) => {
  db.remove(req.params.id);
  res.json({
    patches: [ { op: "REMOVE", targetId: "row-" + req.params.id } ],
    toasts:  [ { level: "SUCCESS", message: "Product deleted" } ]
  });
});
```

</TabItem>
</Tabs>

Returning the whole refreshed `UiPage` instead also works — it's just more bytes.

## Recipe: submit a form, show validation errors

The action names the form as its **payload**, so the bus collects every named
control inside it and sends them as the body.

**The node:**

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiForm.of("product-form", "New product")
    .field(UiField.text("name", "Name", ""))
    .field(UiField.number("price", "Price", null))
    .action(UiAction.primary("save", "Save")
            .onClick(UiTrigger.api("POST", "/products", "product-form")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "action", "id": "save", "label": "Save", "style": "PRIMARY",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST",
               "url": "/products", "payload": "product-form" } }
```

</TabItem>
</Tabs>

**The answer** — on failure, patch the *fields* with errors so the user's input
survives; on success, navigate away:

<Tabs groupId="stack">
<TabItem value="spring" label="Java / Spring Boot">

```java
@PostMapping("/products")
public Object save(@RequestBody ProductForm form) {
    if (form.name() == null || form.name().isBlank()) {
        return UiPatch.of().patch(UiPatch.Operation.replace("name",
                UiField.text("name", "Name", form.name())
                       .error("Name is required")));
    }
    productService.create(form);
    return UiPage.of("/products", productTable());   // full page → navigate
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```js
app.post("/products", express.json(), (req, res) => {
  if (!req.body.name?.trim()) {
    return res.json({ patches: [
      { op: "REPLACE", targetId: "name",
        node: { type: "field", id: "name", label: "Name", fieldType: "TEXT",
                value: req.body.name ?? "", error: "Name is required" } }
    ]});
  }
  db.create(req.body);
  res.json(productListPage());
});
```

</TabItem>
</Tabs>

## Recipe: search / filter a table

A `GET` whose payload is the search form. The bus folds the collected fields
into the query string, so the URL stays bookmarkable.

```java
UiForm.of("search", null)
    .field(UiField.text("q", "Search", q).asEditable())
    .action(UiAction.primary("go", "Search")
            .onClick(UiTrigger.api("GET", "/products", "search")));
```

Answer with the refreshed page (or patch just the table's `id` to leave the
search box — and the cursor — untouched).

## Recipe: no round-trip at all

Two ways to change the UI without a server:

- **`PATCH`** — the patch is baked into the trigger at render time. No handler,
  no fetch. Good for reveal/toggle/fill-from-a-list.
- **`INVOKE`** — the trigger names a client handler you registered; it returns a
  `UiPage`/`UiPatch` (that's the live demo at the top).

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// PATCH: swap a text node, entirely client-side
UiAction.secondary("show", "Show details")
        .onClick(UiTrigger.patch(UiPatch.Operation.replace("detail",
                UiText.of("detail", "Ships in 2–3 days"))));

// INVOKE: call a browser-local "endpoint"
UiAction.primary("add", "Add to cart").onClick(UiTrigger.invoke("addToCart"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "action", "id": "show", "label": "Show details", "style": "SECONDARY",
  "onClick": { "behavior": "PATCH", "patch": { "patches": [
      { "op": "REPLACE", "targetId": "detail",
        "node": { "type": "text", "id": "detail", "text": "Ships in 2–3 days" } }
  ]}}}
```

</TabItem>
</Tabs>

## The handler context (`ctx`)

Every client handler and custom behavior receives one argument:

```ts
interface BehaviorContext {
    trigger: UiTrigger;                        // the trigger as authored
    payload: Record<string, unknown> | null;   // collected form fields, if any
    sourceElement: HTMLElement;                // the clicked button/link/field
    url: string;                               // resolved (placeholders applied)
    method: string;                            // resolved, upper-cased
    fetch: typeof fetch;                       // the bus's auth-aware fetcher
    bus: SuiEventBus;                          // to dispatch follow-on triggers
    files?: File[];                            // for uploads
}
```

A `ClientHandler` returns `UiPage | UiPatch | void`:

```ts
bus.registerClientHandler("saveDraft", async (ctx) => {
  localStorage.setItem("draft", JSON.stringify(ctx.payload));
  return { toasts: [ { level: "INFO", message: "Draft saved locally" } ] };
});
```

Returning **nothing** means *"I already applied my changes"* — use it when the
handler called `ctx.bus.applyPatch(...)` itself.

## Two spellings, one thing

`UiAction` has shorthands that build the same trigger. They are equivalent —
pick one and be consistent:

| Shorthand | Equivalent |
|---|---|
| `.dispatch("POST", url)` | `.onClick(UiTrigger.api("POST", url))` |
| `.dispatch("POST", url, "form-id")` | `.onClick(UiTrigger.api("POST", url, "form-id"))` |
| `.setHref(url)` | `.onClick(UiTrigger.go(url))` |
| `.download(url, hint)` | `.onClick(UiTrigger.download(url))` |
| `.openBlob(url)` | `.onClick(UiTrigger.openInTab(url))` |

## When a trigger does nothing

In order of likelihood:

1. **No event bus.** `renderer.mount(...)` alone renders; it doesn't wire
   anything. You need `new SuiEventBus(renderer, host)`.
2. **`INVOKE` with no handler registered** — the bus logs
   `no client handler registered for "<name>"`. Check the name matches
   `trigger.handler`.
3. **The response wasn't a `UiPage`/`UiPatch`** — a 200 with some other JSON is
   applied as nothing. Check the shape in the network tab.
4. **A patch's `targetId` doesn't exist** in the DOM — ids must match exactly.
   Note that nodes inside a table `cellTemplate` get a per-row id suffix.
5. Inspect the rendered element: the trigger travels as a `data-trigger='{…}'`
   attribute, so you can read exactly what the bus will do.
