---
title: Reading the screen, and acting on it
sidebar_position: 6
---

# Reading the screen, and acting on it

The event bus draws the page and, when something is clicked, sends what the
screen holds. Two more things it can do: **say what is on the screen**, and
**do what a click does, from the outside**.

Both are useful without an agent — a test that asserts on a screen, a support
session that needs to know what someone is actually looking at, a demo button
that drives the app. With an agent they are the whole interface: the agent
reads a snapshot and sends back a command, and nothing has to rebuild the
screen state on the server.

The bus knows nothing about transport. There is no SSE, no channel, no server
protocol in here — two methods, and the application wires them to its own way
of getting messages in and out.

## The bus keeps a copy of the tree

`SuiRenderer.mount()` gets the page as a `UiNode`, and every patch since is
applied to that copy as well: `REPLACE` swaps a subtree, `MERGE` writes over
one, `APPEND` adds a child, `REMOVE` drops one. So `renderer.tree()` and the
DOM say the same thing, and the snapshot below does not have to parse HTML
back into a model.

A server-rendered page seeds the same copy from its `<script id="sui-model">`,
so a hybrid page can be read from the first paint.

```js
renderer.tree();   // the page as it stands, patches included — or null
```

The tree handed to `mount()` is kept, not copied, and the patches write into
it. Mount a page your code is done with — one built per render, or a response
just parsed — rather than a literal you intend to mount again. When a patch
names a node the copy does not have (a server-rendered page with no
`sui-model`, say), the console says so once: the screen changed and the copy
did not, so a snapshot would show the old node.

## `bus.snapshot(options)` — what is on the screen

```js
const shot = bus.snapshot({ root: "email-shell", depth: 4, mode: "outline", maxChars: 20000 });
```

| Option | Default | What it does |
|---|---|---|
| `root` | the whole page | Only this subtree. |
| `depth` | 20 | How many levels below the root are walked. |
| `mode` | `"outline"` | `"outline"` or `"full"`. |
| `maxChars` | 20000 | Ceiling on the serialized answer. |

The answer names the bus that gave it, and says when something was left out:

```json
{
  "busId": "sui-root",
  "mode": "outline",
  "truncated": true,
  "node": { "…": "the outline" }
}
```

### The outline

The outline is what someone acting on this screen needs — what it is called,
what can be read, what can be typed into, what can be pressed. Styling,
triggers and layout hints are left out.

```json
{ "id": "email-list", "type": "list", "title": "All inboxes",
  "items": [ { "id": "pick-1", "label": "Techpresso", "description": "Apple unveils…", "clickable": true } ],
  "fields": [ { "id": "q", "label": "Search", "value": "rechnung", "type": "TEXT" } ],
  "actions": [ { "id": "delete-picked", "label": "Delete the ticked messages",
                 "style": "DANGER", "confirm": "Delete 3 messages?", "enabled": true } ],
  "children": [] }
```

A table says what it shows: its column headings, and a row per line with the
cells keyed by column — the value under the column's `dataKey` where it has
one. A row that can be ticked reports its tick, read from the screen, and a
paginated table says which page this is.

```json
{ "id": "orders", "type": "table", "title": "Orders",
  "columns": [ { "id": "customer", "label": "Customer" }, { "id": "total", "label": "Total" } ],
  "items": [ { "id": "r1", "cells": { "customer": "Ada Lovelace", "total": "42.00" }, "selected": true },
             { "id": "r2", "cells": { "customer": "Alan Turing", "total": "17.50" }, "selected": false } ],
  "pagination": { "page": 1, "size": 20, "total": 84 },
  "actions": [ { "id": "export", "label": "Export CSV", "enabled": true } ] }
```

What the shape is made of:

- **Values come from the DOM, not from the model.** A field that was typed
  into reads back what it says now; a rich-text field gives the editor's
  content, a checkbox its tick. `enabled` is likewise what the screen says: a
  button disabled on the page reports `enabled: false` whatever the model
  claims, and a drawer reports the `state` the user put it in.
- **Secrets are left out.** A `PASSWORD` or `FILE` field, and any field whose
  id or label reads like a token (`_csrf`, `apiKey`, `authorization`, …),
  comes back as `"omitted": true` with no value.
- **A node with no id and nothing to say does not get a level.** Layout
  wrappers disappear and their children move up, so the outline is about as
  deep as the screen looks.
- **`depth` and `maxChars` are kept**, and whatever they cut is marked
  `"truncated": true` — on the node whose content was cut, and on the answer
  as a whole. A large screen cannot produce a half-megabyte reply.

`mode: "full"` returns the nodes as they were rendered — every model field, for
debugging — with the same omissions for secrets and the same live values.

## `bus.perform(command)` — doing what a click does

```js
const result = await bus.perform({
    fields: { q: "rechnung" },
    action: "search",
    confirmed: true,
});
```

First the fields are filled in — as typing does, `input` and `change` events
included, so a rich-text editor and a field's `onChange` both notice — then
the action fires, down the same path a real click takes: busy state, request,
patch, toast. A menu entry, a row with `onClick` and a form button are all the
same thing here: a trigger with an id.

`confirmed: true` answers the action's `confirm` question with yes. Without
it, an action that asks still asks, in the browser — the agreement was
obtained somewhere else, and the bus is told so rather than guessing.

Nothing fires unless everything asked for is there; when it does not, the
answer says why:

```json
{ "ok": false, "triggered": false, "busId": "sui-root",
  "action": "delete-picked", "reason": "disabled",
  "message": "\"delete-picked\" cannot be used right now: pick a message first" }
```

| `reason` | Means |
|---|---|
| `unknown-field` | No field with that id on the screen. Nothing was fired. |
| `unknown-action` | No action with that id. |
| `disabled` | It is there, but disabled or busy — `message` carries what the screen says about it. |
| `no-trigger` | It is there and enabled, but nothing is wired to it. |
| `cancelled` | It asked, and the answer was no. |

A command with `fields` and no `action` just fills them in
(`{ ok: true, triggered: false }`).

## Nothing is wired

Both methods are just methods. There is no listener, no endpoint, no stream
subscription that calls them: a server reaches them only through a bridge the
application builds. That is deliberate, and it is the place to decide who may
do what — the library has no switch, because a switch would suggest the
methods are reachable without one.

What to weigh when you build that bridge:

- **A snapshot collects what has not been submitted.** Values the user has
  typed and not sent are in it. Secrets are left out (see above), but the rest
  is the screen as it stands.
- **`confirmed: true` skips the question.** Send it only where the agreement
  was actually obtained, from a person, for that action. Without it the
  browser asks, which is the safe default.
- **A `DANGER` action is marked as one**, and so is an action with a
  `confirm` — the snapshot reports both, so a bridge can treat them
  differently from the rest without knowing the application.
- **Two things are on whatever you wire.** `suiBuses()` / `suiBus(id)` find
  every bus on the page, and `sui-tree-changed` fires on the document (with
  the changed node's id and the operation, no content). Neither gives a
  same-origin script anything it could not already do to the DOM.

## Which bus answered

```js
bus.id();                    // "sui-root" — the root element's id, or "sui-bus-N"
bus.setId("mail-window");    // rename it
```

The id is also on the root element as `data-sui-bus`, and the module exports
`suiBuses()` and `suiBus(id)` — so a page with two buses (an app and an
embedded widget) can address them one at a time.

Each bus stays on its own screen: `snapshot()` reads values only from the
elements below its root (and the dialogs it opened), and `perform()` will not
press a button that belongs to the other bus — it answers `unknown-action`,
exactly as it would for an id that is nowhere on the page.

## Telling someone the screen changed

Every mount and every patch fires `sui-tree-changed` on the `document`, naming
the node that changed:

```js
document.addEventListener("sui-tree-changed", e => {
    console.log(e.detail);   // { id: "email-list", op: "MERGE" }
});
```

An application that mirrors the screen somewhere else can listen instead of
polling — send on change, rather than waiting to be asked.

## Seeing it work

The [widget showcase ↗](pathname:///widget-demo/) has an
**Agent** tab: a small screen, a button that prints
`bus.snapshot({ root: "ag-screen", depth: 4 })`, and one that runs
`bus.perform({ fields: { "ag-q": "rechnung" }, action: "ag-search" })` and
shows the answer. Type into the search field first — what you typed is in the
snapshot, and the API token beside it is not.
