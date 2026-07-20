---
title: List
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `list` — a collection of items, each its own shape

**`UiList`** renders a `<ul>` of items. Each item is a small record in its own
right: a label (plain or a whole node), a leading icon, a muted description,
per-item actions, and — the reason `UiList` exists — **any `UiNode` as
`content`**. A list item can hold a form, a table, a chart or a nested stack.

Reach for `UiList` when the items are not a neat grid: activity feeds, search
results, agent tool-call cards, anything where the rows differ from each other
or carry rich bodies. When every row has the same columns and you want sorting,
selection and stack-on-mobile, use [`table`](./table.md) instead.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=list"
  title="Live: UiList"
  loading="lazy"
  style={{width: '100%', height: '460px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — items with an icon plus description and a per-item action, one item
whose header is a rich `labelNode`, one collapsible item whose body is a whole
stack, and a pagination footer.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id and DOM `id` of the wrapping `<div class="sui-list">`. |
| `title` | `String` | Rendered as the `<h2>` in the list header. Omitted when absent. |
| `items` | `List<UiList.Item>` | The rows. Defaults to an empty list. See below. |
| `actions` | `List<UiAction>` | List-level buttons in the header, next to the title (Refresh, Add, …). Defaults to an empty list. |
| `pagination` | `UiList.Pagination` | Optional pager rendered under the items. `null` = no pager. |
| `cssClass` | `String` | Extra CSS class added next to `sui-list`. |

### Item fields

`UiList.Item` is a plain nested class, **not** a `UiNode` — it has no `type`
discriminator and no `cssClass`.

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Item id. Becomes the `<li>` id *and* its `data-id`, so a single item can be patched on its own. |
| `label` | `String` | The item headline as plain text. Kept as the accessible fallback even when `labelNode` is set. |
| `labelNode` | `UiNode` | Rich header: rendered *instead of* the plain label text. Lets a row title carry structure — a name plus a status badge, an icon plus a [`menu-button`](./menu-button.md). |
| `icon` | `String` | Leading icon token before the plain label. **Ignored when `labelNode` is set** — a rich header owns its own layout. See [icons](./icon.md). |
| `description` | `String` | Muted second line. **Only rendered when `content` is absent** — `content` takes the same slot. |
| `onClick` | `UiTrigger` | Fired when the item's label is clicked. `null` = a static, non-clickable label. |
| `actions` | `List<UiAction>` | Per-item buttons, rendered in their own trailing cell. Defaults to an empty list. |
| `content` | `UiNode` | Any node, rendered as the item's body. Wins over `description`. |
| `collapseSummary` | `String` | When set, the whole item body is wrapped in a `<details>` disclosure with this text as the `<summary>`. |
| `collapseOpen` | `boolean` | Defaults to `false`. `true` renders the disclosure open. Ignored when `collapseClientControlled` is set. |
| `collapseSummaryId` | `String` | Puts an id on the `<summary>` text so a patch can REPLACE just the summary (flip "running" to "done") without touching the body. |
| `collapseClientControlled` | `boolean` | Defaults to `false`. `true` renders collapsed and tags the element `data-sui-client-collapse`, so the user's manual expand/collapse survives re-renders and streaming patches. |

### Pagination

`UiList.Pagination` is informational unless you give it a trigger template.

| Field | Type | Meaning |
|---|---|---|
| `page` | `int` | Current page, 1-based. |
| `size` | `int` | Items per page. The renderer derives the page count as `ceil(total / size)`. |
| `total` | `long` | Total number of items across all pages. |
| `pageTrigger` | `UiTrigger` | Template fired when a page button is clicked. The renderer substitutes the literal `{page}` in the trigger's `url` with the target page number. **`null` renders both buttons disabled** — the pager becomes a read-out. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiList.of("activity", "Recent activity")
    .action(UiAction.secondary("l-refresh", "Refresh")
            .onClick(UiTrigger.api("POST", "/activity/refresh")))

    // icon + description + a per-item action
    .item(UiList.Item.of("l1", "Order #1024 shipped")
            .icon("success")
            .description("web-frontend · 2 minutes ago")
            .onClick(UiTrigger.go("/orders/1024"))
            .action(UiAction.secondary("l1-show", "Show").icon("show")
                    .appearance(UiAction.Appearance.ICON)
                    .onClick(UiTrigger.go("/orders/1024"))))

    // a rich header instead of a plain label
    .item(UiList.Item.of("l3", "Import job")
            .labelNode(UiStack.of(
                    UiIcon.of("l3-icon", "document"),
                    UiText.of("l3-title", "Import job"),
                    UiText.of("l3-badge", "· running"))
                .direction(UiStack.Direction.HORIZONTAL).gap(8))
            .description("3 of 8 files processed"))

    // a collapsible item whose body is a whole node
    .item(UiList.Item.of("l4", "Nightly build")
            .collapsible("Nightly build — 3 warnings (click to expand)", false)
            .content(UiText.of("l4-1", "Build passed with 3 lint warnings.")))

    // a live card whose open state belongs to the user, not the server
    .item(UiList.Item.of("l5", "Tool call")
            .collapsibleClient("search_web — running…", "l5-sum")
            .content(UiText.of("l5-body", "")))

    .paginate(1, 4, 42, UiTrigger.api("GET", "/activity?page={page}"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "list", "id": "activity", "title": "Recent activity",
  "actions": [
    { "type": "action", "id": "l-refresh", "label": "Refresh", "style": "SECONDARY",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "POST", "url": "/activity/refresh" } }
  ],
  "items": [
    { "id": "l1", "label": "Order #1024 shipped", "icon": "success",
      "description": "web-frontend · 2 minutes ago",
      "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/orders/1024" },
      "actions": [
        { "type": "action", "id": "l1-show", "label": "Show", "icon": "show",
          "appearance": "ICON", "style": "SECONDARY",
          "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET", "url": "/orders/1024" } }
      ] },

    { "id": "l4", "label": "Nightly build",
      "collapseSummary": "Nightly build — 3 warnings (click to expand)",
      "collapseOpen": false,
      "content": { "type": "text", "id": "l4-1",
                   "text": "Build passed with 3 lint warnings." } },

    { "id": "l5", "label": "Tool call",
      "collapseSummary": "search_web — running…", "collapseSummaryId": "l5-sum",
      "collapseClientControlled": true,
      "content": { "type": "text", "id": "l5-body", "text": "" } }
  ],
  "pagination": { "page": 1, "size": 4, "total": 42,
    "pageTrigger": { "behavior": "APPLY_RESPONSE", "method": "GET",
                     "url": "/activity?page={page}" } } }
```

</TabItem>
</Tabs>

## Notes

**`list` or `table`?** If every row has the same fields and the user wants to
compare them column by column, it is a [`table`](./table.md) — that is where
sorting, filtering, row selection and `stackOnMobile` live. If the rows differ,
or any row needs a body richer than a string, it is a `UiList`. Items are also
individually addressable by id, which makes a list the better fit for feeds that
grow by APPEND.

**`content` replaces `description`, `labelNode` replaces `icon`.** These are the
two overrides worth memorising: the renderer picks `content` over `description`
for the body slot, and `labelNode` over `icon` + `label` for the header slot.
Setting both members of a pair is not an error — the loser is simply never
drawn, though `label` is still kept as the plain-text fallback.

**Client-controlled collapse is for live content.** A server-driven `open`
attribute fights the user every time a patch arrives: they collapse a card, the
next re-render springs it open again. `collapsibleClient(...)` hands the state
to the browser and tags the element so the morpher leaves it alone — which is
exactly what streaming tool-call and sub-agent cards need. Pair it with
`collapseSummaryId` so the summary can flip from "running…" to "done" via a
targeted REPLACE while the body keeps streaming.

**Pagination without a `pageTrigger` is decoration.** The renderer emits both
page buttons disabled when no template is given, which is a fine read-out but
catches people out. Pass the trigger with a literal `{page}` in the URL and the
renderer substitutes it per button — no per-page node building on the server.

**Patch the item, not the list.** Each `<li>` carries the item id, so a status
change is one `REPLACE` on that item — not a re-render of the whole collection.
That keeps scroll position, focus and every other item's disclosure state
intact.

## See also

- **[`table`](./table.md)** — same data, grid semantics.
- **[`detail`](./detail.md)** — a single record instead of a collection.
- **[`action`](./action.md)** — list-level and per-item buttons.
- **[`text`](./text.md)** — the usual `content` of a simple item.
- **[Triggers cookbook](../triggers-cookbook.md)** — item clicks, paging, streaming into an item.
