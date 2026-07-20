# semantic-ui — Server-Side Rendering (SSR) via Handlebars

> Implementation note, kept in sync with the codebase. Companion to
> [`concept.md`](concept.md) — the *why*; this document is the *how*.

## Motivation

The original renderer was the TypeScript `SuiRenderer` running in the
browser: server emits `UiNode` JSON, client turns it into DOM. That's
the SPA path — it needs JavaScript, one JSON round-trip, and the
EventBus loaded before anything is visible.

There are cases where that's the wrong trade-off:

- **First paint without a JS round-trip** — the initial page should
  arrive as plain HTML.
- **JS-free clients / progressive enhancement** — read-only views
  (detail pages, lists, audit traces, memory inspectors) should work
  without JavaScript.
- **Editable markup** — building HTML from Java `StringBuilder` chains
  or template literals isn't fun. A real templating language is much
  easier to change.

The thesis stays the same: **there is exactly one `UiNode` tree.** Only
its serialisation differs — JSON for the SPA, HTML for SSR. Controllers
and page-builder classes keep producing the same tree and don't know
which serialisation will be used.

## Two modes side by side

SSR doesn't replace the SPA — both exist in parallel and are chosen
**per request**:

| Mode | First paint            | Interaction              | JS needed | Streaming (chat) |
|------|------------------------|--------------------------|-----------|------------------|
| SPA  | JSON → TS renderer     | In-place patches         | yes       | yes              |
| SSR  | Handlebars → HTML      | Full page from server    | no        | no               |

Some routes lend themselves to SSR (read-only detail and list views);
others need the SPA (chat with SSE streaming). The choice is per route,
not global.

## Mode selection: content negotiation via `Accept`

The same controller endpoint serves both modes. The `Accept` header
decides:

```
Accept: text/html          → Handlebars HTML  (browser navigation, JS-free)
Accept: application/json    → UiPage JSON     (EventBus fetch, SPA)
```

This is REST-conformant, keeps URLs clean (no `?render=` flag, no
duplicated endpoints), and aligns with the existing security config
that distinguishes browser navigations from XHR calls.

- A **direct browser GET** to `/admin/products` sends
  `Accept: text/html` → SSR.
- The **EventBus** sets `Accept: application/json` on its `fetch`
  calls → JSON / SPA.

Same page rendered server- or client-side depending on the entry
point, with no controller knowledge.

## Where the markup lives: one Handlebars template per UiNode type

Each `UiNode` type owns a single `.hbs` file. The server compiles it
with `handlebars.java`; the browser-side client renderer (future) would
load the same file via `/sui/templates`. **Single source of truth for
markup.**

```
src/main/resources/templates/sui/
  page.hbs           ← UiPage wrapper (navigate + node)
  form.hbs           ← UiForm
  field.hbs          ← UiField (one input, dispatched by fieldType)
  action.hbs         ← UiAction → SsrTriggerMapper
  link.hbs           ← UiLink
  detail.hbs         ← UiDetail
  list.hbs           ← UiList
  list-item.hbs      ← list-item partial
  table.hbs          ← UiTable (columns + rows + selection + pagination)
  column.hbs         ← UiColumn (standalone preview for the editor)
  row.hbs            ← UiRow (standalone preview for the editor)
  section.hbs        ← UiSection (tabbed container)
  section-entry.hbs  ← UiSectionEntry
  section/tabs.hbs   ← tab strip + panels partial
  stack.hbs          ← UiStack
  header.hbs         ← UiHeader (brand + extras + user)
  chart.hbs          ← UiChart
  text.hbs           ← UiText
  pagination.hbs     ← shared pagination partial
```

These templates are the **SSR counterpart** to the per-type render
functions in `core/mc-semantic-ui-core/src/main/ts/renderers/`. They emit
**the same markup with the same CSS classes**, so `sui.css` applies to
both renderers.

## Why Handlebars (and not Thymeleaf, Mustache, EJS, …)

Three constraints had to be met simultaneously:

1. **Templates can be shipped from the backend** to a future browser
   renderer — single source of truth for markup.
2. **Markup logic must be expressible** without resorting to a full
   programming language (otherwise the template can't be portable).
3. **JVM and browser implementations must be production-grade**, not
   side projects.

Handlebars meets all three. `handlebars.java` (jknack) is mature; the
official `handlebars.js` is the de facto JS implementation. The
**helper concept** absorbs computation (`{{ceil}}`, `{{add}}`, `{{eq}}`)
and transformation (`{{rowAction}}`, `{{cell}}`, `{{trigger}}`,
`{{render}}`) cleanly — these helpers are registered once per language
(Java + future JS), independent of node-type count.

Alternatives:

- **Thymeleaf / JTE / Pebble** — JVM-only; can't serve the same
  template to the browser.
- **Mustache** — strictly logic-less; *all* computation has to be
  pre-computed into a view model. That view model would be needed in
  both languages too, but it grows with each node type rather than
  staying a small, fixed helper set.
- **EJS** — needs a JS engine on the JVM (GraalVM) to share templates.
  Defeats the build-simplicity goal.
- **Liquid** — viable but less industry-standard, smaller ecosystem.

The trade-off Handlebars asks for is *discipline*: helpers are the only
place where new logic may live. New node types add a template + an
optional helper, not template-side code blocks. This is enforced in
code review.

## Default in the core JAR, override per app

Templates are looked up against a two-step resolver chain (highest
priority first):

1. `classpath:/templates/sui/` in the **app** — app-specific override
2. `classpath:/templates/sui/` in the **core JAR** — bundled default

`SuiServerRenderer` caches compiled templates per name; a missing
template falls through to the next loader transparently. An app that
only wants a different `table.hbs` ships that one file and inherits
everything else from the core JAR.

## Hybrid HTML: same markup serves SSR and SPA

The SSR templates don't emit "SSR-only" markup. They emit **hybrid
HTML** — every interactive element carries both:

- A native HTML mechanism (an `<a href>` for navigation, a
  `<form method="post">` with `_method` tunneling for DELETE/PUT, a
  hidden submit button for forms).
- A `data-trigger='{…JSON…}'` attribute the `SuiEventBus` reads when
  the SPA bootstrap script is loaded.

| `UiTrigger`                       | SSR HTML                                                                                |
|-----------------------------------|-----------------------------------------------------------------------------------------|
| `go(href)` / GET, no payload      | `<a href="{href}" data-trigger='…'>`                                                    |
| `api("POST", url)`                | `<form method="post" action="{url}" data-trigger='…'>` + submit button                  |
| `api("DELETE" \| "PUT", url)`     | `<form method="post">` + `<input type="hidden" name="_method" value="DELETE">`          |
| `download(url)` / `openInTab(url)`| `<a href="{url}" target="_blank">` (browser handles `Content-Disposition` natively)     |
| `stream(…)` (SSE)                 | no JS-free equivalent — streaming pages stay SPA-only                                   |

This is the central trick: **the same page upgrades from SSR to SPA by
just including the bootstrap script**, with no markup change. The
browser uses the native form / anchor when no JS is loaded; the
EventBus intercepts the `data-trigger` once it boots.

`DELETE` / `PUT` over HTML forms (which natively support only GET /
POST) rely on Spring's `HiddenHttpMethodFilter`. The SSR
auto-configuration registers it.

`STREAM` triggers have no JS-free equivalent — Server-Sent Events
require a client. Streaming pages stay SPA-only. That's acceptable
because not every page needs SSR.

## Form bodies: JSON vs. form-urlencoded

The SPA collects form values via the EventBus and posts them as JSON
(`Content-Type: application/json`). In SSR mode the browser collects
the fields natively on `<form>` submit and posts them as
`application/x-www-form-urlencoded`.

That means controller endpoints expecting `@RequestBody Map<…>` need to
accept form-urlencoded too if they want SSR write support. A small
helper (`FormBody`) reads both content types into the same map.

The first iteration sidestepped the problem on purpose: only read-only
views (lists, details, traces) were SSR-enabled. Write-side endpoints
have since been extended to accept both content types — see
`AdminProductController` in the shop demo.

## Components (current implementation)

```
core/mc-semantic-ui-core/
  pom.xml                              ← handlebars.java declared <optional>true
  src/main/resources/templates/sui/    ← default .hbs templates
  src/main/java/.../ssr/
    SuiServerRenderer.java             ← UiNode/UiPage → HTML (dispatches by type)
    SsrTriggerMapper.java              ← UiTrigger → hybrid HTML (form + data-trigger)
    SuiHandlebarsHelpers.java          ← registers eq / includes / ceil / divide / …,
                                         {{render child}}, {{action}}, {{rowAction}},
                                         {{cell}}, {{trigger}}, {{selectionInput}}, …
    UiPageHtmlMessageConverter.java    ← Spring HTTP message converter:
                                         Accept: text/html → renderer.renderPage(page)
    SuiSsrAutoConfiguration.java       ← wires the converter, the
                                         HiddenHttpMethodFilter, and the static
                                         /sui/* asset handlers (gated by
                                         mindconnect.sui.ssr.enabled=true)
```

- **`SuiServerRenderer`** only works when `handlebars.java` is on the
  classpath. Pure-SPA apps don't pull Handlebars in (optional
  dependency) and see no SSR.
- **`UiPageHtmlMessageConverter`** is the only Spring integration
  surface: controllers keep returning `UiPage`, the converter picks
  HTML vs. JSON based on `Accept`.
- **Helper library** stays small and is unit-tested for behavioural
  parity with the TS renderer's logic — same outputs for the same
  inputs.

## EventBus adaptation

The EventBus sets `Accept: application/json` on its `fetch` calls so
content negotiation hands it JSON instead of HTML — without this, the
bus would try to apply an SSR HTML page as a patch payload. The header
is set centrally in the bus's behavior handlers, not per call site.

## Risks and conscious limits

- **Discipline cost** — helpers are the only place logic may live.
  Template authors must resist adding inline computation. Enforced in
  code review; in practice the helper set has stayed small.
- **Helper duplication (future)** — once the browser-side
  Handlebars renderer ships, the helper set will live in both Java and
  TypeScript. The mitigation is parameterised tests in both languages
  feeding the same inputs and asserting equal outputs.
- **Streaming is SSR-incompatible** — chat / live updates stay
  SPA-only.
- **No hydration** — by design. SSR and SPA are alternative modes,
  not "SSR then hydrate over it". An SSR page stays SSR (full reload
  on interaction); a SPA page starts as SPA. No hydration mismatch
  class of bugs because there is no hydration.

