---
title: Build an app (SPA)
sidebar_position: 5
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Build a full app with the SuiRenderer

This walks through a minimal but complete single-page app: a static
`index.html` shell, an `app.js` that wires the `SuiRenderer` to the
`SuiEventBus`, and a Spring Boot controller that returns `UiPage` JSON.

The shell is **static** — it never changes. All screens come from the server
as `UiPage` trees; the renderer paints them and the event bus handles clicks,
form submits, navigation and history.

## 1. The backend — return `UiPage` JSON

Any endpoint that returns the `UiPage` shape works — the SPA client only ever
sees JSON, so the backend language is your choice:

<Tabs groupId="stack">
<TabItem value="spring" label="Java / Spring Boot">

```java
@GetMapping(path = "/admin/products", produces = "application/json")
public UiPage list(@RequestParam(defaultValue = "") String q) {
    var table = UiTable.of("products-table", "Products")
            .column(UiColumn.text("sku", "SKU"))
            .column(UiColumn.text("name", "Name"))
            .rowAction(UiAction.danger("delete", "Delete")
                    .confirm("Delete this product?")
                    .dispatch("DELETE", "/admin/products/{id}"));

    productService.findAll(q).forEach(p -> table.row(Map.of(
            "id", p.getId().toString(), "sku", p.getSku(), "name", p.getName())));

    return UiPage.of("/admin/products", table);
}
```

</TabItem>
<TabItem value="node" label="Node.js">

```js
// Express: the same UiPage tree as JSON — no semantic-ui code on the server.
app.get("/admin/products", async (req, res) => {
  const rows = await db.findProducts(req.query.q);
  res.json({
    type: "page",
    navigate: "/admin/products",
    node: {
      type: "table", id: "products-table", title: "Products",
      columns: [
        { type: "column", id: "c-sku",  label: "SKU",  dataKey: "sku"  },
        { type: "column", id: "c-name", label: "Name", dataKey: "name" }
      ],
      rows: rows.map(r => ({ type: "row", id: r.id, data: r })),
      rowActions: [
        { type: "action", id: "delete", label: "Delete", style: "DANGER",
          confirm: "Delete this product?",
          onClick: { url: "/admin/products/{id}", method: "DELETE" } }
      ]
    }
  });
});
```

</TabItem>
</Tabs>

The `UiPage`'s `navigate` value (`/admin/products`) is the URL the event bus
uses for history and deep-linking. (SSR to HTML is JVM-only — a Node backend
serves the SPA path; see the [Node.js quickstart](./quickstart-node.md).)

## 2. The shell — `index.html`

A plain HTML file. It loads the stylesheet, provides one host element, and
loads the app entry script. Nothing app-specific lives here.

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>My admin</title>
  <link rel="stylesheet" href="/sui/sui.css">
</head>
<body>
  <main id="app"></main>
  <script type="module" src="/app.js"></script>
</body>
</html>
```

`/sui/sui.css` (and the renderer bundle) are served by the
`mc-semantic-ui-core` auto-configuration — you don't ship them yourself.

:::tip No backend serving /sui/*?
If your `index.html` is *not* hosted by a Spring Boot app with
`mc-semantic-ui-core` on the classpath, load the runtime from the public
docs site instead — no build step, no npm:

```html
<link rel="stylesheet"
      href="https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/sui.css">
```

```js
import { SuiRenderer, installDefaultHandlers }
  from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/renderer.js";
import { SuiEventBus }
  from "https://cdn.jsdelivr.net/gh/mindconnect-ai/mc-docs@sui-v0.1.0/sui/eventbus.js";
```

Pinned tags are immutable; `https://mindconnect-ai.github.io/mc-docs/sui/…`
always tracks the latest `main`. URL shapes, versioning and a full example:
[CDN assets for HTML clients](./cdn-assets.md).
:::

## 3. The entry script — `app.js`

Create the renderer bound to the host element, attach the event bus, and load
the first screen:

```js
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";

const host = document.getElementById("app");

// Renderer paints UiNode trees into the host element.
const renderer = installDefaultHandlers(new SuiRenderer(host));

// Event bus wires clicks, form submits, navigation and browser history.
const bus = new SuiEventBus(renderer, host);

// Boot from the URL in the address bar so deep links survive a reload, and
// wire the popstate listener for back/forward. start() = navigate + history.
const path = window.location.pathname + window.location.search;
bus.start(path === "/" ? "/admin/products" : path);
```

That's the whole client. From here on:

- A click on a `data-trigger` element (button, link, row action) is caught by
  the bus, which fetches the new `UiPage` (or `UiPatch`) and applies it.
- A `<form>` submit is intercepted and dispatched with the right verb.
- `bus.navigate(href)` updates the URL and history; the back button works.

## Bookmarkable deep links (SPA + JSON on one path)

A subtlety appears once your controller serves `UiPage` JSON **and** users
bookmark deep links. Both share one URL — e.g. `/admin/products/42`:

- The **browser** loading that URL must get the SPA shell (`index.html`), so the
  bus can boot and fetch the page.
- The **bus** fetching that same URL must get the **JSON** `UiPage`.

The bus already distinguishes itself: every fetch it makes carries
`Accept: application/json`. A direct browser navigation carries
`Accept: text/html`. So the rule is simply: *HTML wants the shell, JSON wants
the data.*

Letting Spring decide this via `produces` does **not** work reliably — a
specific controller path (`/{id}`) wins over a generic SPA wildcard before
content negotiation runs, so the browser gets raw JSON. Intercept earlier, in a
servlet filter, and forward browser navigations to the shell:

```java
@Component
@Order(0)
public class SpaForwardingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        if (isBrowserNavigation(req)) {
            req.getRequestDispatcher("/index.html").forward(req, res);
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean isBrowserNavigation(HttpServletRequest req) {
        if (!"GET".equals(req.getMethod())) return false;

        String path = req.getRequestURI();
        if (!path.equals("/admin") && !path.startsWith("/admin/")) return false;

        // The bus fetches with Accept: application/json — let those through to
        // the controller. Only HTML navigations get the shell.
        String accept = req.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }
}
```

This keeps one clean, bookmarkable path per screen (`/admin/products/42`, no
`/api` prefix, no client-side URL rewriting): the filter hands browsers the
shell, and the bus's JSON fetches flow straight to your `@RestController`.
Static assets (`/sui/*`, your CSS) and POSTs are untouched.

## 4. Authenticated apps (BFF / OIDC)

If your backend uses the Spring Security BFF pattern (server-held session,
HttpOnly cookie, CSRF token), swap the fetcher so every request carries the
session cookie and CSRF header, and redirect to login on `401`:

```js
import { bffFetch, redirectToLogin } from "/sui/bff.js";

const bus = new SuiEventBus(renderer, host)
    .setFetcher(bffFetch)
    .setOnUnauthenticated(() => redirectToLogin("/oauth2/authorization/keycloak"));
```

No other change — the rest of the app is identical.

## Forms, structure &amp; validation

Fields, multi-column / tabbed / grouped layout, and server-driven validation
have their own page: **[Forms &amp; validation](./forms.md)**. In short — a
form submits every named control inside its `<form>` as one payload, so you can
lay fields out freely (`UiForm.content`, `UiFieldGroup`, `UiSection` tabs) and
still submit the whole thing; on an invalid submit, re-render the form as a
`UiPatch` with `UiField.error(...)` / `UiForm.error(...)` set.

## Embed as an island instead

If you don't want a full SPA shell, you can drop a single `<div>` into any
existing page and mount one tree — see
[Embed as a UI island](./ui-island.md).
