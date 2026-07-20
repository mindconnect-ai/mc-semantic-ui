# mc-sui-editor-standalone-app

A **backend-free, self-contained** build of the Semantic UI visual editor. The
whole thing runs in the browser: projects and pages live in `localStorage`,
there is no server, no fetch to an API, no database.

It reuses the real pieces:

- the **core `SuiRenderer` + `SuiEventBus`** (`mc-semantic-ui-core`) to paint
  the projects home and the live preview,
- the **core `UiTree`** node for the project/page navigator,
- the **`mc-sui-editor`** editor itself (`bootEditor`), wired to a
  `localStorage` backend instead of the Spring REST endpoints.

## What you can do

- A **landing page** listing your projects (core `UiTree`). Create / rename /
  delete / open a project.
- Each project has **its own page** listing that project's pages: add pages,
  open them in the editor, preview, rename, delete, and **download the project
  as a runnable app**.
- **Edit** a page in the full visual editor (tree · properties · live preview).
  The properties panel is a **Monaco JSON editor** with live **schema
  validation** — the schema (`ui-node.schema.json`) is generated at build time
  from the real `UiNode` classes, so wrong enum values, type mismatches or an
  unknown node `type` are underlined inline as you type. (Monaco loads from a
  CDN; the *downloaded* apps contain no editor and stay fully offline.)
- **Preview** a project: pages are mounted with the real renderer **and** event
  bus, so triggers fire, forms submit, tabs switch — exactly like production.
  A page switcher jumps between pages; authored links to other pages
  (`page:<id>` / page name) resolve against `localStorage`.
- **Download this project** as a **plain, hand-written Semantic UI app** — the
  standard setup from the docs (`index.html` shell + `app.js` wiring
  `SuiRenderer` + `SuiEventBus`), **not** the editor and not a custom runtime.
  Three flavours:
  - **Static site** — `app.js` embeds your pages as `UiNode` trees and mounts
    them; navigation between pages is backend-free. No server.
  - **Spring Boot app** — **one clean path per page** (`GET /<slug>`) returns
    `UiPage` JSON; a `SpaForwardingFilter` forwards browser navigations
    (`Accept: text/html`) to the shell, the event bus's `application/json`
    fetches get the page. Bookmarkable deep links, no `/api` prefix.
  - **Node.js / Express app** — the same, with an Express server.

  Authored cross-page links (a trigger to `page:<id>`) are rewritten to the real
  target — a clean path for the server builds, an in-browser navigation for the
  static build. The download is a real dev project you can open and keep
  building on (e.g. back the page paths with a database).

## How the backend-free part works

The Spring-hosted editor talks to four endpoints under `/editor/api`. Those are
hidden behind an `EditorBackend` interface (`mc-sui-editor/backend.ts`); this
app installs a `LocalStorageBackend`:

| Editor need        | Spring editor            | This app                                   |
|--------------------|--------------------------|--------------------------------------------|
| node catalogue     | `GET /schema`            | bundled `data/schema.json`                 |
| default instance   | `GET /default/{type}`    | clone of bundled `data/defaults.json`, re-id'd |
| load page tree     | `GET /state`             | `localStorage`                             |
| save page tree     | `PUT /state`             | `localStorage`                             |

`schema.json` and `defaults.json` are **dumped at build time from the same
`NodeRegistry`** the Spring editor uses (`SchemaDump`), so the catalogue never
drifts. `data/manifest.json` lists every file in the site so the in-app
exporters can repackage it.

The renderer's optional Idiomorph-from-CDN morph is pinned to a plain
`innerHTML` morpher here, so the app makes **no external requests at all**.

## Build & run

```bash
# Assemble the static site under target/dist (from the repo root or here)
mvn -f semantic-ui/editor/mc-sui-editor-standalone-app/pom.xml package

# Serve it (module scripts + fetch need http://, not file://)
cd semantic-ui/editor/mc-sui-editor-standalone-app/target/dist
npx serve .            # or: python3 -m http.server 8080
```

Then open the printed URL.

## Layout of `target/dist`

```
index.html  shell.js  store.js  preview.js  offline.js  app.css
exporters/  — in-browser static / Spring Boot / Node exporters + zip writer
sui/        — compiled core renderer + event bus + themes (from mc-semantic-ui-core)
sui-editor/ — compiled visual editor (from mc-sui-editor)
data/       — schema.json, defaults.json, manifest.json
```

Not a published Maven artifact — it produces a static site, not a jar.
