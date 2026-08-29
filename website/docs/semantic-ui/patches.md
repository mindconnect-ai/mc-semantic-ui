---
title: 'Patches & visibility'
sidebar_position: 5
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Patches: changing part of a page

A [trigger](./triggers.md) that answers with a **`UiPage`** replaces the screen.
A trigger that answers with a **`UiPatch`** changes part of it and leaves the
rest alone — the DOM, the scroll position, the cursor in a half-typed field.

A patch is a list of operations, applied **in order**, each naming a node by
`id`:

```java
UiPatch.of()
        .patch(UiPatch.Operation.replace("product-table", newTable))
        .patch(UiPatch.Operation.hide("empty-state"));
```

```json
{ "patches": [
  { "op": "REPLACE", "targetId": "product-table", "node": { "…": "…" } },
  { "op": "MERGE",   "targetId": "empty-state", "attributes": { "display": "HIDDEN" } }
] }
```

## The five operations

| Op | Carries | Does |
|---|---|---|
| `REPLACE` | a whole `node` | Swaps the target for it. |
| `MERGE` | an `attributes` map | Changes the fields it names, keeps the rest. |
| `APPEND` | a whole `node` | Adds it inside the target. |
| `REMOVE` | nothing | Takes the target off the page. |
| `CLEAR` | nothing | Empties the target, keeps the container. |

All five work the same in all three renderers — browser SPA, JavaFX desktop,
and (for the ones that make sense without a client) server-side rendering.

## `MERGE`: change what you name

`REPLACE` needs the whole node. To grey out one button, the server rebuilds and
resends the subtree it sits in, and the client throws away the one it had.

`MERGE` names the fields that changed:

```java
UiPatch.of().patch(UiPatch.Operation.merge("save", Map.of(
        "enabled", false,
        "label",   "Saving…")));
```

The button's `id`, icon, confirmation prompt and `onClick` trigger are never
mentioned, so none of them travels — and none of them has to be rebuilt
correctly on the server to survive the round-trip. **That is the real
argument.** The byte count is a side effect; not having to know the parts you
are not changing is the point.

:::tip What `MERGE` is not
It is not a compression trick. For a small node the saving is unimpressive. It
earns its place when the node you are patching is expensive to rebuild, or when
the server does not actually know what the rest of it looked like.
:::

### Clearing a field

An explicit `null` **clears** a field rather than being ignored:

```java
UiPatch.Operation.merge("badge", Collections.singletonMap("icon", null));
```

Being able to set a state but never clear it would be half a feature. Note
`Collections.singletonMap` rather than `Map.of` — `Map.of` rejects null values.

### It is shallow

Naming a field replaces it **whole**. Merging `items` swaps the entire list; it
does not reconcile entries. Deep-merging arrays is a much bigger promise than
this operation makes, and it would need a rule for identity that the model does
not carry.

## Visibility: `hide()` and `show()`

The commonest merge of all has its own shorthand:

```java
UiPatch.Operation.hide("filters");    // out of the layout
UiPatch.Operation.show("filters");    // back again
```

Both are merges of the single `display` attribute. There are **two ways to
hide**, and the difference matters:

| | Web | JavaFX | Behaves like |
|---|---|---|---|
| `Display.HIDDEN` | `.sui-hidden` → `display: none` | not visible, not managed | The node is gone; everything below moves up. |
| `Display.BLANK` | `.sui-blank` → `visibility: hidden` | not visible, still managed | The node is invisible; its space stays. |

```java
UiPatch.Operation.hide("toolbar", UiNode.Display.BLANK);   // keeps its space
```

Reach for `BLANK` when something around the node would jump — a toolbar that
appears on hover, a spinner that swaps with a value. Reach for `HIDDEN` when the
node genuinely should not be taking up room.

### On the node itself

The same state is a property of any node, so a page can arrive with something
already hidden:

```java
UiText.of("warning", "Disk almost full").hidden();
UiText.of("warning", "Disk almost full").blank();
UiText.of("warning", "Disk almost full").visible();   // the default
```

:::note `display` is the source of truth
The renderers fold `display` into a CSS class (`sui-hidden` / `sui-blank`) so a
plain stylesheet can act on it. Do not set those classes yourself through
`cssClass` — `setCssClass` strips them precisely so the two cannot disagree.
Set `display`, and let the class be derived.
:::

## Where a merge finds the rest of the node

A merge only says what changed, so **the client has to already know the rest.**
Where it knows it from depends on how the page was delivered.

<Tabs groupId="delivery">
<TabItem value="spa" label="Client-rendered (SPA)">

The renderer drew the tree, so it remembers what every `id` was built from. A
merge works immediately, on any node on the page.

The index is refilled by `mount()` — a new page means the old ids no longer mean
anything — and an id is forgotten when a `REMOVE` takes it off the page.

</TabItem>
<TabItem value="hybrid" label="Server-rendered + SPA bus (hybrid)">

The page arrived as finished HTML. The client never drew it, so it knows what
every element *looks like* and nothing about what any of them *are*.

For this case the server parks the page's own model at the end of the body:

```html
<script type="application/json" id="sui-model">{"type":"stack","id":"page", …}</script>
```

The bus reads it at startup and seeds the renderer, so a merge works on the very
first patch after a fresh load. Only hybrid pages carry it — a pure-SSR page has
no client to read it.

</TabItem>
<TabItem value="fx" label="JavaFX">

The painted control carries its own model, so a merge finds it the same way and
needs no equivalent of the embedded blob.

</TabItem>
</Tabs>

If a merge names a target the client has no model for, it is **reported rather
than silently ignored** — a warning in the browser console, the error handler on
JavaFX. A merge that quietly does nothing is worse than one that says why.

## Merging inside a table

Rows and columns are nodes with ids, so they can be merged like anything else:

```java
UiPatch.Operation.merge("row-42", Map.of("data", updatedRow));
```

A table keeps its own model, and a merge on a row goes through it: the table
re-renders so header, cells, `cellTemplate`s and selection state stay
consistent. A lone `<tr>` swap could not do that.

## Compatibility

`MERGE` arrived after `REPLACE`, `APPEND`, `REMOVE` and `CLEAR`. A client older
than the operation will not understand it — if you serve a mix of versions, keep
to `REPLACE` until the floor has moved.

## Try it

`mc-sui-merge-demo` in the repository is a page built to be poked at: hide and
show a panel, toggle a button's own label and style, and read what each click
actually put on the wire beside the `REPLACE` that would have done the same
thing.

```bash
mvn -f demo/mc-sui-merge-demo/pom.xml spring-boot:run   # http://localhost:9093
```
