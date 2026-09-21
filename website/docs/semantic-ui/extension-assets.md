---
title: The asset registry
sidebar_position: 1
---

# The asset registry

**A host does not list its extensions.** Every jar that brings a node type —
the calendar, the chart, a plugin of your own — declares what the browser
needs from it: a stylesheet, an ES module, or an extension whose
`install(renderer, { bus })` registers its node types. `mc-semantic-ui-core`
collects those declarations into a `SuiAssetRegistry` and serves them as one
module, `/sui/assets.js`. Put a jar on the classpath and its node types render
on the next page load; take it off and they are gone.

## In the host

```js
// app.js
import { SuiRenderer, installDefaultHandlers } from "/sui/renderer.js";
import { SuiEventBus } from "/sui/eventbus.js";
import { installAll } from "/sui/assets.js";

const root = document.getElementById("sui-root");
const renderer = installDefaultHandlers(new SuiRenderer(root));
const bus = new SuiEventBus(renderer, root);
await installAll(renderer, bus);   // before the first render
bus.start("/");
```

`installAll` links every stylesheet the page does not carry yet, starts all
imports at once, installs the extensions one after another in the registry's
order, and resolves once the stylesheets have loaded — so the first render has
every node type and every style. A module that fails to load, an extension
without `install`, an `install` that throws: each is logged and skipped, and
the others go on. It resolves to a report, `[{ id, ok, error? }]`.

**The page head.** With a hand-written `index.html` there is nothing to add —
`installAll` links the stylesheets. A host that renders its own head can link
them there, before the first paint:

```java
@Autowired SuiAssetRegistry assets;
// …
String head = assets.headTags(request.getContextPath());
// <link rel="stylesheet" href="/sui-ext/calendar/calendar.css" data-sui-asset="calendar.css">…
```

`installAll` recognises those links by `data-sui-asset` and does not add them
twice. Pages the core renders server-side (`UiPage` with SSR switched on) get
the links in their head automatically.

## Declaring assets

**In a jar**, as `META-INF/sui/assets.json` — the way every extension of this
project does it:

```json
[
  { "id": "calendar.css", "kind": "css",       "href": "/sui-ext/calendar/calendar.css" },
  { "id": "calendar",     "kind": "extension", "href": "/sui-ext/calendar/extension.js" }
]
```

**In code**, as a `SuiAssetContribution` bean — for a list that depends on
configuration:

```java
@Bean
SuiAssetContribution officeAssets() {
    return () -> List.of(
            SuiAsset.css("office.css", "/office/office.css"),
            SuiAsset.extension("office", "/office/extension.js"));
}
```

| Field | Meaning |
|---|---|
| `id` | Unique name: letters, digits, `.`, `_`, `-`. By convention `<name>` for the module and `<name>.css` for its stylesheet. |
| `kind` | `css` — linked in the head; `module` — imported, nothing more; `extension` — imported, then `install(renderer, { bus })`; `icons` — an SVG sprite for the icon tokens starting with `prefix` ([below](#icon-sets)). |
| `href` | Where the browser loads it: a path on this server, starting with a single `/`. The servlet context path is added when the registry serves it. |
| `order` | Load order, and the rank among declarations of the same id. Default `0`. |
| `disabled` | `true` takes the asset off the page — see below. |
| `prefix` | For `icons` only, and required there: the token prefix the sprite serves, lowercase-kebab ending in `-` (`brand-`). |

The files themselves are ordinary static resources: put them under
`META-INF/resources/…` in the jar and Spring Boot serves them.

## Icon sets

A plugin that brings its own icons — provider logos such as `brand-microsoft`
and `brand-google` — declares its sprite as an icon set:

```json
[
  { "id": "brand-icons", "kind": "icons", "prefix": "brand-", "href": "/sui-ext/brand/brand-icons.svg" }
]
```

```java
SuiAsset.icons("brand-icons", "brand-", "/sui-ext/brand/brand-icons.svg")
```

Every icon token that starts with the prefix comes from that sprite, in the
browser and on the server alike: `installAll(renderer, bus)` adds the sets to
the renderer before it installs anything else, and server-rendered pages
resolve the same tokens to the same markup. Any other token keeps coming from
the standard sprite, `/sui/icons.svg`.

- **Several plugins, several sets.** Each prefix is served by its own sprite;
  a token takes the **longest** prefix it starts with, so `acme-logo-` can sit
  inside `acme-`.
- **Two sets for one prefix** compete like two declarations of one id: the
  higher `order` wins; on a tie the id that sorts last, with a warning in the
  log. `disabled` takes a set off the page, and its tokens go back to the
  standard sprite.
- **The sprite** is a plain SVG of `<symbol id="…">` elements, the id being
  the full token (`brand-microsoft`). An icon from a set carries the class
  `sui-icon--set` and is drawn **filled**, not stroked: a shape with a fixed
  `fill` keeps its colour (a logo), a shape with `fill="currentColor"` — or
  with no fill — follows the text colour. The standard icons stay as they are.

```xml
<svg xmlns="http://www.w3.org/2000/svg" style="display:none">
  <symbol id="brand-microsoft" viewBox="0 0 24 24">
    <rect x="1" y="1" width="10" height="10" fill="#F25022"/>
    <rect x="13" y="1" width="10" height="10" fill="#7FBA00"/>
    <rect x="1" y="13" width="10" height="10" fill="#00A4EF"/>
    <rect x="13" y="13" width="10" height="10" fill="#FFB900"/>
  </symbol>
  <symbol id="brand-github" viewBox="0 0 24 24">
    <path fill="currentColor" d="…"/>
  </symbol>
</svg>
```

A plugin no longer needs to replace the icon resolver for this — which only
ever worked for one plugin, since the next one replaced it again. A plugin
that must do more than a sprite can wrap the resolver instead of replacing it;
see [the icon library](./icons.md#adding-to-the-resolver).

## Overriding and switching off

Several declarations may share an `id`. **The highest `order` wins.** A
plugin replaces the calendar's stylesheet with its own by declaring the same id
with a higher order:

```json
[{ "id": "calendar.css", "kind": "css", "href": "/acme/calendar.css", "order": 10 }]
```

```css
/* /acme/calendar.css — keep the original, change what you want */
@import url("/sui-ext/calendar/calendar.css");
.sui-calendar { --sui-calendar-hour-h: 40px; }
```

**A winner that is `disabled` takes the id off the page** — a host that does
not want the kanban's browser side:

```json
[{ "id": "kanban", "disabled": true, "order": 1 }]
```

**A tie** — the same id and the same order from two places — is resolved the
same way on every start (a run-time registration beats a bean, a bean beats a
jar, and between two of a kind the one whose source sorts last) and logged as
a warning, because it is almost always an accident. Give one of them a higher
order to decide.

The resolved assets load in `order`, then by id.

## Registering at run time

For a marketplace, the registry changes while the app runs:

```java
assets.register(SuiAsset.extension("gantt", "/market/gantt/extension.js"));
assets.register(SuiAsset.css("gantt.css", "/market/gantt/gantt.css"));
assets.unregister("gantt");                 // only run-time declarations
assets.register(SuiAsset.disabled("chart", 100));   // switch off a shipped one
```

The registry does not check who is asking — put `register` behind your own
admin endpoint and its permissions. It checks the asset: the `id` must be
well-formed and the `href` a path on this server — no scheme, no host, no
`//`, no backslash, quote or whitespace — since a file the page loads runs
code. A refused asset throws `IllegalArgumentException` and changes nothing.

Every change moves the registry's generation on, and with it the ETag of
`/sui/assets.js`: the next page load fetches the new module.

## The endpoints

| | |
|---|---|
| `GET /sui/assets` | The resolved assets as JSON: `id`, `kind`, `href`, `url` (under the context path), `order`. |
| `GET /sui/assets.js` | The module: `installAll(renderer, bus)`, `linkStyles()`, `assets`. |

Both answer with an ETag and `Cache-Control: no-cache` — the browser keeps
its copy and revalidates, getting a `304` until something changes. Switch the
registry off with `mindconnect.sui.assets.enabled=false`; define a
`SuiAssetRegistry` bean of your own to replace it.

## A page without a server

A static page — a demo, a docs site — has no `/sui/assets.js` to ask for.
`SuiAssetsExport` writes the same module as a file at build time, from the
`assets.json` of every jar on the build's classpath:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>export-sui-assets</id>
      <phase>process-resources</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>ai.mindconnect.ui.assets.SuiAssetsExport</mainClass>
        <classpathScope>compile</classpathScope>
        <arguments>
          <argument>${project.build.directory}/dist/sui/assets.js</argument>
          <argument>..</argument>   <!-- base in front of every href -->
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The module resolves every url against its own location, so a relative base
works wherever the files are hosted: `..` for a module in `sui/` beside
`sui-ext/`. The page imports it as it would the served one —
`import { installAll } from "./sui/assets.js"`. The widget demo
(`demo/mc-sui-widget-demo`) is built this way: its extensions are Maven
dependencies, and `demo.js` lists none of them.

## An example

With a server: the file-explorer demo (`demo/mc-sui-file-explorer-demo`) depends on the
calendar and kanban extensions and wires neither: `/agenda` shows both, its
bootstrap calls `installAll`, and `DemoAssets` contributes the demo's own
stylesheet and overrides `calendar.css` with order 10.
