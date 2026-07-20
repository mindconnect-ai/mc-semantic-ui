---
title: Section
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `section` — tabs and collapsible panels

**`UiSection`** is one node with two jobs. Given a list of
[`section-entry`](./section-entry.md) children it renders a **tab bar** with one
panel visible at a time. Given a `collapseSummary` it renders the same content
inside a native `<details>` **disclosure** instead.

Both modes are content-complete without JavaScript: every panel is in the DOM,
and the disclosure is pure HTML. The event bus only adds the client-side panel
switch on top.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=section"
  title="Live: UiSection"
  loading="lazy"
  style={{width: '100%', height: '360px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — click the tabs; switching happens in the browser, no request. Below them
the same node in collapsible mode.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `title` | `String` | Rendered as an `<h2>` above the tab bar. Omit for a bare tab strip. |
| `sections` | `List<UiSectionEntry>` | The entries — one per tab. Defaults to an empty list. |
| `initialSection` | `String` | `id` of the entry shown first. Falls back to the first entry. |
| `tabOverflow` | `WRAP` · `MENU` | How the bar copes when the tabs do not fit. `WRAP` when unset. |
| `collapseSummary` | `String` | Set it and the section renders as a `<details>` disclosure with this text as the summary. Null/blank = tabbed mode. |
| `collapseOpen` | `boolean` | Initial open state of that disclosure. Defaults to `false`. |
| `cssClass` | `String` | Extra CSS class on the section wrapper. |

### Tabbed mode

The default. Each entry becomes a `<button class="sui-tab">` (or an `<a>` when
the entry has an `href`) plus a `<div class="sui-panel">`; every panel but the
active one carries `hidden`.

`tabOverflow` decides what happens at narrow widths:

- **`WRAP`** (default) — the tabs flow onto more rows.
- **`MENU`** — the bar stays a single row and the tabs that do not fit collapse
  into a trailing "⋯" dropdown. This needs the SPA; with no JavaScript the CSS
  still wraps, so the fallback stays usable.

### Collapsible mode

Set `collapseSummary` (via `collapsible(summary, open)`) and the renderer wraps
the whole section body in `<details class="sui-section--collapsible">` with the
summary as its `<summary>`. The body inside is still a normal section body — so
a collapsible section can itself contain tabs.

### Stack mode

There is a third, implicit shape: if **every** entry has no `title` and no
`href`, the renderer emits all panels visible with no tab bar at all. Empty tab
buttons would otherwise hide everything but the first panel. This is what makes
the single-entry collapsible pattern above work.

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// tabs
UiSection.of("product-tabs", "Product")
    .section("overview", "Overview", overviewBody)
    .section("stock",    "Stock",    stockTable)
    .section("history",  "History",  historyList)
    .initialSection("stock")
    .tabOverflow(UiSection.TabOverflow.MENU);

// tabs that are real URLs (each panel lives on its own page)
UiSection.of("admin-tabs", null)
    .section("users",  "Users",  "/admin/users",  usersTable)
    .section("audit",  "Audit",  "/admin/audit",  auditList)
    .initialSection("users");

// the same node as a collapsible panel
UiSection.of("advanced", null)
    .section("adv-body", null, advancedForm)
    .collapsible("Advanced settings", false);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "section", "id": "product-tabs", "title": "Product",
  "initialSection": "stock", "tabOverflow": "MENU",
  "sections": [
    { "type": "section-entry", "id": "overview", "title": "Overview", "icon": "info",
      "content": { "type": "text", "id": "o", "text": "…" } },
    { "type": "section-entry", "id": "stock", "title": "Stock",
      "content": { "type": "table", "id": "stock-table", "columns": [], "rows": [] } }
  ] }

{ "type": "section", "id": "advanced",
  "collapseSummary": "Advanced settings", "collapseOpen": false,
  "sections": [
    { "type": "section-entry", "id": "adv-body",
      "content": { "type": "form", "id": "advanced-form", "fields": [] } }
  ] }
```

</TabItem>
</Tabs>

## Notes

**Tabs switch client-side, for free.** All panels ship in the DOM and the bus
shows the matching one on click — no handler, no request, no state on your side.
Give an entry an `href` only when each tab should be a real URL that survives
JavaScript being off.

**`initialSection` is how you restore state.** After a server round-trip, set it
to the tab the user was on; otherwise a re-render silently sends them back to
the first tab.

**Reach for `MENU` on tab bars that can grow.** A bar of four fixed tabs is fine
wrapping. A bar whose length depends on data — one tab per workspace, per
document — should be `MENU`, or a narrow viewport turns it into three rows of
chrome.

**Collapsible sections cost no JavaScript.** `<details>` is native HTML, so a
collapsible section works identically in SSR and SPA mode and needs nothing
wired. Use it for advanced/optional blocks rather than building a show/hide
trigger.

**Section or stack?** Use a section when the children compete for one viewport
and the user picks; use a [`stack`](./stack.md) when everything should just be
visible in order.

## See also

- **[`section-entry`](./section-entry.md)** — the individual tab and its panel.
- **[`stack`](./stack.md)** — plain composition, no tabs.
- **[`page`](./page.md)** — the envelope around a section tree.
- **[Responsive](../responsive.md)** — `tabOverflow` and narrow viewports.
- **[Triggers & actions](../triggers.md)** — for an entry's `onClick`.
