---
title: 'Quickstart: Java / Spring Boot'
---

# Quickstart — Java / Spring Boot

You build the `UiPage` tree in a controller and return it. The **same method**
serves two ways, chosen by content negotiation:

- `Accept: text/html` → no-JS **server HTML** (SSR, via Handlebars on the JVM)
- `Accept: application/json` → JSON for the **SPA client**

No template files, no frontend build.

## 1. Add the dependency

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

The core module serves the browser runtime (`/sui/renderer.js`, `/sui/sui.css`,
…) from the jar — you ship neither yourself.

:::note Snapshots
Releases are on Maven Central, so the dependency above resolves with no extra
configuration. Snapshots are not published anywhere — to try an unreleased
state, clone this repo and `mvn install -DskipTests`.
:::

## 2. Turn SSR on

Server-side rendering is **opt-in** — the auto-configuration is gated on a flag,
so without it a browser hitting your endpoint gets JSON (or a `406`), not HTML:

```yaml
# application.yml
mindconnect:
  sui:
    ssr:
      enabled: true   # registers the UiPage → HTML message converter
```

Skip this only if you're building a JSON-only backend for the SPA client.

## 3. Return a `UiPage` from a controller

```java
@GetMapping(path = "/products", produces = { "text/html", "application/json" })
public UiPage products(@RequestParam(defaultValue = "") String q) {
    var table = UiTable.of("products", "Products")
            .column(UiColumn.text("sku",  "SKU"))
            .column(UiColumn.text("name", "Name"))
            .rowAction(UiAction.danger("delete", "Delete")
                    .confirm("Delete this product?")
                    .dispatch("DELETE", "/products/{id}"));

    productService.findAll(q).forEach(p -> table.row(Map.of(
            "id", p.getId().toString(), "sku", p.getSku(), "name", p.getName())));

    return UiPage.of("/products", table);
}
```

That's a working, styled, responsive table with a confirmed row action.

## 4. Pick a mode

- **No-JS / SSR** — with the flag from step 2 set, a browser hitting `/products`
  gets a finished HTML page; the `<form>` and links work without JavaScript. See
  **[Server-side rendering](./server-side-rendering.md)**.
- **Live SPA** — add a static `index.html` shell + a tiny `app.js` that wires the
  `SuiRenderer` to the `SuiEventBus`. The full walkthrough (shell, routing,
  deep links, auth) is **[Build an app](./building-an-app.md)**.

## Next

- The building blocks: **[Node vocabulary](./node-vocabulary.md)**,
  **[Triggers & actions](./triggers.md)**, **[Forms & validation](./forms.md)**.
- A full CRUD example end to end: **[Shop demo](./shop-demo.md)** (Postgres) and
  the **[File explorer demo](./file-explorer-demo.md)**.
