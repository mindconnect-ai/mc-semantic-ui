---
title: Shop demo
sidebar_position: 11
---

# End-to-end demo: the shop

`demo/mc-sui-shop-demo` is a full Spring Boot CRUD application against Postgres —
the most complete worked example of semantic-ui in action.

## Run it

```bash
cd semantic-ui/demo/mc-sui-shop-demo
cp .env.docker.example .env.docker
./start-postgres.sh
mvn spring-boot:run
# then open http://localhost:8080
```

## What it demonstrates

- Product list with **search + pagination + per-row actions**.
- View / edit / delete via Post/Redirect/Get.
- **SSR mode** (no JS) and **SPA mode** (load the bootstrap script), switchable
  from a header dropdown — the same backend code serves both.
- Three themes (light, dark, SBB), swappable via the same dropdown.
- A toast after every save, a dialog for inline-edit, and a customer tab
  showcasing the tab section.

Everything above comes from one set of controllers returning `UiPage` trees —
there is no separate SPA codebase.

## Tests as a spec

The smoke tests in `PageRenderSmokeTest` lock the markup behaviour of every
interaction, so the demo doubles as an executable specification of how the
renderers should behave.

## Client-only variant

`demo/mc-sui-shop-client-demo` is the mirror image: the **same shop shape with
no backend at all**. It is a static site — the build assembles a self-contained
folder (the compiled core renderer + stylesheets, plus an `index.html` and an
`app.js` of plain `UiNode` literals) that you can serve from any static host.

```bash
mvn -f semantic-ui/core/mc-semantic-ui-core/pom.xml install -DskipTests
mvn -f semantic-ui/demo/mc-sui-shop-client-demo/pom.xml process-resources
cd semantic-ui/demo/mc-sui-shop-client-demo/target/dist
python3 -m http.server 8085   # then open http://localhost:8085
```

Every interaction is a browser-only [trigger](./triggers.md): products live in
an in-memory array (persisted to `localStorage`), and there is no `fetch`.

- **`INVOKE`** — search, the product detail dialog, delete and reset call
  JS handlers registered with `bus.registerClientHandler(...)`, each returning
  a `UiPatch` (or `UiPage`) the bus applies just like a server response.
- **`PATCH`** — the "catalog" list fills a detail pane with **zero round-trip**:
  each item carries its finished `UiPatch` inline in the trigger, so clicking it
  needs no handler and no server.
- **field `onChange`** — a checkbox enables/disables the delivery-address field
  by firing an `INVOKE` trigger that reads its state and patches the field.
- **`UiUpload`** — a drag-and-drop drop zone whose `onUpload` is an `INVOKE`
  trigger: the dropped image's `File` is read via `ctx.files` and previewed with
  an object URL, again with no server.

It is the same node vocabulary and the same renderer as the Spring version —
proof that the tree is the only contract, and the backend is optional.
