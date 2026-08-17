---
title: Scroll pane
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `scrollpane` — a scrolling viewport

**`UiScrollPane`** wraps one child in a scrolling viewport — the element to
reach for when a feed or a long list should scroll *inside* the page instead
of growing it: chat threads, live run logs, the master list of a
master-detail.

Two height modes:

- **`maxHeight` set** — the pane caps at that CSS length in normal flow
  (`"60vh"`, `"400px"`).
- **`maxHeight` absent** — the pane fills the leftover space of a
  flex-column parent. This is the chat-page layout: header on top, composer
  at the bottom, the pane takes everything in between.

`stickToLatest` turns the pane into a **live feed**: as long as the user is
at (or near) the bottom, new content keeps the view pinned to the newest
entry. The moment they scroll up to read, the sticking stops — nothing yanks
the view while reading — and a floating **jump-to-latest arrow** appears.
Clicking it (or scrolling back down) re-arms the stick. The behaviour is
wired client-side by the event bus's auto-enhance; with no JavaScript the
pane simply scrolls.

A [`list`](./list.md) placed directly inside a pane keeps its header row
(title, header `icon`, `headerExtra`, actions) **sticky** at the top while
the items scroll — the list header doubles as the pane's toolbar.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `content` | `UiNode` | The scrolled child. Any node; usually a [`list`](./list.md) or [`stack`](./stack.md). |
| `maxHeight` | `String` | CSS length capping the pane's height. Absent = fill the flex parent. |
| `stickToLatest` | `Boolean` | Live-feed mode: pin to the newest content + jump-to-latest arrow. |
| `cssClass` | `String` | Extra CSS class added next to `sui-scrollpane`. |

The rendered element is `<div class="sui-scrollpane" data-stick-latest>` —
the marker attribute is what the auto-enhance pass looks for.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// a chat thread: fills the space between header and composer,
// follows new messages, arrow appears when scrolled up
UiScrollPane.of("chat-scroll", messageList)
    .stickToLatest(true);

// a capped log excerpt inside a normal page
UiScrollPane.of("run-log", logList)
    .maxHeight("50vh")
    .stickToLatest(true);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "scrollpane", "id": "chat-scroll",
  "stickToLatest": true,
  "content": { "type": "list", "id": "messages", "items": [] } }
```

</TabItem>
</Tabs>

## Patching into a live feed

The pane itself is static — the *content* is what changes. `APPEND` new
entries into the child list (or `REPLACE` the child wholesale); the pane's
observer sees the mutation and, if the user was at the bottom, follows it.
Because the pane element survives a `REPLACE` of its content, the wiring
survives too — nothing to re-arm after a patch.

## Notes

- The arrow rides in a zero-height `position: sticky` host pinned to the
  pane's lower edge, so it floats over the content instead of scrolling away
  with it.
- Legacy escape hatch: a container that scrolls in a *descendant* (not
  itself) can opt into the same behaviour with the `sui-autoscroll` CSS
  class — the enhancer then attaches to the first scrollable descendant.
  Prefer the real node.
