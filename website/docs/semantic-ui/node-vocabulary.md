---
title: Node vocabulary
sidebar_position: 3
---

# The node vocabulary

Every screen is built from these 27 node types. A node is a plain object with a
`type` — which decides how it renders — and an `id`, which is how you address it
afterwards:

```json
{ "type": "text", "id": "cart-total", "text": "€ 49.00" }
```

That `id` flows all the way through: it is the JSON id, the DOM `id` on the
rendered element, the `targetId` a [patch](./triggers.md) aims at, the editor's
selection target, and the anchor for form-field names. Give ids you'd be happy to
see in a test.

Each type exists three times over, kept symmetric: a **Java class**, a
**Handlebars template** for server-side rendering, and a **TypeScript render
function** for the browser. Adding a type means adding all three — or, in the
browser only, [one function](./how-it-works.md#step-5--add-a-node-type-of-your-own).

## Every type

Each page has the full field reference, a Java and a JSON example, and a live
preview you can click.

### Layout & structure

| Type | Purpose |
|---|---|
| [`stack`](./elements/stack.md) | Vertical / horizontal layout box |
| [`section`](./elements/section.md) | Tabbed or collapsible container |
| [`section-entry`](./elements/section-entry.md) | One tab + its panel body |
| [`page`](./elements/page.md) | Top-level envelope; carries toasts and dialogs |
| [`header`](./elements/header.md) | Page chrome — brand, extras, user widget |
| [`app-shell`](./elements/app-shell.md) | Header + menu + content, wired and styled |

### Data display

| Type | Purpose |
|---|---|
| [`table`](./elements/table.md) | Tabular data — columns, rows, row actions, paging |
| [`column`](./elements/column.md) | One table column (label + dataKey + cell template) |
| [`row`](./elements/row.md) | One table row (id + data map) |
| [`list`](./elements/list.md) | Item list — icons, descriptions, rich content |
| [`detail`](./elements/detail.md) | Read-only key/value view of one record |
| [`tree`](./elements/tree.md) | Expand/collapse tree |
| [`tree-node`](./elements/tree-node.md) | One tree entry (label, icon, children, rich content) |
| [`text`](./elements/text.md) | Bare text — and the classic patch target |

### Input

| Type | Purpose |
|---|---|
| [`form`](./elements/form.md) | `<form>` with fields, actions and links |
| [`field`](./elements/field.md) | One input — TEXT / SELECT / DATE / BOOLEAN / FILE / … |
| [`fieldgroup`](./elements/fieldgroup.md) | Titled `<fieldset>` grouping related fields |
| [`upload`](./elements/upload.md) | Drag-and-drop file drop zone |

### Actions & navigation

| Type | Purpose |
|---|---|
| [`action`](./elements/action.md) | Button, link or icon control with an `onClick` trigger |
| [`link`](./elements/link.md) | Plain navigation link |
| [`menu`](./elements/menu.md) | Navigation sidebar — expanded, rail, drawer |
| [`menu-item`](./elements/menu-item.md) | One menu entry (extends `action`) |
| [`menu-button`](./elements/menu-button.md) | Dropdown / context menu |

### Overlays & feedback

| Type | Purpose |
|---|---|
| [`dialog`](./elements/dialog.md) | Modal overlay whose body is any node |
| [`toast`](./elements/toast.md) | Corner message (not a node — rides on a page or patch) |
| [`spinner`](./elements/spinner.md) | Busy indicator |
| [`progress`](./elements/progress.md) | Progress bar or ring |
| [`icon`](./elements/icon.md) | Standalone icon from the swappable icon library |

Beyond the core, two extensions add a node type each — the
[chart extension](./chart-extension.md) adds `chart`, the
[diagram extension](./diagram-extension.md) adds `diagram` — and
`mc-semantic-ui-ext-markdown` adds `markdown` and `mc-semantic-ui-ext-json`
adds `json-viewer`. All of them use
the same registration mechanism you'd use for your own type.

A node type and the ability to render it belong together: the core only owns
types it can actually draw, which is why charts and diagrams live in modules.

Every type is also rendered live, next to its JSON and Java, in the
**[widget showcase ↗](pathname:///widget-demo/)**.

:::note `page` is an envelope, not a widget
`UiPage` wraps the tree the client navigates to (plus optional toasts, dialogs
and stream state). The browser renderer has **no `page` handler** — the event bus
unwraps it and mounts `page.node`.

So `renderer.mount(...)` takes a **node**. Hand it a `UiPage` and it won't throw:
it renders a `<pre>` dump of the JSON and logs *"no handler for node type"*.
`bus.applyPage(page)` is what takes a `UiPage`; `bus.navigate(href)` and
`bus.start(href)` take a **URL**, fetch the page and apply it.
:::

## Extending the vocabulary

The core ships these types; the set is open. A new node is a class, a template
and a render function — or, for a browser-only app, just the render function.
See [step 5 of How it works](./how-it-works.md#step-5--add-a-node-type-of-your-own)
and the [diagram extension](./diagram-extension.md), which is this mechanism at
full size.
