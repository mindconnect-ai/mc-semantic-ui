---
title: Mobile & responsive
sidebar_position: 8
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Mobile &amp; responsive

Several nodes adapt to narrow screens. All of it is opt-in on the model and
degrades gracefully without JavaScript.

## Tables → stacked cards

Set `stackOnMobile` and a wide table becomes, on a narrow screen, one card per
row with `Column: value` lines (the header row is hidden; each cell prints its
column name):

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
UiTable.of("products", "Products")
    .column(UiColumn.of("name", "Name"))
    .column(UiColumn.of("price", "Price"))
    .row(Map.of("id", "p1", "name", "Widget", "price", "€ 19.00"))
    .stackOnMobile(true);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "table", "id": "products", "stackOnMobile": true, "columns": [...], "rows": [...] }
```

</TabItem>
</Tabs>

Renders below `640px` as:

```
Name:  Widget
Price: € 19.00
──────────────
Name:  Gadget
Price: € 49.00
```

Each `<td>` carries a `data-label` (the column label) that the CSS shows via a
`::before`, so it works in both the SSR and SPA output and needs no script.

## Tabs → wrap or a "⋯ More" menu

Tab bars **wrap** onto more rows by default when they don't fit. For a
single-row bar that collapses the overflow into a dropdown instead, set
`tabOverflow: MENU`:

```java
UiSection.of("main", null)
    .section("overview", "Overview", overviewBody)
    .section("orders",   "Orders",   ordersBody)
    // …many tabs…
    .tabOverflow(UiSection.TabOverflow.MENU);
```

It works out of the box: mount your page through a `SuiEventBus` and the tabs that
don't fit collapse into a trailing **⋯** dropdown on their own — it re-computes on
resize. Without JS the bar simply wraps, so it stays usable either way.

```ts
const bus = new SuiEventBus(renderer, root);
renderer.mount(page);   // tab overflow is handled automatically
```

## Navigation menu

The sidebar `UiMenu` has its own responsive story — a `RESPONSIVE` mode that is
a push rail on desktop and an overlay drawer on mobile. See
[`menu`](./elements/menu.md).

---

:::note Do I still need the wire… helpers?
As a normal consumer — no. The `SuiEventBus` watches its root and runs these
enhancers itself after every render, so tab overflow, menu-button popovers and the
sidebar's persisted collapse state all just work once you've mounted through a bus.

The functions stay **exported** only as an escape hatch for the rare case of
rendering **without** a bus — e.g. progressively enhancing a static, server-rendered
page with a sprinkle of JS but no full SPA runtime. Then you'd call the one you need
once after the markup is in the DOM. If you use the bus (the usual case), you can
forget they exist.
:::
