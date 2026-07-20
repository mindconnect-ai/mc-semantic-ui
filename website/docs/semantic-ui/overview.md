---
title: semantic-ui
slug: /
sidebar_position: 1
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# semantic-ui

> **Stop wasting your time with complicated frontend frameworks.**
>
> Why did UI frameworks get this complicated? All you wanted was an input screen
> and a table. Instead you're picking a bundler, learning a reactivity model,
> reasoning about signals and effects, wiring a router, mirroring your validation
> rules in a second language — before a single field shows up on screen.
>
> semantic-ui throws that out. You **describe** the screen as a small, typed tree
> — `UiTable`, `UiForm`, `UiField`, … — and a ready-made renderer draws it.
> Data-driven, with a clean split between **logic** (yours, where your data
> already is) and **design** (one CSS system, shared by every screen). No
> components. No state. No build step.

semantic-ui flips the relationship:

|                     | React / Angular                    | semantic-ui                            |
|---------------------|------------------------------------|----------------------------------------|
| **You write**       | components (JSX / templates)       | data — a typed `UiNode` tree           |
| **Markup + logic**  | mixed inside components            | split: model = structure, CSS = look   |
| **State**           | hooks / signals / Redux            | none — UI = `render(your data)`        |
| **Styling**         | per component, drifts over time    | one shared CSS design system           |
| **Runtime / build** | a framework + toolchain to learn   | a `<script>`, or plain JSON            |
| **Interactivity**   | wire up handlers + state yourself  | declarative triggers (fetch / patch / navigate) |

The model is plain typed JSON. A small TypeScript renderer turns the tree into a
live, interactive UI in the browser; on the JVM a matching Handlebars renderer
produces the **same** markup as no-JS server HTML, and a JavaFX renderer draws
the very same tree as a native desktop client. One model, many renderers: a
live app, no-JS HTML, a [desktop client](./javafx.md), and a built-in visual
editor. Where the data comes from is
up to you — a JavaScript object in the page, or any backend that emits the JSON.

![Semantic UI — how it works](/img/semantic-ui/how-it-works.svg)

The rightmost box is not hypothetical. Here is a `UiNode` tree — the same kind
this page describes for the browser — painted by the
[JavaFX renderer](./javafx.md) as a native desktop window:

![The same UiNode tree as a native JavaFX desktop app](/img/semantic-ui/javafx/orders-table.png)

## The two shapes you'll write

Everything is one of two payloads. Build them with the Java API, or emit the
same JSON from anything — they are the identical wire format.

**`UiPage`** — a whole screen. What you return when the user navigates:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiPage.of("/products",
    UiTable.of("products", "Products")
        .column(UiColumn.text("name", "Name"))
        .row(Map.of("id", "p1", "name", "Widget")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "page",
  "navigate": "/products",
  "node": {
    "type": "table", "id": "products", "title": "Products",
    "columns": [
      { "type": "column", "id": "c-name", "label": "Name", "dataKey": "name" }
    ],
    "rows": [
      { "type": "row", "id": "p1", "data": { "name": "Widget" } }
    ]
  }
}
```

</TabItem>
</Tabs>

The same model — two more columns, two more rows, a row action — drawn live
below by the real renderer with the stock stylesheet. No build step, no
components, nothing else on the page:

<iframe
  src="/mc-semantic-ui/embed/products.html"
  title="A live semantic-ui island: the products table"
  loading="lazy"
  style={{width: '100%', height: '330px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

**`UiPatch`** — a surgical update. What you return to change *part* of the
screen without resending it. Each operation targets a node by `id`
(`REPLACE`, `APPEND`, `CLEAR`, `REMOVE`), and it can carry a toast:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiPatch.of()
    .patch(UiPatch.Operation.replace("cart-total",
            UiText.of("cart-total", "€ 49.00")))
    .toast(UiToast.success("Added to cart"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "patches": [
    { "op": "REPLACE", "targetId": "cart-total",
      "node": { "type": "text", "id": "cart-total", "text": "€ 49.00" } }
  ],
  "toasts": [ { "level": "SUCCESS", "message": "Added to cart" } ]
}
```

</TabItem>
</Tabs>

That's the whole interaction model: a node carries a
[trigger](./triggers.md), the trigger calls you, you answer with a `UiPage` or
a `UiPatch`. Nothing else to learn.

<div class="sui-cta">

## Draw it instead — and download the running app

You don't have to write that JSON by hand. The **visual editor** runs entirely in
your browser — no install, no account, no backend — and it edits the same tree
this page has been describing: drag nodes, set properties, watch the live
preview.

Then **export it as a runnable application**. Pick your target and download a
zip:

| Export | What you get |
|---|---|
| **Static HTML** | An `index.html` plus the runtime. Open it, or drop it on any static host. |
| **Spring Boot** | A Maven project with a controller per page. `mvn spring-boot:run`. |
| **Node.js** | An Express app with a route per page. `npm install && npm start`. |

The export is a plain, hand-written semantic-ui app — **not** a copy of the
editor and not a runtime that interprets a saved file. It is the starting point
you would otherwise have typed yourself, which also makes the editor a
reasonable way to *learn* the model: build a screen, export it, read the code.

<div class="sui-cta-actions">
  <a class="button button--primary button--lg" href="/mc-semantic-ui/editor/">Open the visual editor ↗</a>
  <a class="button button--secondary button--lg" href="/mc-semantic-ui/semantic-ui/editor">How the editor works</a>
</div>

</div>

## Choose your path

semantic-ui fits three setups. Pick the one that matches your stack and start
there — all three share the same [core concepts](./node-vocabulary.md).

- **[Java / Spring Boot](./quickstart-spring-boot.md)** — a controller returns a
  `UiPage`; get no-JS server HTML (SSR) and/or a live SPA client from the same
  code.
- **[JavaScript client only](./quickstart-client.md)** — no backend at all: load
  the runtime from a `<script>` and render `UiNode` literals in the browser.
- **[Node.js backend](./quickstart-node.md)** — an Express (or any) server emits
  the `UiPage` JSON; the browser renderer paints it.

:::tip Try it live
Open the **[interactive widget showcase ↗](pathname:///widget-demo/)** — a fully
static page (no backend) that renders every UiNode, each shown next to the JSON
the renderer consumes and the equivalent Java builder.
:::

## Why it feels different

Frontend development has piled up a mental load that's wildly out of proportion
for most CRUD-shaped business apps: build toolchain, reactivity model, state
management, routing, form-state, server-state cache, a component library,
validation — much of it duplicated on both sides of the wire.

semantic-ui takes a different stance:

- **You write data, not components** — no JSX, no lifecycle, no re-render model.
  A `UiTable` literal *is* the table.
- **No state management** — the UI is a pure function of your data.
- **Structure is data, design is CSS** — they can't tangle; you get a consistent
  design system by default. Business logic stays where your data is.

### What you can build with it

Most application UIs are structured screens — and structured screens are exactly
what a typed tree describes well:

- Admin / backoffice, ERP / CRM, accounting, HR, order entry
- Workflow, wizard and case-management UIs
- B2B SaaS dashboards, tables, reporting surfaces
- Customer-facing CRUD and shop frontends — the design system is yours to
  restyle; see the [shop demo](./shop-demo.md)
- Editors and canvases — the [visual editor](./editor.md) and the
  [diagram extension](./diagram-extension.md) are themselves built this way
- Streaming / chat surfaces, via `UiPatch` over SSE
- LLM-agent-operable UIs — a finite vocabulary is far easier for an agent to
  drive than free-form HTML

If a screen can be described as *"these fields, this table, these actions"*, it
fits. And when one corner of a page needs something bespoke, you don't leave the
framework: add a node type (or drop in a [UI island](./ui-island.md)) and keep
the rest.

## Core concepts (shared by all three paths)

- **[How it works](./how-it-works.md)** — the data → renderer model, with a full
  example.
- **[Node vocabulary](./node-vocabulary.md)** — all 27 types, each with its own
  reference page: fields, Java, JSON and a live preview.
- **[Triggers & actions](./triggers.md)** — how clicks/forms turn into
  fetch / patch / navigate.
- **[Forms & validation](./forms.md)** and **[Mobile & responsive](./responsive.md)**
  — the two topics that cut across many node types.
- **[Rendering modes](./rendering-modes.md)** — SSR, SPA, and partial updates
  with `UiPatch`.
- **[JavaFX desktop client](./javafx.md)** — the same tree as a native desktop
  app, with a complete small example.
- **[How it compares](./how-it-compares.md)** — versus HTMX, Datastar,
  Inertia.js and Vaadin Flow.

Setup lives in the quickstart for your stack — pick one under
**[Choose your path](#choose-your-path)** above.
