---
title: Diagram extension
sidebar_position: 4
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Diagram extension

`mc-semantic-ui-ext-diagram` adds a `diagram` UiNode that renders a directed
graph as an **interactive SVG canvas** — nodes, edges, labels and waypoints. It
is domain-agnostic: any server-side producer can build a `UiDiagram`. In this
repo its primary use is rendering a `WorkflowData` as an activity diagram (the
workflow diagram editor builds on it).

It lives in its own extension module — separate from the small
the smaller extensions — because the editor pipeline it grows
into brings real weight (ELK layout, web-component event plumbing, a
domain-specific patch type).

:::note Its sibling
[`chart`](./chart-extension.md) is built the same way: an extension module that
brings a node type and the painters for it. Both follow the same rule — a node
and the ability to render it ship together, so the core never carries a type it
cannot draw.
:::

## The wire shape

A `UiDiagram` is a `diagram` node with a list of nodes and edges — built with
the Java API, or emitted as JSON by any producer:

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
var diagram = new UiDiagram();
diagram.setId("wf-42");
diagram.setWidth(800);
diagram.setHeight(600);

var start = UiDiagramNode.of("n1", "event-start", null);
start.setPosition(Position.of(240, 40));

var action = UiDiagramNode.of("n2", "action", "fetch");
action.setPosition(Position.of(240, 140));

diagram.addNode(start)
       .addNode(action)
       .addEdge(UiDiagramEdge.flow("e1", "n1", "n2"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{
  "type": "diagram",
  "id": "wf-42",
  "width": 800,
  "height": 600,
  "nodes": [
    { "id": "n1", "shape": "event-start", "position": {"x": 240, "y": 40} },
    { "id": "n2", "shape": "action", "label": "fetch", "position": {"x": 240, "y": 140} }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "target": "n2", "kind": "flow" }
  ]
}
```

</TabItem>
</Tabs>

### Nodes

A `UiDiagramNode` carries an `id`, a `shape` and `position`, plus optional
`label`, `shapeClass` (domain CSS styling), `marker`, `width`/`height`, and
nested `children` (containers hold their nested steps as children). Capability
flags — `canSelect`, `canDrag`, `canInsertAfter`, `canDelete` — and a
`synthetic` flag let the editor decide what the user may do with each node;
`stepRef` and a free-form `data` map link a node back to its domain object.

### Edges

A `UiDiagramEdge` connects nodes by `id` (`source` → `target`), not by index, so
the wire form survives client-side reordering. Each edge has a `kind` (defaults
to `flow`), an optional `label`, an `ownerNodeId`, and optional `waypoints` — a
list of points the renderer threads a polyline through before reaching the
target.

## Enable it

Add the module; Spring Boot registers the Jackson subtype automatically:

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-ext-diagram</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`UiDiagramAutoConfiguration` picks up `UiDiagramModule`, which registers the
`diagram` subtype with Jackson. Non-Spring consumers can call
`mapper.findAndRegisterModules()` to register it via `ServiceLoader`.

### …or client-side only, without any of that

The Maven module matters for the **Java** side — the model class and the Jackson
subtype. If your tree comes from somewhere else (a literal in the page, a Node
backend, a static JSON file), you need none of it: the extension is a browser
bundle you import and install.

```html
<link rel="stylesheet" href="/sui/sui.css">
<link rel="stylesheet" href="/sui-ext/diagram/diagram.css">
<div id="app"></div>

<script type="module">
  import { createDefaultRenderer } from "/sui/renderer.js";
  import { install as installDiagram } from "/sui-ext/diagram/extension.js";

  const renderer = createDefaultRenderer().attach(document.getElementById("app"));
  installDiagram(renderer);          // registers the "diagram" handler — that's it

  renderer.mount({
    type: "diagram", id: "flow", width: 520, height: 200,
    nodes: [
      { id: "start", label: "Order placed", shape: "rounded-rect", position: { x: 10,  y: 70 } },
      { id: "check", label: "In stock?",    shape: "diamond",      position: { x: 210, y: 58 } },
      { id: "ship",  label: "Ship",         shape: "rect",         position: { x: 340, y: 20 } },
      { id: "back",  label: "Backorder",    shape: "rect",         position: { x: 340, y: 120 } }
    ],
    edges: [
      { id: "e1", source: "start", target: "check" },
      { id: "e2", source: "check", target: "ship", label: "yes" },
      { id: "e3", source: "check", target: "back", label: "no"  }
    ]
  });
</script>
```

That page is running here — no server, no build step:

<iframe
  src="/mc-semantic-ui/embed/diagram-client.html"
  title="Live: the diagram extension, client-side only"
  loading="lazy"
  style={{width: '100%', height: '300px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

Two details worth knowing:

- **`installDiagram(renderer)` is scoped to that renderer.** Nothing global is
  patched, so a page can run two renderers where only one knows `diagram`.
- **The stylesheet is separate.** `extension.js` paints the SVG;
  `diagram.css` styles it. Load both, or you get an unstyled drawing.

### Shape names

`shape` is looked up in the extension's registry — an unknown name silently
falls back to a plain rectangle, so a typo shows up as a box rather than an
error:

| Shape | Default size |
|---|---|
| `rect` | 160 × 56 |
| `rounded-rect` | 160 × 56 |
| `circle` | 48 × 48 |
| `diamond` | 80 × 80 |
| `bar` | 160 × 12 |
| `container-with-header` | 200 × 120 |

Register your own with `registerShape(name, defaultSize, painter)` from the same
module.

## How it stays an extension

The `diagram` type is added the same way any custom node is — a Java class
(`@JsonTypeName("diagram")`) + a Jackson module + a TypeScript render handler —
without changing the semantic-ui core. See
[Node vocabulary → Extending the vocabulary](./node-vocabulary.md#extending-the-vocabulary).
