# mc-sui-shop-client-demo

A **backend-free** shop built entirely from Semantic UI `UiNode` literals —
with no server, no `fetch`, and no build step for the app itself.

The point it makes: a Semantic UI screen doesn't need a backend to be
interactive. It showcases the three trigger styles that run purely in the
browser:

- **`INVOKE`** — the trigger names a local JS function registered on the bus
  (`bus.registerClientHandler`). The function reads/mutates local state and
  returns a `UiPage` / `UiPatch`. Used for search, the detail dialog, delete
  and reset.
- **`PATCH`** — the trigger carries a `UiPatch` *inline* (`trigger.patch`). No
  function at all: the patch is baked in at render time and the bus just
  applies it. Used for the catalog list → detail-pane fill (zero round-trip,
  zero handler code).
- **field `onChange`** — a field-level trigger fired on value change. Used for
  the delivery-address checkbox that enables/disables another field.
- **`UiUpload`** — a drag-and-drop drop zone whose `onUpload` INVOKE handler
  reads the dropped `File` via `ctx.files` and previews the image locally (an
  object URL rendered by a small custom `image` node), with no upload.

## How it works

Every interactive element carries a trigger like:

```js
onClick: { behavior: "INVOKE", handler: "products.detail", url: p.id }
```

On the client you register a handler by that name:

```js
bus.registerClientHandler("products.detail", (ctx) => {
    const p = byId(ctx.trigger.url);
    return { patches: [], dialog: detailDialog(p) };   // a UiPatch
});
```

When the trigger fires, the `SuiEventBus` calls your function (passing the same
`BehaviorContext` a server call would get — including any collected form
payload) and applies the returned `UiPage` / `UiPatch` through its normal
response path: full mount, in-place patch, dialog open/close, toasts. The
handler is the browser-local equivalent of a REST endpoint.

Data lives in an in-memory array seeded on first load and persisted to
`localStorage`, so deletes and resets survive a page reload.

### `INVOKE` handlers in [`app.js`](src/main/static/app.js)

| Handler            | Trigger source          | Returns                                     |
|--------------------|-------------------------|---------------------------------------------|
| `products.search`  | Search form / button    | `UiPatch` — REPLACE table + catalog list    |
| `products.detail`  | SKU link / row action   | `UiPatch` — open the detail `dialog`        |
| `products.delete`  | Button in the dialog    | `UiPatch` — REPLACE lists + `closeDialog`   |
| `products.reset`   | Header button           | `UiPatch` — REPLACE the whole shop          |
| `delivery.toggle`  | Checkbox `onChange`     | `UiPatch` — REPLACE a field (enable/disable)|

### Inline `PATCH` — no handler at all

The "Katalog" list needs no handler. Each item is built in JS, so its finished
patch is baked straight into the trigger:

```js
onClick: {
    behavior: "PATCH",
    patch: { patches: [{ op: "REPLACE", targetId: "catalog-detail", node: detail(p) }] },
}
```

Clicking the item just applies that patch — the detail pane fills with zero
round-trip. Use this whenever the update is known ahead of time (list → detail,
open a fixed dialog, reveal a section). Use `INVOKE` when the update depends on
runtime state the server/handler must compute.

> Note: inline `PATCH` is baked per item, so build such lists item-by-item
> (a `UiList`) rather than via a shared table `cellTemplate` — templates apply
> a per-row id suffix that would rename a stable REPLACE target.

## Run it

It's a static site — the Maven build assembles a self-contained folder under
`target/dist` (the compiled core renderer + stylesheets unpacked from
`mc-semantic-ui-core`, plus `index.html` and `app.js`):

```bash
# from the repo root, build core first so the renderer/eventbus are current
mvn -f semantic-ui/core/mc-semantic-ui-core/pom.xml install -DskipTests
mvn -f semantic-ui/demo/mc-sui-shop-client-demo/pom.xml generate-resources

# then serve the assembled site (any static server works)
cd semantic-ui/demo/mc-sui-shop-client-demo/target/dist
python3 -m http.server 8085
# open http://localhost:8085
```

There is no application server and no database. Everything you see is a plain
`UiNode` object rendered by the core `SuiRenderer`, driven by client-side
`INVOKE` handlers.
