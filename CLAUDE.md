# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project Overview

**semantic-ui** is a fast, dynamic server-side UI that also runs as a client
app: a typed JSON UI vocabulary (`UiNode` tree) that renders three ways from one
model — SSR HTML (no JS), a live SPA (TypeScript renderer + `SuiEventBus`), and
inside the visual editor. Java 21 + Spring Boot 3.5.x. Fully self-contained —
it does not depend on any other repo.

Read [README.md](README.md) first, and `doc/concept.md` for the long-form
rationale.

## Architecture (the one thing to internalize)

The same `UiNode` tree renders **twice**, and the two outputs must be
**identical markup**:

- **SPA renderer** — `core/mc-semantic-ui-core/src/main/ts/renderers/*.ts`, each
  registered in `renderer.ts` (`installDefaultHandlers`). `SuiEventBus`
  (`eventbus.ts`) upgrades a page to a live SPA and auto-runs the enhancers
  (menu state, tab overflow, menu-button popovers) after every render.
- **SSR renderer** — Handlebars templates `src/main/resources/templates/sui/{type}.hbs`,
  resolved by type name, driven by `SuiServerRenderer.java`.

When you add or change a node, update **both** renderers plus the model in all
three places:

- `src/main/java/.../ui/model/*.java` — the `UiNode` subtype. Jackson
  polymorphism is declared in `UiNode.java` via `@JsonSubTypes` (`type`
  discriminator). New subtype ⇒ add a `@JsonSubTypes.Type` entry.
- `src/main/ts/model.ts` — the mirroring TypeScript discriminated union.
- Editor metadata: `editor/mc-sui-editor/.../NodeRegistry.java`.

Keep `SuiServerRendererTest` green — it locks the SSR markup for every node.

## Modules

Libraries live under `core/` and `ext/`; everything else is an application.
A node type and the ability to render it belong together, so the core owns
only the types it can draw itself — anything needing its own painter is an
extension.

- `core/mc-semantic-ui-core` — `UiNode` model + dual renderer (the heart)
- `ext/mc-semantic-ui-ext-json` (`json-viewer`),
  `ext/mc-semantic-ui-ext-markdown` (`markdown`),
  `ext/mc-semantic-ui-ext-diagram` (`diagram`),
  `ext/mc-semantic-ui-ext-chart` (`chart`) — each adds node types
- `editor/mc-sui-editor` (+ `-app`, `-standalone-app`) — embeddable visual editor
- `demo/mc-sui-shop-demo` (Postgres CRUD), `mc-sui-widget-demo`,
  `mc-sui-file-explorer-demo`, `mc-sui-shop-client-demo`,
  `mc-sui-node-demo` (no Java — `packaging: pom`, builds its npm side only)
- `website/` — Docusaurus docs (live in this repo; no separate deploy)

## Build & run

```bash
mvn clean install -DskipTests            # whole repo
mvn -pl core/mc-semantic-ui-core install      # one module, via the reactor root
mvn -pl core/mc-semantic-ui-core test -Dtest=SuiServerRendererTest

# apps
mvn -f editor/mc-sui-editor-app/pom.xml spring-boot:run   # http://localhost:8080/editor
mvn -f demo/mc-sui-shop-demo/pom.xml    spring-boot:run   # needs Postgres; see its README

# docs
cd website && npm install && npm run start
```

The TS bundle is built by `frontend-maven-plugin`. Node installs **once** into a
shared `.node/` at the repo root. On a fresh build macOS can rarely fail the
first Node unpack (`Could not install Node: …/tmp`) — just re-run; Node is then
present and skipped. Prefer building single modules **via the reactor root**
(`-pl`) so the shared `.node` is reused (a bare `mvn -f <module>` reinstalls Node
under that module and can hit the same race).

## Parent / versioning

One parent for the whole repo: `parents/mc-semantic-ui-parent` (Java 21 +
Lombok + Spring Boot BOM + Jackson + frontend/handlebars). Every module inherits
it via `relativePath`. Libraries (`core`, `extensions`) stay Spring-free — the
BOM only manages versions; app modules add their own starters.

The whole repo shares one version (SemVer; pre-1.0 — breaking changes may land
in a minor); `main` always carries the next `-SNAPSHOT`. Keep it that way — the
release workflow rewrites the version across every pom, so a module that drifts
out of step breaks it. Released versions go to Maven Central, see
[RELEASING.md](RELEASING.md).

## Conventions

- All Java packages use the `ai.mindconnect.*` root (e.g. `ai.mindconnect.ui.*`).
- SSR and SPA output must match; `encodeTrigger` + `data-trigger='…'` is the
  shared trigger encoding. Icons resolve via `renderIcon(name)` (lowercase-kebab
  sprite ids).
- **Git commits: do NOT add a `Co-Authored-By` trailer.**
