---
title: Tree
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `tree` — recursive, expandable node lists

**`UiTree`** is a list of root [`tree-node`](./tree-node.md)s, each of which may hold
its own children to any depth. It is the shape you reach for whenever the data is
hierarchical: a file explorer, an org chart, a category browser, a nested config.

The tree itself is thin — it holds `nodes` and a `title`, and everything interesting
lives in the rows. Expand/collapse is handled in the browser by the renderer and the
event bus; the server only decides the *initial* state.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=tree"
  title="Live: UiTree"
  loading="lazy"
  style={{width: '100%', height: '360px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — expand or collapse a folder by clicking its twisty (`src`, `test`,
`components`). Clicking a file name fires that row's `onClick` instead of toggling.
`Tree.ts` is `selected`.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `title` | `String` | Optional heading rendered above the list (`<h2>`). |
| `nodes` | `List<UiTreeNode>` | The root rows. Defaults to an empty list. |
| `cssClass` | `String` | Extra CSS class on the wrapper `<div>`. |

That is the whole model. Every other knob — icons, labels, click behaviour, nested
content — belongs to [`tree-node`](./tree-node.md).

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiTree.of("tree-explorer", "File explorer")
    .node(UiTreeNode.of("t-src", "src").icon("folder").open(true)
        .child(UiTreeNode.of("t-main", "main").icon("folder").open(true)
            .child(UiTreeNode.of("t-app", "app.ts").icon("document")
                .onClick(UiTrigger.go("/files/app.ts")))
            .child(UiTreeNode.of("t-tree", "Tree.ts").icon("document").selected(true)
                .onClick(UiTrigger.go("/files/Tree.ts"))))
        .child(UiTreeNode.of("t-test", "test").icon("folder")
            .child(UiTreeNode.of("t-spec", "app.spec.ts").icon("document")
                .onClick(UiTrigger.go("/files/app.spec.ts")))))
    .node(UiTreeNode.of("t-readme", "README.md").icon("document")
        .onClick(UiTrigger.go("/files/README.md")));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "tree", "id": "tree-explorer", "title": "File explorer",
  "nodes": [
    { "type": "tree-node", "id": "t-src", "label": "src", "icon": "folder", "open": true,
      "children": [
        { "type": "tree-node", "id": "t-app", "label": "app.ts", "icon": "document",
          "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/files/app.ts" } },
        { "type": "tree-node", "id": "t-tree", "label": "Tree.ts", "icon": "document",
          "selected": true,
          "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/files/Tree.ts" } }
      ] },
    { "type": "tree-node", "id": "t-readme", "label": "README.md", "icon": "document",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/files/README.md" } }
  ] }
```

</TabItem>
</Tabs>

## Notes

**Expand/collapse is client-owned.** Each expandable row renders as a native
`<details>` tagged `data-sui-client-collapse`. The morpher preserves that state
across re-renders and streaming patches, so a user's manual open/close is *not*
undone when the server pushes new data. `open` on a node is only the starting point
— do not try to drive the disclosure from the server afterwards.

**Every row is patch-addressable.** `tree-node` is a full `UiNode` with its own id,
not a nested value object. That means a `REPLACE` patch on one node id re-renders
exactly that row and its subtree — the natural way to stream in children lazily, or
to flip a status badge deep inside a big tree without resending the whole thing.

**Clicking a label does not toggle the row.** The bus calls `preventDefault()` on
`[data-trigger]` clicks, so a label with an `onClick` fires its trigger and leaves
the disclosure alone; toggling happens on the twisty and the rest of the summary.
Leaf rows have no disclosure at all.

**SSR falls back to JSON.** There is no Handlebars template for the tree yet, so a
server-side rendering pass emits a `<pre>` dump of the node instead of markup. Use
the tree on pages that run the TypeScript renderer (SPA or UI island); see
[rendering modes](../rendering-modes.md).

## See also

- **[`tree-node`](./tree-node.md)** — the rows, and everything they can carry.
- **[`list`](./list.md)** — flat collections that do not nest.
- **[`menu`](./menu.md)** — navigation hierarchies, not data hierarchies.
- **[Triggers & actions](../triggers.md)** — what a row's `onClick` can do.
