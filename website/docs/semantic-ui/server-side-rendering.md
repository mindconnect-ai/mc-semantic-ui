---
title: Server-side rendering
sidebar_position: 9
---

# Server-side rendering (SSR)

SSR renders the `UiNode` tree to **HTML on the JVM** instead of shipping JSON to
a browser renderer. The browser gets a finished page — no JavaScript round-trip,
forms and links work natively.

The thesis is unchanged: there is exactly one `UiNode` tree. Only its
serialisation differs — JSON for the SPA, HTML for SSR. Controllers produce the
same tree and don't know which one will be used.

## When SSR is the right trade-off

- **First paint without a JS round-trip** — the initial page arrives as plain
  HTML.
- **JS-free clients / progressive enhancement** — read-only views (detail pages,
  lists, audit traces) work without JavaScript.
- **Editable markup** — a real templating language is easier to change than
  Java `StringBuilder` chains.

SSR doesn't replace the SPA; both exist in parallel and are chosen **per
request**.

| Mode | First paint        | Interaction          | JS needed |
|------|--------------------|----------------------|-----------|
| SPA  | JSON → TS renderer | in-place patches     | yes       |
| SSR  | Handlebars → HTML  | full page from server | no        |

## Enable it

Add `mc-semantic-ui-core` and turn SSR on:

```yaml
mindconnect:
  sui:
    ssr:
      enabled: true   # registers the UiPage <-> HTML message converter
```

`SuiSsrAutoConfiguration` then wires:

- a `SuiServerRenderer` bean (Handlebars-based),
- a `UiPageHtmlMessageConverter` so controllers can just return `UiPage`,
- Spring's `HiddenHttpMethodFilter` so HTML forms can issue DELETE / PUT / PATCH
  via `_method` tunnelling,
- resource handlers for `/sui/*` (the CSS and JS bundle).

Now any `@GetMapping` that returns `UiPage` renders as a full HTML page in the
browser.

## Mode selection: content negotiation

The same endpoint serves both modes; the `Accept` header decides:

```
Accept: text/html        → Handlebars HTML  (browser navigation, JS-free)
Accept: application/json  → UiPage JSON     (SuiEventBus fetch, SPA)
```

A direct browser `GET` sends `Accept: text/html` → SSR. The event bus sets
`Accept: application/json` on its fetches → JSON / SPA. No `?render=` flag, no
duplicated endpoints. Produce both from one controller method:

```java
@GetMapping(path = "/admin/products", produces = "application/json,text/html")
public UiPage list(...) { /* build and return the UiPage tree */ }
```

### Reloading a deep URL

This is where SSR quietly earns its keep. Press F5 on `/admin/products/42` and
the browser makes a *document* request — no JavaScript involved yet:

| Setup | What the browser gets |
|---|---|
| **SSR enabled** | a complete HTML page from the same controller. **Nothing to configure.** |
| **SSR disabled** (SPA-only) | **HTTP 500 — "No converter for UiPage"**: the JSON converter can't answer `Accept: text/html`. |

So a deep link works out of the box *because* the endpoint answers both. There is
no SPA fallback filter to install, no `forward:/index.html` — the controller that
serves the SPA its JSON is the same one that serves the browser its HTML.

:::warning If you run SPA-only on Spring Boot
Leave SSR off and a reload on any deep URL is a 500. The endpoint has nothing to
say to a document request, and Spring turns that into an error page.

Two ways out:

**Turn SSR on.** One property, and the problem disappears — you also get the
no-JS rendering for free.

**Or forward document requests to your shell**, the classic SPA fallback:

```java
@Controller
class SpaFallbackController {
    // Any GET that asks for HTML and isn't a static file or an API path is
    // answered with the SPA shell; the bus then routes client-side from the
    // URL. Keep the pattern tight so it never swallows real endpoints.
    @GetMapping(value = "/{path:^(?!api|sui|assets).*}/**", produces = MediaType.TEXT_HTML_VALUE)
    public String shell() {
        return "forward:/index.html";
    }
}
```

The second option is what other SPA stacks require of you. The first is why
semantic-ui usually doesn't.
:::

## Where the markup lives

Each `UiNode` type owns a single Handlebars template under
`templates/sui/{type}.hbs` — `page.hbs`, `form.hbs`, `table.hbs`, `field.hbs`,
and so on. These are the SSR counterpart to the per-type TypeScript render
functions and emit **the same markup with the same CSS classes**, so `sui.css`
applies to both renderers.

### Override per app

Templates resolve against a two-step chain (highest priority first):

1. `classpath:/templates/sui/` in **your app** — app-specific override,
2. `classpath:/templates/sui/` in the **core JAR** — bundled default.

An app that only wants a different `table.hbs` ships that one file and inherits
everything else from the core.

## Hybrid HTML: one markup for both modes

SSR templates don't emit "SSR-only" markup — they emit **hybrid HTML**. Every
interactive element carries both a native mechanism **and** a `data-trigger`
attribute:

- An `<a href>` for navigation, or a `<form method="post">` with `_method`
  tunnelling for DELETE / PUT — works with no JavaScript.
- A `data-trigger='{…}'` attribute the `SuiEventBus` reads once the SPA
  bootstrap script is loaded.

So a page **upgrades from SSR to SPA just by including the bootstrap script** —
no controller and no template change. Start server-rendered and JS-free; add
interactivity later by loading the script.
