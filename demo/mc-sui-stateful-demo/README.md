# mc-sui-stateful-demo

The shop's product admin, written as **stateful views**: one class per
screen, Java listeners on the nodes, no controller per action. The server
keeps the state and sends only what changed.

```bash
mvn -f demo/mc-sui-stateful-demo/pom.xml spring-boot:run   # http://localhost:9094
```

Data lives in memory and is re-seeded on every start.

## What to look at

- **`ProductsView`** — search, pagination, an edit dialog with validation,
  delete with confirm. Every button is an `on(node).click(e -> …)` next to
  the node it belongs to; the state is a small `State` class the framework
  keeps between requests as JSON.
- **`CounterView`** — the smallest possible view. Reload the page and the
  count survives; open the address in a second tab and it gets its own
  instance.
- **SSR ↔ SPA** — the page starts as plain server-rendered HTML with native
  forms; *Switch to SPA* sets a cookie that adds the core's bootstrap script,
  and from then on the same listeners run over `fetch` with `UiPatch`
  responses. Same views, same server code.

Open the browser's network tab in SPA mode: a click on *+1* is a `POST` to
`/sui/s/<instance>/inc/click` and a response of one `MERGE` naming the text
that changed.
