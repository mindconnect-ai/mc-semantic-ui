# mc-sui-widget-demo

A **static, backend-free** showcase of every Semantic UI widget — including the
generic [`UiTree`](../../core/mc-semantic-ui-core/src/main/ts/renderers/tree.ts) with
expandable / collapsible nodes.

There is no Spring app and no server here. The page is driven entirely by plain
JavaScript objects: each is a `UiNode` literal (the same JSON a server would
send), and the core `SuiRenderer` turns it into DOM in the browser.

## Can the renderer load a UiNode straight from JS? Yes — natively.

That is the whole point of this module. No core change was needed:

```js
import { createDefaultRenderer } from "./sui/renderer.js";

const root = document.getElementById("sui-root");
createDefaultRenderer()
    .attach(root)
    .mount({ type: "text", id: "t", text: "Hello, world" });
```

`render(node)` / `mount(node)` accept any `UiNode` object. Interactivity that
needs no backend (tab switching, tree expand/collapse, native `<details>`) is
wired by attaching a `SuiEventBus` to the same root. See
[`src/main/static/demo.js`](src/main/static/demo.js) for the full example,
including a small custom inline-SVG chart handler registered via
`renderer.register("chart", …)`.

## Build

```bash
# From the repo root (reactor builds mc-semantic-ui-core first):
mvn -pl semantic-ui/demo/mc-sui-widget-demo -am -DskipTests install
```

The build unpacks the compiled renderer + stylesheets from the
`mc-semantic-ui-core` jar and assembles a self-contained site under:

```
target/dist/
├── index.html
├── demo.js
└── sui/            ← renderer.js, eventbus.js, model.js, renderers/*.js, sui*.css
```

## Run

ES module imports don't work over `file://`, so serve the folder with any
static file server (no application server needed):

```bash
cd semantic-ui/demo/mc-sui-widget-demo/target/dist
python3 -m http.server 8000
# open http://localhost:8000
```

`target/dist` is fully static — drop it on GitHub Pages, S3, nginx, or any CDN
as-is.

## Code panels

Every widget has a collapsible **"Show code — JSON · Java"** panel with two tabs:

- **JSON** — generated live from the exact node being rendered (via
  `JSON.stringify`), so it can never drift from what's on screen. This is the
  payload the `SuiRenderer` consumes.
- **Java** — the equivalent server-side builder (`UiTree.of(...)`,
  `UiForm.of(...)`, …) that produces that node.

The two are *equivalent*, not byte-identical: server-side defaults (e.g.
`enabled:true`) and generated DOM ids (e.g. `UiColumn.of(...)` mints a
`col-…` id) may differ slightly from the trimmed JSON shown.

## What's shown

- **Tree** — file explorer (icons, nested folders, clickable leaves, initial
  open state, selected node) and trees whose nodes carry a whole component
  (`detail`, `chart`) as inline content.
- **Lists & Tables** — list with collapsible rows / actions / pagination;
  table with multi-select, row actions and pagination.
- **Forms** — every field type (text, textarea, number, currency, percent,
  date, select, multiselect, boolean), all action styles, links; plus a
  read-only `detail`.
- **Layout & Charts** — text, button styles, links, a collapsible section, and
  BAR / LINE / AREA / PIE / DONUT charts.
- **Themes** — light / dark, switchable live from the top bar.
