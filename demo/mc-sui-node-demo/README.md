# mc-sui-node-demo

A **pure Node.js / Express** demo for semantic-ui — a simplified version of the
shop demo showing just the product list. It proves the point that semantic-ui's
wire format is plain JSON: there is **no semantic-ui code on the server**, and
no Java. The server only builds and returns the `UiPage` tree as JSON; the
browser-side `SuiRenderer` paints it.

## What it shows

- A product list with search and a per-row **Delete** action.
- Clicking a SKU opens a product **detail dialog** (`GET /products/:id`
  returns a `UiPage` with a `dialog`).
- The exact same JSON shape a Spring Boot controller would return.
- The unchanged client (`SuiRenderer` + `SuiEventBus`) from the core module,
  driving an Express backend.

## Run it

The client assets (the compiled TypeScript renderer + CSS) live in the core
module. Build them once, then start the demo:

```bash
# 1. Compile the core module's TypeScript (one-time, from the repo)
cd semantic-ui/core/mc-semantic-ui-core
npm install && npm run build

# 2. Start the Node demo
cd ../demo/mc-sui-node-demo
npm install
npm start
# then open http://localhost:3000
```

`npm start` copies the client assets into `public/sui/` (via
`scripts/copy-client.js`) and starts Express on port 3000.

## How it works

- `server.js` — Express. Holds products in memory (no Postgres) and returns
  `GET /products` and `DELETE /products/:id` as `UiPage` JSON.
- `public/index.html` — the static shell: loads `/sui/sui.css`, mounts a
  `SuiRenderer` + `SuiEventBus` on a `<div>`, and calls `bus.navigate("/products")`.
- `public/sui/` — the renderer and CSS, copied from `mc-semantic-ui-core`.

The Java shop demo (`demo/mc-sui-shop-demo`) is the full version with Postgres,
SSR, themes and CRUD. This one is the minimal cross-language counterpart.
