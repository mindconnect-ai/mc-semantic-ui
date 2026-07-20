---
title: How it compares
sidebar_position: 12
---

# How it compares

semantic-ui is **UI as data**: a screen is a typed JSON tree, and a renderer
interprets it. That one choice is what separates it from its neighbours — so the
useful question is what each of them actually sends over the wire.

| Framework | What travels |
|---|---|
| **HTMX**, **Datastar** | rendered HTML fragments |
| **Vaadin Flow** | component-tree diffs against a stateful server session |
| **Inertia.js** | props for a client SPA framework |
| **Adaptive Cards**, **JSON Forms** | UI as JSON — one card, one form |
| **semantic-ui** | UI as JSON — a whole application |

Because the payload is an abstract model rather than markup, three things follow
that no neighbour offers together: the same tree renders as a live SPA, as no-JS
server HTML and inside a [visual editor](./editor.md); a screen can run with
**no backend at all**; and a new render target is
[one function per node type](./how-it-works.md#step-5--add-a-node-type-of-your-own).

## Against server-driven frameworks

|                          | **HTMX** | **Datastar** | **Inertia.js** | **Vaadin Flow** | **semantic-ui** |
|--------------------------|----------|--------------|----------------|-----------------|-----------------|
| What ships over the wire | rendered HTML | rendered HTML + signals | JSON props for a SPA framework | component-tree diffs | **abstract UI model (typed JSON)** |
| Server holds UI state    | no | no | no | yes — a live tree per session | **no — stateless responses** |
| Renderer is              | the browser | the browser | the SPA framework | locked in | **swappable / pluggable** |
| Client-side state        | none | signals | SPA-framework store | none (on server) | **none you manage** |
| Backend language         | any | any | any | Java only | **any — or none at all** |
| Type-safe contract       | no | no | partial | yes | **yes** |
| Visual (WYSIWYG) editor  | — | — | — | — | **yes, built in** |

**HTMX** and **Datastar** put markup in your controllers. semantic-ui puts a
typed tree there: refactorable, diffable, machine-generatable, and
compile-time-checkable in Java, Kotlin or Go. The look of every screen lives in
one stylesheet.

**Inertia.js** keeps a React / Vue / Svelte SPA on the client — its build, its
dependencies, its upgrades. semantic-ui drops the SPA framework and still renders
the same JSON as plain HTML when no JS is loaded.

**Vaadin Flow** keeps a live component tree per user on the server. A
semantic-ui response is stateless — one tree, one request — so it scales like the
rest of your backend, in any language.

### The same screen, both ways

An HTMX controller assembles markup, so the app's look lives in every fragment
that produces it:

```html
<!-- the server returns this -->
<table class="table table-striped">
  <thead><tr><th>SKU</th><th>Name</th></tr></thead>
  <tbody>
    <tr><td>A-1</td><td>Widget</td>
        <td><button hx-delete="/products/p1" hx-target="closest tr"
                    class="btn btn-sm btn-danger">Delete</button></td></tr>
  </tbody>
</table>
```

A semantic-ui controller states what the screen *is*. The renderer and one
stylesheet decide how it looks — for every table in the app at once:

```json
{ "type": "table", "id": "products",
  "columns": [
    { "type": "column", "id": "c-sku",  "label": "SKU",  "dataKey": "sku"  },
    { "type": "column", "id": "c-name", "label": "Name", "dataKey": "name" }
  ],
  "rows": [ { "type": "row", "id": "p1", "data": { "sku": "A-1", "name": "Widget" } } ],
  "rowActions": [
    { "type": "action", "id": "del", "label": "Delete", "style": "DANGER",
      "confirm": "Delete this product?",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "DELETE", "url": "/products/{id}" } }
  ] }
```

No CSS-framework class names in your backend, and the same payload is what the
visual editor reads and writes.

## Against JSON-described UI

The closest relatives — both describe UI as data. semantic-ui scales the idea up
from a single artefact to an application:

|                     | **Adaptive Cards** | **JSON Forms** | **semantic-ui** |
|---------------------|--------------------|----------------|-----------------|
| Scope               | one card | one form (from JSON Schema) | **a whole application** |
| Navigation / routing | — | — | **yes** (`UiPage`, triggers) |
| Partial updates     | — | — | **yes** (`UiPatch` by node id) |
| Tables, menus, dialogs, trees | limited | — | **yes** |
| Server-side HTML rendering | — | — | **yes** (Handlebars, no JS needed) |
| Typed builder API   | SDKs per platform | — | **yes** (Java; any language emits the JSON) |

**Adaptive Cards** proved the model: a JSON UI payload with host-specific
renderers, designed to live inside someone else's app. semantic-ui carries it to
the whole screen — routing, partial updates, full-page rendering.

**JSON Forms** turns a JSON Schema into a form, and does it well. In semantic-ui
a form is one node type among [27](./node-vocabulary.md), next to tables, menus,
dialogs and trees.

## Choosing

Pick semantic-ui when you want a **typed, language-agnostic UI contract** whose
renderer you control, at application scope — see
[what you can build](./overview.md#what-you-can-build-with-it). Pick HTMX or
Datastar when hand-writing markup is what your team actually wants.
