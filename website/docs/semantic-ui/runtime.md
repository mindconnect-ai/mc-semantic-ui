---
title: Renderer & event bus
sidebar_position: 4
---

# The renderer and the event bus

Two objects run a semantic-ui app in the browser. You create both **once**, at
startup, and then keep handing them trees.

| Object | Job | How many |
|---|---|---|
| **`SuiRenderer`** | Turns a node tree into HTML and writes it into the DOM. | One per host element. |
| **`SuiEventBus`** | Watches that DOM for triggers, fetches, applies the response. | One per renderer. |

If your app has one content area — the normal case — you have exactly one of
each, created at boot and never replaced.

## The three-line boot

```js
import { createDefaultRenderer } from "/sui/renderer.js";
import { SuiEventBus }           from "/sui/eventbus.js";

const host     = document.getElementById("app");         // 1. where it lives
const renderer = createDefaultRenderer().attach(host);   // 2. what draws
const bus      = new SuiEventBus(renderer, host);        // 3. what reacts
```

That is the whole setup. Everything after it is `mount()` or `navigate()`.

## Create → attach → mount

The three verbs are easy to confuse because they look sequential, but they run
at completely different rates:

| Step | Call | How often |
|---|---|---|
| **Create** | `createDefaultRenderer()` | **Once**, at startup. |
| **Attach** | `.attach(host)` | **Once**, right after creating. |
| **Mount** | `.mount(node)` | **Every time the screen changes.** |

**Create** builds a renderer and registers a render function for every built-in
node type. `createDefaultRenderer()` is the shorthand; the long form is
`installDefaultHandlers(new SuiRenderer())`, which is what you want when you
need a reference to the bare renderer first.

**Attach** binds the renderer to one DOM element. That element's contents become
the renderer's territory: `mount()` replaces them. Nothing else on your page is
touched, which is what makes a
[UI island](./ui-island.md) possible.

**Mount** renders a tree into the attached host. Call it whenever the screen
changes — it is cheap and idempotent, and the morpher reuses DOM nodes whose
`id`s match, so focus, scroll position and CSS animations survive a re-mount.

:::warning Two mistakes that cost an afternoon
**`mount()` without `attach()` throws** — *"SuiRenderer.mount(): no host element
attached"*. `createDefaultRenderer()` deliberately returns an unattached
renderer, so the `.attach(host)` is not optional.

**`mount()` takes a node, not a `UiPage`.** Hand it a page and it won't throw: it
renders a `<pre>` dump of your JSON and logs *"no handler for node type"*. There
is no `page` handler — a page is an envelope. Use `bus.applyPage(page)`, or let
`bus.navigate(href)` fetch and unwrap it for you.
:::

## Which method do I call?

Once the renderer is attached, four methods write to the DOM. They differ only
in *what* they replace:

| Method | Replaces | Use it when |
|---|---|---|
| `mount(node)` | the whole host's contents | The screen changed. |
| `applyPatch(patch)` | the nodes a patch targets, by `id` | You have a `UiPatch`. |
| `renderInto(el, node)` | the **contents** of `el` | You own a sub-area and want to fill it. |
| `replaceElement(el, node)` | `el` **itself**, attributes included | The element's own attributes change too. |
| `render(node)` | nothing — returns an HTML string | You want the markup and will place it yourself. |

`render()` is the odd one out and the useful escape hatch: it touches no DOM, so
you can use the renderer to produce markup for a template, an email, or a test
assertion.

## What the bus adds

Without the bus, a mounted tree is a picture: buttons do nothing. The bus
listens on the host element and gives triggers their meaning.

```js
const bus = new SuiEventBus(renderer, host);

bus.start("/products");             // fetch a UiPage and mount it
bus.navigate("/products/42");       // same, for one URL
bus.applyPage(pageObject);          // apply a page you already have
bus.applyPatch(patchObject);        // apply a patch you already have
```

Three settings matter early on:

| Call | Why |
|---|---|
| `setHistoryEnabled(false)` | **Required for islands.** Otherwise the bus pushes its own URLs into the host page's address bar. |
| `setLoadingPolicy("manual")` | Suppresses the global bar and the inline busy state — for surfaces that manage their own affordances. |
| `setFetcher(fn)` | Route every request through your own fetch — auth headers, a base URL, a mock in tests. |

## Extending the renderer

A renderer is a map from `type` to a render function, so extending it is
registering a function:

```js
renderer.register("rating", (node) =>
  `<span class="my-rating" id="${node.id}">${"★".repeat(node.stars)}</span>`);
```

That is the same mechanism the built-ins use — `installDefaultHandlers()` is a
list of 26 `register()` calls. Two consequences:

- **Registering an existing type overrides it.** Register your own `"table"` and
  every table in the app uses it, including the ones your backend emits.
- **Order doesn't matter, the last registration wins.** So install extensions
  first, then your overrides.

A packaged extension is a function that does the registering for you:

```js
import { install as installDiagram } from "/sui-ext/diagram/extension.js";

installDiagram(renderer);           // registers the "diagram" node type
```

See [step 5 of How it works](./how-it-works.md#step-5--add-a-node-type-of-your-own)
for the full walk-through with a live example, and the
[diagram extension](./diagram-extension.md) for a large one.

### The other swappable parts

Beyond node types, four seams let you change behaviour without forking:

| Seam | Call | Replaces |
|---|---|---|
| List items | `renderer.registerItemHandler(fn)` | How `UiList` items render. |
| DOM morphing | `renderer.setMorpher(fn)` | Idiomorph — bundle your own, or disable morphing. |
| Busy indicator | `renderer.setLoadingIndicator(obj)` | The default spinner overlay. |
| Icons | `setIconResolver(fn)` | The whole icon scheme — see [icon library](./icons.md). |

## A note on the JVM side

On the server the equivalent object is `SuiServerRenderer`, which walks the same
tree through Handlebars templates and returns finished HTML. It is a Spring bean
— you don't create, attach or mount anything; a controller returns a `UiPage`
and the HTTP message converter does the rest. See
[server-side rendering](./server-side-rendering.md).

## See also

- **[How it works](./how-it-works.md)** — the five steps, with live examples.
- **[Embed as a UI island](./ui-island.md)** — one renderer inside someone
  else's page.
- **[Triggers & actions](./triggers.md)** — what the bus does with a trigger.
- **[Rendering modes](./rendering-modes.md)** — SSR, SPA, and patches.
