---
title: Section entry
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `section-entry` — one tab and its panel

**`UiSectionEntry`** is a single tab inside a [`section`](./section.md): the tab
label and icon on one side, the `content` node that becomes the panel body on
the other. It is a real `UiNode` with its own `type` discriminator, which is why
the editor can select, edit and delete a tab with exactly the same code path as
any other node.

You rarely construct one by hand — `UiSection.section(...)` builds them for you —
but the entry is where `icon`, `href` and `onClick` live.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=section-entry"
  title="Live: UiSectionEntry"
  loading="lazy"
  style={{width: '100%', height: '300px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — an entry has no standalone screen presence, so this is a small `section`
whose three entries each show one thing an entry carries: title only, title plus
icon, and an entry with an `onClick` trigger that fires alongside the switch.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Tab id — the panel's DOM `id`, the value `initialSection` matches, and the patch target for the panel. |
| `title` | `String` | The tab label. |
| `content` | `UiNode` | The body shown when this tab is active. Any node. |
| `icon` | `String` | Icon token rendered before the tab title. See [icons](./icon.md). |
| `href` | `String` | When set, the tab renders as an `<a href>` navigation link instead of a JS-driven `<button>`. Null = client-side panel switch. |
| `onClick` | `UiTrigger` | Trigger fired *in addition to* activating this panel. Independent of `href`. |
| `selectOnClick` | `boolean` | Default `true`. `false` fires `onClick` but leaves the panel where it is — the application decides. See [Guarding a tab](#guarding-a-tab). |
| `cssClass` | `String` | Extra CSS class on the entry wrapper. |

When the editor renders an entry on its own it emits a labelled box — the
`title` as an `<h3>` above the `content` — enough to read the structure without
the surrounding tab bar.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// usually built through the section:
UiSection.of("product-tabs", null)
    .section("overview", "Overview", overviewBody)
    .section("audit", "Audit", "/products/42/audit", auditList); // href variant

// directly, when you need icon or onClick:
UiSectionEntry.of("stock", "Stock", stockTable)
    .icon("grid");

UiSectionEntry.of("history", "History", placeholder)
    .icon("document")
    .onClick(UiTrigger.api("GET", "/products/42/history"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "section-entry", "id": "stock", "title": "Stock", "icon": "grid",
  "content": { "type": "table", "id": "stock-table", "columns": [], "rows": [] } }

{ "type": "section-entry", "id": "audit", "title": "Audit",
  "href": "/products/42/audit",
  "content": { "type": "list", "id": "audit-list", "items": [] } }

{ "type": "section-entry", "id": "history", "title": "History", "icon": "document",
  "onClick": { "behavior": "APPLY_RESPONSE", "method": "GET",
               "url": "/products/42/history" },
  "content": { "type": "spinner", "id": "history-loading" } }
```

</TabItem>
</Tabs>

## Notes

**`onClick` fires after the switch.** By default the panel changes first and the
trigger runs afterwards, so it can react but never prevent. To decide *whether*
the tab may change, set `selectOnClick: false` — see below.

**`onClick` is the lazy-load hook.** The panel still switches; the trigger fires
alongside it. Ship a [`spinner`](./spinner.md) or a placeholder as `content`,
fetch the real body on first open and patch it into the panel by the entry's
`id`.

**`href` is for SSR, `onClick` is for behaviour.** `href` says "this tab is a
different URL" — the tab becomes an anchor that works with JavaScript off, and
the SPA intercepts it and routes through `navigate()`. They are independent: an
entry can have both.

**Title-less entries turn off the tab bar.** If every entry in a section has no
`title` and no `href`, the section renders all panels visible instead of empty
tab buttons. Handy deliberately; a surprise if you forget a title on one entry
of a set — then you get one blank tab.

**The entry id owns the panel.** Patches that replace a tab's body target the
entry's `id`, not the section's. Keep them stable across renders.

## Guarding a tab

Sometimes a tab must not change on click: unsaved edits, a permission check, a
confirmation. `selectOnClick: false` keeps the panel where it is and hands the
decision to your handler, which selects a tab by patching the section's
`initialSection`.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=section-guarded"
  title="Live: a guarded tab"
  loading="lazy"
  style={{width: '100%', height: '240px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — "Billing" asks first. Decline and the panel never moves; allow and the
handler switches it by patch.*

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiSectionEntry.of("billing", "Billing", billingPanel())
              .selectOnClick(false)                       // don't switch on click
              .onClick(UiTrigger.api("GET", "/billing/may-open"));
```

The endpoint decides. To let the tab through, return a patch that re-renders
the section with the new `initialSection`:

```java
return UiPatch.of().patch(UiPatch.Operation.replace("sec",
        sectionWith("billing")));                          // same tree, different active tab
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "section-entry", "id": "billing", "title": "Billing",
  "selectOnClick": false,
  "onClick": { "behavior": "INVOKE", "handler": "mayOpenBilling" },
  "content": { "type": "text", "id": "p3", "text": "…" } }
```

```json
{ "patches": [
  { "op": "REPLACE", "targetId": "sec",
    "node": { "type": "section", "id": "sec", "initialSection": "billing",
              "sections": [ /* … */ ] } }
] }
```

</TabItem>
</Tabs>

:::note Why a patch rather than a "select" call
The active tab is not hidden state — it is `initialSection` on the section node.
Re-rendering the section with a different value *is* selecting a tab, and the
morpher keeps the surrounding DOM (and any focus or scroll) intact. One
mechanism, no separate imperative API to learn.
:::

## See also

- **[`section`](./section.md)** — the container these live in.
- **[`icon`](./icon.md)** — the valid `icon` tokens.
- **[Triggers & actions](../triggers.md)** — everything `onClick` can do.
- **[Rendering modes](../rendering-modes.md)** — why `href` matters in SSR.
