---
title: Kanban extension
sidebar_position: 4
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# The kanban extension

**`mc-semantic-ui-ext-kanban` brings a board**: lanes side by side, each
holding cards, and the one thing a page cannot do without JavaScript — a card
dragged from one lane to another, with the server told where it went.

Like the [chart extension](./chart-extension.md) it ships both halves: the
node types (`UiKanban`, `UiKanbanLane`, `UiKanbanCard`) *and* the painters —
three Handlebars templates for the server, a browser bundle that draws the
same markup and adds the dragging. Add the module and the board appears in the
Java model, in Jackson, in the browser renderer and in SSR at once.

## What you get

| | |
|---|---|
| Node types | `kanban` (`UiKanban`), `kanban-lane` (`UiKanbanLane`), `kanban-card` (`UiKanbanCard`) |
| Dragging | a card to another lane, or to another place in its own lane — HTML5 drag and drop, mouse and pen |
| The server hears | `onMove`, a trigger whose URL may carry `{card}`, `{from}`, `{to}` and `{index}` |
| Rules a lane can set | `limit` (a full lane takes no card), `locked` (no card lands here) |
| Works without JavaScript | **yes** as a picture — rendered server-side; dragging needs the browser bundle |

## Install

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java / server-side">

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-ext-kanban</artifactId>
  <version>0.3.5</version>
</dependency>
```

Nothing else to configure: the module ships its templates and registers its
node types through Spring Boot auto-configuration, or via `ServiceLoader` in a
plain-Java app.

```java
@GetMapping("/board")
public UiPage board() {
    var board = UiKanban.of("board",
            UiKanbanLane.of("todo", "To do",
                    UiKanbanCard.of("t1", "Write the spec").badge("P1").tag("docs")
                            .onClick(UiTrigger.go("/cards/t1"))),
            UiKanbanLane.of("doing", "Doing").limit(3),
            UiKanbanLane.of("done", "Done"))
        .onMove(UiTrigger.api("POST", "/board/move?card={card}&from={from}&to={to}&index={index}"));
    return UiPage.of("/board", board);
}

@PostMapping("/board/move")
public UiPatch move(@RequestParam String card, @RequestParam String from,
                    @RequestParam String to, @RequestParam int index) {
    boards.move(card, from, to, index);
    return UiPatch.toast("Moved.");          // or REPLACE the board / MERGE a lane
}
```

</TabItem>
<TabItem value="browser" label="Browser">

```html
<link rel="stylesheet" href="/sui/sui.css">
<link rel="stylesheet" href="/sui-ext/kanban/kanban.css">

<script type="module">
  import { createDefaultRenderer } from "/sui/renderer.js";
  import { SuiEventBus } from "/sui/eventbus.js";
  import { install as installKanban } from "/sui-ext/kanban/extension.js";

  const root = document.getElementById("app");
  const renderer = createDefaultRenderer().attach(root);
  const bus = new SuiEventBus(renderer, root);
  installKanban(renderer, { bus });   // registers the three handlers; drops go through the bus

  bus.start("/board");
</script>
```

`installKanban(renderer, { bus })` is scoped to that renderer. Hand it the bus
and a drop fires the board's `onMove` through it, with the lane marked busy
until the server has answered and the response applied like any other. Without
a bus the board still emits a `sui-kanban-move` event on every drop
(`detail: { card, from, to, index }`), for an app that handles the move itself.

</TabItem>
</Tabs>

## How a move reaches the server

The board does not send a body of its own. It takes the `onMove` trigger and
fills four placeholders in its URL at drop time — the substitution a table
applies to `{id}` in a row action, done when the card lands:

| Placeholder | Filled with |
|---|---|
| `{card}` | the card's id |
| `{from}` | the id of the lane it left |
| `{to}` | the id of the lane it landed in — the same as `{from}` when it only changed place |
| `{index}` | its new position in that lane, zero-based |

The card moves in the page at once, and the lane counts follow. Whatever the
server answers settles it: a toast, a `REPLACE` of the board, a `MERGE` of the
lanes involved. A drop that changes nothing — the card let go where it was —
sends nothing.

## The nodes

`UiKanban`:

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `lanes` | `List<UiKanbanLane>` | The lanes, left to right. |
| `onMove` | `UiTrigger` | Fired when a card has been dropped somewhere else. |
| `readOnly` | `boolean` | Nothing can be dragged; the board is a picture of its state. |

`UiKanbanLane`:

| Field | Type | Meaning |
|---|---|---|
| `id`, `title` | `String` | Id and heading. |
| `cards` | `List<UiKanbanCard>` | The cards, top to bottom. |
| `limit` | `Integer` | Work-in-progress limit — shown as `n/limit`, and a full lane takes no card. |
| `locked` | `boolean` | No card can be dropped here; its own cards can still leave. |
| `color` | `String` | Accent colour along the lane's top edge — a hex value, a name, `rgb()`/`hsl()` or `var(--…)`; anything else is dropped. |

`UiKanbanCard`:

| Field | Type | Meaning |
|---|---|---|
| `id`, `title` | `String` | Id and headline. |
| `description` | `String` | A line or two under the title. Plain text. |
| `badge` | `String` | Short marker beside the title — a priority, a count, an initial. |
| `tags` | `List<String>` | Small labels along the bottom edge. |
| `color` | `String` | Accent colour along the card's left edge — a hex value, a name, `rgb()`/`hsl()` or `var(--…)`; anything else is dropped. |
| `locked` | `boolean` | The card stays put; it cannot be dragged. |
| `onClick` … | `UiTrigger` | The node-level events every node carries — a click opens the card's detail, say. |

Lanes and cards are nodes with ids of their own, so a patch can `REPLACE` one
card, `MERGE` a lane's `cards`, or `REMOVE` a card — the same way a tree row
or a table row is addressed.

### Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiKanban.of("board",
        UiKanbanLane.of("todo", "To do",
                UiKanbanCard.of("t1", "Write the spec").description("Two pages, no more.")
                        .badge("P1").tag("docs").color("#e0a300"),
                UiKanbanCard.of("t2", "Sprint goal").locked(true)),
        UiKanbanLane.of("doing", "Doing").limit(3).color("#4f6bed"),
        UiKanbanLane.of("done", "Done").locked(true))
    .onMove(UiTrigger.api("POST", "/board/move?card={card}&from={from}&to={to}&index={index}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "kanban", "id": "board",
  "onMove": { "url": "/board/move?card={card}&from={from}&to={to}&index={index}", "method": "POST" },
  "lanes": [
    { "type": "kanban-lane", "id": "todo", "title": "To do", "cards": [
      { "type": "kanban-card", "id": "t1", "title": "Write the spec",
        "description": "Two pages, no more.", "badge": "P1", "tags": ["docs"], "color": "#e0a300" },
      { "type": "kanban-card", "id": "t2", "title": "Sprint goal", "locked": true } ] },
    { "type": "kanban-lane", "id": "doing", "title": "Doing", "limit": 3, "color": "#4f6bed", "cards": [] },
    { "type": "kanban-lane", "id": "done", "title": "Done", "locked": true, "cards": [] } ] }
```

</TabItem>
</Tabs>

## Styling

Everything is built on the core's tokens, so every theme applies without a
line of your own. The one knob of the extension's own is `--sui-kanban-accent`:
a lane or card carrying a `color` sets it inline, and the stylesheet draws it
along the lane's top edge and the card's left edge. The states a page can
style are `is-dragging` on the card in flight, `is-drop-target` on the lane
under the pointer, and `is-loading` on the lane whose move the server is still
answering.

## Both renderers draw the same board

The markup exists twice — `kanban.hbs`, `kanban-lane.hbs`, `kanban-card.hbs`
on the server and `kanban/extension.ts` in the browser — and they are held to
the same bytes by one fixture (`board.json` → `board.expected.html`) that both
test suites render and compare. The `<sui-kanban>` element the templates emit
is what the browser bundle upgrades; on a page without it, it is an ordinary
styled block.

## Limits

Dragging uses the browser's own drag and drop, which mouse and pen support
and touch screens largely do not. Give a card an `onClick` that opens a
"move to" choice for those, and for keyboard users.
