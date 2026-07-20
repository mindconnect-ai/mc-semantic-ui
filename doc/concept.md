# semantic-ui — Concept

> Living document. Reflects the state of the codebase, not a roadmap.

## Motivation

Frontend development has reached a mental load that's out of proportion
for most business-software UIs. A productive backend engineer is
expected to also master a build toolchain, framework reactivity model
(signals / hooks / observables), state management, routing, form
state, server-state cache, styling layer, component library,
validation, and testing pipeline. For a CRUD-shaped business app
that's massive overengineering.

`semantic-ui` addresses that pain with a single thesis:

> A structured business app's UI is fully describable as a small,
> typed JSON vocabulary. The server emits that tree, a thin renderer
> turns it into HTML. Design is orthogonal to logic and is decided
> centrally in the renderer.

## Sweet spot

`semantic-ui` is explicitly *opinionated* and *not* meant for every
use case.

### Strong fit

- Internal admin / back-office
- Business software proper (ERP, CRM, accounting, HR, order entry)
- Workflow UIs, application flows, public-sector / insurance UIs
- B2B-SaaS dashboards with tabular data
- Configuration surfaces (DevOps, infrastructure management)
- Knowledge-base admin UIs, LLM-agent-operable surfaces
- Embedded chat features inside business apps (ticket comments,
  workflow discussions)
- Shop back-offices (product maintenance, order management)

### Out of scope on purpose

- Marketing sites, landing pages, consumer storefronts with strong
  design opinions
- Real-time editors (IDE, whiteboard, spreadsheet, photo / video
  editor)
- Highly interactive consumer real-time apps (Discord / Slack class,
  consumer chat)
- Trading frontends with high-frequency updates
- Games and anything that needs sub-16ms interactivity

## Design decisions

### 1. JSON schema instead of HTML as server output

Unlike HTMX and Datastar, the server doesn't emit HTML — it emits a
typed JSON vocabulary (`UiPage`, `UiForm`, `UiTable`, `UiDetail`,
`UiAction`, …).

**Advantages:**
- Compile-time safety in typed backend languages (Java, Kotlin, C#,
  Go)
- IDE support, refactoring, static type checking
- A small, finite vocabulary — friendly to LLM-driven UI generation
- The renderer is swappable (web, native mobile, terminal, print)
- Clean separation between "what the UI *is*" and "how it *looks*"

**Disadvantages:**
- Browsers can't render it natively → a dedicated renderer is needed
- A higher learning curve than "I already know HTML"

### 2. Server-driven tree, optional client-side reactivity

The UI tree is produced by the server and lives in the frontend.
Updates arrive as `UiPatch` (JSON-Patch-like) and are applied locally
to the tree.

Local reactions to browser events (scroll, resize, viewport, hover)
are possible but only through **declarative behaviours**, never
through freely written client-side code:

```json
{
  "type": "list",
  "behaviors": {
    "virtualize": true,
    "scrollAnchor": "first-visible",
    "loadMoreOnScroll": { "threshold": 0.8 }
  }
}
```

The LLM-readable, declarative character of the tree is preserved
without forcing latency-sensitive patterns through a server round
trip.

**Limit:** Behaviours are a curated, finite list. Anyone who wants
"I write my own JavaScript logic in the tree" should not pick
`semantic-ui`.

### 3. State can stay in the frontend — but doesn't have to

Unlike Vaadin Flow, `semantic-ui` doesn't force backend session state.
The tree lives in the browser, the server is stateless. As an option
the server can hold the tree for a session and ship only patches
(bandwidth, optimistic sync) — both are supported by the same
protocol.

### 4. Logic / design separation

The application developer describes the UI in domain terms
(`UiForm.of("user").field(...).action(...)`). Looks (spacing,
typography, colours, responsive breakpoints, light/dark) are decided
centrally by the renderer. Brand customisation flows through CSS
variables and theme configuration, not by editing every screen.

**Reality check:** This separation isn't absolute. Field order,
grouping, primary vs. secondary action all carry implicit design
decisions. What `semantic-ui` reduces is the *granularity* of the
design surface, not its full elimination. That fits admin UIs, not
designer-driven products.

### 5. Responsive design centralised in the renderer

Rather than teaching every component its own media queries, the
renderer decides once, centrally: a `UiTable` collapses to a `UiList`
below 768px, `UiSection` tabs become an accordion, action bars move
into a drawer menu. The backend developer doesn't have to think about
breakpoints.

### 6. Two renderers, one Handlebars source of truth

The server emits a typed `UiNode` tree (not HTML), so the renderer is
swappable. Two render paths exist today:

- **`SuiRenderer`** in TypeScript — runs in the browser, drives the
  SPA. One file per `UiNode` type under
  `core/mc-semantic-ui-core/src/main/ts/renderers/`.
- **`SuiServerRenderer`** in Java — runs on the server, drives SSR.
  One Handlebars template per `UiNode` type under
  `core/mc-semantic-ui-core/src/main/resources/templates/sui/`.

Mode selection happens via content negotiation: `Accept: text/html` →
`SuiServerRenderer`, `Accept: application/json` → `SuiRenderer`. Same
controller endpoint serves both. A future third renderer
(`SuiHandlebarsRenderer` running `handlebars.js` in the browser)
would load the same `.hbs` files from the backend and unify the
markup source across all three. See [`ssr.md`](ssr.md) for the SSR
design, deliberate limits (no streaming in SSR, form-body handling,
no hydration), and the helper-library trade-off.

## Comparison to existing solutions

|                       | React / Angular   | Vaadin Flow      | HTMX             | Datastar         | semantic-ui                       |
|-----------------------|-------------------|------------------|------------------|------------------|-----------------------------------|
| Server output         | JSON data         | Java components  | HTML             | HTML + signals   | typed JSON                        |
| Client state          | complex           | none (server)    | none             | signals          | minimal, declarative behaviours   |
| Backend language      | any (via API)     | Java             | any              | any              | any (JSON protocol)               |
| Renderer              | in framework      | in framework     | browser-native   | browser-native   | swappable (TS / Handlebars)       |
| Reactivity            | client-central    | server-central   | server round-trip| hybrid           | hybrid via behaviours             |
| LLM fit               | good for code     | weak             | good for HTML    | good for HTML    | very good for schema              |
| Learning curve        | very high         | medium           | low              | low              | low (backend), medium (renderer)  |

**Closest competitor:** Datastar hits the same sweet spot with
HTML output. `semantic-ui` differs by being schema-typed and by
making the renderer swappable — a narrow but real niche for
type-safe backend teams.

## Architecture

```
┌──────────────────────────────────────────────┐
│         Backend (Java / Spring Boot)         │
│  ┌────────────────────────────────────────┐  │
│  │  Domain logic / services / persistence │  │
│  └────────────────────┬───────────────────┘  │
│                       │                       │
│  ┌────────────────────▼───────────────────┐  │
│  │  UI assembler                          │  │
│  │  (builds UiPage / UiNode from domain)  │  │
│  └────────────┬─────────────────┬─────────┘  │
│               │ Accept:         │ Accept:    │
│               │ application/json│ text/html  │
│  ┌────────────▼────────┐  ┌─────▼─────────┐  │
│  │  JSON (Jackson)     │  │ SuiServerRen- │  │
│  │                     │  │ derer (HBS)   │  │
│  └────────────┬────────┘  └─────┬─────────┘  │
└───────────────┼─────────────────┼────────────┘
                │                  │
                │ HTTP / SSE / WS  │ HTTP (HTML)
                │                  │
┌───────────────▼─────────────┐    │
│   Browser — SPA mode        │    │
│  ┌───────────────────────┐  │    │
│  │  UiNode tree (in mem) │  │    │
│  └────────────┬──────────┘  │    │
│  ┌────────────▼──────────┐  │    │
│  │  SuiRenderer          │  │    │
│  │  pluggable handlers   │  │    │
│  └────────────┬──────────┘  │    │
│  ┌────────────▼──────────┐  │    │
│  │  SuiEventBus          │  │    │
│  │  click / submit /     │  │    │
│  │  navigate / behaviors │  │    │
│  └───────────────────────┘  │    │
└─────────────────────────────┘    │
                                   │
┌──────────────────────────────────▼────────────┐
│   Browser — SSR mode (no JS / first paint)    │
│   Native HTML from SuiServerRenderer. Forms,  │
│   anchors and _method tunnelling carry intent.│
│   Upgrades to SPA simply by loading the       │
│   bootstrap script — same markup is hybrid.   │
└───────────────────────────────────────────────┘
```

### Maven modules

- **`mc-semantic-ui-core`** — the mixed Java + TypeScript module.
  Ships the full vocabulary plus both renderers.
  - **Java model:** `UiPage`, `UiNode` and 16 subtypes (`UiForm`,
    `UiTable`, `UiList`, `UiDetail`, `UiSection`, `UiSectionEntry`,
    `UiStack`, `UiHeader`, `UiChart`, `UiAction`, `UiField`,
    `UiLink`, `UiText`, `UiColumn`, `UiRow`). Jackson-serialisable
    with `@JsonTypeInfo`-based polymorphism, fluent builder API.
  - **Java SSR:** `SuiServerRenderer` + `SsrTriggerMapper` +
    `SuiHandlebarsHelpers` — produce hybrid HTML from a `UiPage`,
    dispatched per type into `.hbs` templates.
  - **TypeScript SPA:** `SuiRenderer` with pluggable per-type
    handlers (`renderers/form.ts`, `renderers/table.ts`, …), mount
    lifecycle, `applyPatch`, loading-indicator overlay, Idiomorph-
    based DOM morphing that preserves focus / selection / CSS
    animations across patches. `SuiEventBus` handles
    click / submit delegation, tab switching, behaviours
    (`APPLY_RESPONSE`, `STREAM`, `DOWNLOAD`, `OPEN_IN_TAB`), and SPA
    navigation (`navigate`, `applyPage`, `start`, `popstate`).
    Optional `bff` sub-module (`bffFetch`, `csrfHeader`,
    `redirectToLogin`, `submitLogoutForm`, `loadUser`).
  - **CSS:** `sui.css` ships base styles via CSS variables;
    `sui-dark.css` and `sui-sbb.css` are theme overrides. A single
    `<link rel="stylesheet" href="/sui/sui.css">` gives full styling.
  - **Build:** `frontend-maven-plugin` runs `tsc` during the `compile`
    phase. TS output and CSS land in the JAR under
    `META-INF/resources/sui/` so any Spring Boot consumer automatically
    serves them at `/sui/*` — no extra configuration needed.

- **`ext/*`** — one module per node type the core cannot draw itself,
  either because its renderer depends on a third-party library or
  because it needs its own painter: `mc-semantic-ui-ext-json`
  (`UiJsonViewer`), `mc-semantic-ui-ext-markdown` (`UiMarkdown`),
  `mc-semantic-ui-ext-diagram`, `mc-semantic-ui-ext-chart`. One module
  per type rather than a grab-bag, so a host that wants Markdown does
  not also pull in a JSON viewer. Each extension ships:
  - Java node class with `@JsonTypeName(...)`
  - Jackson `SimpleModule` (registered via `ServiceLoader` for plain
    Java and via Spring Boot auto-configuration for Boot hosts) so
    the core `ObjectMapper` knows the subtype without core-side
    `@JsonSubTypes` editing
  - TypeScript handler with an `install(renderer)` function that
    lazy-loads its external dependency via dynamic
    `import("https://cdn.jsdelivr.net/...")` — no `<script>` tags
    needed in the host page
  - TS output and any extension-specific CSS land in the JAR under
    `META-INF/resources/sui-ext/`

  `mc-semantic-ui-ext-diagram` is the same pattern at full size — heavier
  dependencies, a server-side painter as well as a browser one, and its
  own release cadence.

- **`editor/mc-sui-editor`** — embeddable visual editor: tree,
  property panel (JSON view) and live preview, all wired against an
  `EditorContentStore` SPI. Spring Boot starter; add the dependency
  and `/editor` is live.

- **`editor/mc-sui-editor-app`** — standalone showcase for the
  editor with sample content covering every node type.

- **`demo/mc-sui-shop-demo`** — end-to-end demo: Postgres-backed
  product CRUD demonstrating SSR + SPA mode switching, theme picker,
  cell templates, multi-row selection, toasts, dialogs.

- **Backend SPI:** Spring Boot starter today. The JSON protocol is
  language-agnostic; ports to Node / Python / Go are explicit
  community contributions, not core maintenance.

### Frontend lifecycle (consumer view)

A complete host page looks like this — regardless of the backend
language:

```html
<!DOCTYPE html>
<html>
  <head>
    <link rel="stylesheet" href="/sui/sui.css">         <!-- from core JAR -->
    <link rel="stylesheet" href="/css/app.css">         <!-- optional, app-specific -->
  </head>
  <body>
    <div id="main"></div>
    <script type="module" src="/js/app.js"></script>
  </body>
</html>
```

```js
// app.js — boot sequence
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus }                         from "/sui/eventbus.js";
import { bffFetch, redirectToLogin }           from "/sui/bff.js";
import { install as installMarkdown }          from "/sui-ext/markdown/extension.js";

const main     = document.getElementById("main");
const renderer = installDefaultHandlers(new SuiRenderer(main));
await installMarkdown(renderer);

const bus = new SuiEventBus(renderer, main);
bus.setFetcher(bffFetch)
   .setUrlRewriter(uiToApiPath)
   .setOnUnauthenticated(() => redirectToLogin("/oauth2/authorization/keycloak"));

bus.start(window.location.pathname);
```

App-specific code is limited to URL-rewrite conventions and
authentication wiring.

### Two core classes — Renderer and EventBus

Two classes, clear division of labour:

- **`SuiRenderer`** — DOM output. Receives a tree, writes HTML.
  Knows `mount(node)`, `applyPatch(patch)` and the loading-indicator
  lifecycle. A pure-string output mode (`render(node) → string`) is
  available for tests and SSR fixtures — no browser required.
- **`SuiEventBus`** — anything DOM events, HTTP, navigation. Click
  and submit delegation on the same root element (and on the dialog
  host when an overlay is open). Dispatches triggers via built-in
  behaviours (`APPLY_RESPONSE`, `STREAM`, `DOWNLOAD`, `OPEN_IN_TAB`).
  Built-in navigation: `navigate(href)`, `applyPage(page,
  fallbackHref)`, `start(initialHref)`, popstate listener, optionally
  disabled via `setHistoryEnabled(false)`. Holds fetcher /
  URL-rewriter / response handler / 401 handler centrally.

The EventBus is optional: apps that don't need DOM event handling
(SSR-only, test runners without a DOM) use only the renderer.

The contract between the two is a vocabulary of `data-*` attributes
(`data-trigger`, `data-action`, `data-href`, `data-target`,
`data-sui="form"`, `data-confirm`, `data-selected`). That's the
public API boundary.

### Hybrid markup — same HTML drives SSR and SPA

Both renderers emit the same markup. Every interactive element
carries both:

- A native HTML mechanism (an `<a href>` for navigation, a
  `<form method="post">` with `_method` tunnelling for DELETE / PUT,
  a hidden submit button for forms).
- A `data-trigger='{…JSON…}'` attribute that the `SuiEventBus`
  reads when the SPA bootstrap script is loaded.

That means an SSR-served page **upgrades to SPA simply by loading
the bootstrap script** — no markup change, no hydration step. Native
form / anchor when no JS is loaded; the EventBus intercepts the
trigger once it boots.

### Pagination is not a special case

Pagination buttons are normal triggers. The server provides a
`Pagination.pageTrigger` template carrying `{page}` as a placeholder;
the renderer substitutes it at render time. A pagination click goes
through the same `APPLY_RESPONSE` path as every other click — no
dedicated `data-page` handler in the EventBus, no app-specific
pagination logic needed.

### One URL per page — method decides the response

A single URL serves the entire lifecycle of a page:

- **`GET /admin/products/42`** → returns a complete `UiPage`. Issued
  both by the SPA's `navigate(...)` and by a direct browser hit
  (reload, shared link).
- **`POST / PUT / PATCH / DELETE /admin/products/42`** → typically
  returns a `UiPatch` (or a full `UiPage` if the whole page re-renders).

The HTTP method is the natural disambiguator: `GET` is read-only and
idempotent — perfect for address-bar hits — and mutating methods
produce partial changes. The same path returns HTML or JSON depending
on the `Accept` header, so browsers and the EventBus reach the same
endpoint with no rewrite step.

### BFF helpers (optional)

`/sui/bff.js` is an optional sub-module for apps with the typical
Spring Security BFF setup: `csrfToken()`, `csrfHeader()`,
`bffFetch(url, init)` (= `fetch` with same-origin cookie + automatic
CSRF header), `redirectToLogin(loginUrl)` with anti-loop guard,
`submitLogoutForm()`, `loadUser(meUrl)`. Apps that don't use a BFF
don't import it — zero overhead.

Typical wiring:

```js
import { bffFetch, redirectToLogin } from "/sui/bff.js";
bus.setFetcher(bffFetch)
   .setOnUnauthenticated(() => redirectToLogin("/oauth2/authorization/keycloak"));
```

### CSS theming

`sui.css` exposes a flat set of CSS variables on `:root`
(`--sui-color-primary`, `--sui-radius-md`, `--sui-font-family`, …).
Host apps override them in their own CSS file without forking
`sui.css`:

```css
:root {
  --sui-color-primary: #16a34a;
  --sui-radius-md:     2px;
}
```

App-specific layouts (header, sidebar, special widgets) belong in a
separate `app.css`, not in `sui.css`. `sui-dark.css` and `sui-sbb.css`
ship as full theme overrides — see the shop demo for a runtime theme
picker.

## Open design questions

See [`todo.md`](todo.md) for the prioritised work list.

## Realistic expectations

`semantic-ui` is primarily an **internal framework for mindconnect.ai
projects** that is made public because the design might be instructive
to others and a small organic following could form.

It is **not** trying to compete with React / Vue / Angular or lead
the next OSS wave in the post-React movement. Datastar, HTMX and
LiveView have the lead and community there.

Success is: the framework works excellently for our use case, is
cleanly documented and reasoned about, and individual teams outside
find value in it.
