---
title: Chart extension
sidebar_position: 2
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# The chart extension

**`mc-semantic-ui-ext-chart` brings both halves of `chart`**: the `UiChart`
node type *and* the two painters that draw it — one for the browser, one for
server-side rendering.

That pairing is the rule the project follows: **a node type and the ability to
render it belong in the same place.** The core owns only what it can actually
draw, so it never promises a picture it cannot produce, and never has to depend
on a charting library. `chart` is therefore not part of the core vocabulary —
add this module and the node appears in the Java model, in Jackson, in the
browser renderer and in SSR at once.

<iframe
  src="/mc-semantic-ui/embed/chart-extension.html"
  title="Live: the chart extension"
  loading="lazy"
  style={{width: '100%', height: '520px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — four chart types from one data set. Hover anywhere over a column: the
tooltip shows every series at once, the column lifts and a crosshair marks it.
Rendered on the server without the script, the same chart falls back to native
SVG `<title>` tooltips.*

## What you get

| | |
|---|---|
| Node type | `chart` (`UiChart`), contributed by this module |
| Types | `BAR`, `LINE`, `AREA`, `PIE`, `DONUT` |
| Output | plain SVG — no canvas, no charting library, no runtime dependency |
| Works without JavaScript | **yes**, when rendered server-side — with native tooltips instead of the hover |

The [diagram extension](./diagram-extension.md) is built the same way, and is
the model to copy if you write your own.

## Install

:::tip With the asset registry
A Spring Boot host with the core's asset registry needs none of the browser
wiring below: the jar declares its files, and `installAll(renderer, bus)` from
`/sui/assets.js` installs it. See [the asset registry](./extension-assets.md).
:::

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java / server-side">

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-ext-chart</artifactId>
  <version>0.4.3</version>
</dependency>
```

Nothing else to configure. The module ships `templates/sui/chart.hbs` plus the
Handlebars helper it needs, contributed through `SuiHelperContributor` — found
either as a Spring bean or via `ServiceLoader`, so it works in a plain-Java app
with no Spring on the classpath.

```java
@GetMapping("/dashboard")
public UiPage dashboard() {
    var chart = new UiChart();
    chart.setId("revenue");
    chart.setTitle("Revenue");
    chart.setChartType(UiChart.ChartType.BAR);
    chart.setData(quarterlyRevenue());     // labels + one series
    return UiPage.of("/dashboard", chart);
}
```

</TabItem>
<TabItem value="browser" label="Browser">

```html
<link rel="stylesheet" href="/sui/sui.css">
<link rel="stylesheet" href="/sui-ext/chart/chart.css">

<script type="module">
  import { createDefaultRenderer } from "/sui/renderer.js";
  import { install as installChart } from "/sui-ext/chart/extension.js";

  const renderer = createDefaultRenderer().attach(document.getElementById("app"));
  installChart(renderer);        // registers the "chart" handler

  renderer.mount({
    type: "chart", id: "revenue", title: "Revenue", chartType: "BAR",
    data: {
      labels: ["Q1", "Q2", "Q3", "Q4"],
      series: [{ name: "Revenue", values: [12, 30, 18, 26] }]
    }
  });
</script>
```

`installChart(renderer)` is scoped to that renderer — a page can run two
renderers where only one draws charts.

</TabItem>
</Tabs>

:::tip A chart that survives JavaScript being off
The server-rendered output is static SVG with `<title>` tooltips — no script tag
anywhere. That makes it the rare chart that still works in an email client, a
PDF export, or a hardened browser.
:::

## The node

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `title` | `String` | Optional heading rendered above the chart (`<h2>`). |
| `chartType` | `LINE` · `BAR` · `PIE` · `DONUT` · `AREA` | Which chart to draw. |
| `data` | `UiChart.ChartData` | The payload. |
| `stacked` | `Boolean` | A bar chart with several series stacks them, one bar per label, the total in the tooltip. Otherwise they stand side by side. |
| `crosshair` | `Boolean` | The dashed line down the hovered column of a bar, line or area chart. On unless `false`. |
| `valueFormat` | `UiChart.ValueFormat` | `prefix`, `suffix`, `decimals` — how values read in tooltips, legends and on the axis. |
| `cssClass` | `String` | Extra CSS class on the wrapper `<div>`. |

`UiChart.ChartData` and its nested `Series`:

| Field | Type | Meaning |
|---|---|---|
| `data.labels` | `List<String>` | Category labels — the x axis, or the pie slice names. |
| `data.series` | `List<ChartData.Series>` | One or more series. |
| `series[].name` | `String` | Series name, for legends and tooltips. |
| `series[].values` | `List<Number>` | The values, positionally aligned with `labels`. |

There are no colour, axis or scale fields: the painter picks round ticks for
the value axis itself, and the palette comes from CSS. What a value *means* —
money, a unit — is the model's, through `valueFormat`:

```java
UiChart.of("cost", "Cost per day (CHF)", UiChart.ChartType.BAR, data)
        .valueFormat(null, " CHF", 2);        // tooltip "1.61 CHF", axis "1.5"
UiChart.of("tokens", "Tokens per day", UiChart.ChartType.BAR, data)
        .stacked(true);                       // input and output, one bar a day
```

Values are written with thousands separators (`143,911`). Without `decimals`
a whole number gets none, a number of at least 1 two, and a smaller one up to
four. The axis keeps the prefix and leaves the suffix to the title, and writes
large ticks short (`250k`, `1.5M`).

### Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
var data = new UiChart.ChartData();
data.setLabels(List.of("Q1", "Q2", "Q3", "Q4"));

var revenue = new UiChart.ChartData.Series();
revenue.setName("Revenue");
revenue.setValues(List.of(24, 38, 30, 45));
data.setSeries(List.of(revenue));

UiChart.of("chart-revenue", "Revenue by quarter", UiChart.ChartType.BAR, data);
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "chart", "id": "chart-revenue", "title": "Revenue by quarter",
  "chartType": "BAR",
  "data": {
    "labels": ["Q1", "Q2", "Q3", "Q4"],
    "series": [ { "name": "Revenue", "values": [24, 38, 30, 45] } ]
  } }
```

</TabItem>
</Tabs>

**`data` is intentionally minimal.** Labels plus named numeric series is the
common denominator every charting library accepts. If your chart needs more
(dual axes, time scales), carry it in your own extension node type rather than
stretching `UiChart`.

## The hover

With the extension's script on the page, every bar, line and area chart has
one transparent band per column over the whole plot height. The pointer
anywhere over a column:

- shows a tooltip at once — the label, each series with its colour and value,
  and for stacked bars the total — beside the column, flipping sides at the
  chart's right edge;
- marks the column: its bars grow slightly and brighten, its dots grow, every
  other column dims (CSS transitions, off under `prefers-reduced-motion`);
- draws the dashed crosshair down its centre, unless `crosshair` is `false`.

A donut or pie segment under the pointer, or its legend row, marks the
segment and the row together. The listeners sit on the document, added once by
`install()` (or `enableHover()`), so server-rendered charts come alive too; the
script removes the native `<title>`s the first time it draws its own.

**Charts nest.** A `chart` is a plain node, so it fits anywhere a node fits —
including a [`tree-node`](./elements/tree-node.md)'s `content` slot or a
[`detail`](./elements/detail.md) panel.

## Both renderers draw the same picture

The painter exists twice — `ChartPainter.java` and `chart/extension.ts` —
because the project renders from both sides. They are held to **byte-identical
output**: `ChartPainterGoldenTest` draws every fixture in
`src/test/resources/chart-golden/` in Java and compares it with what the
TypeScript painter drew for it (`npm run build && node scripts/chart-golden.mjs`
writes those).

Three formatting rules exist only to keep that true, and each is pinned by a
test because each one actually broke once:

| Rule | Why |
|---|---|
| Whole numbers print without `.0` | Java would write `30.0` where JS writes `30` — visible in tooltips |
| Negative zero is collapsed | The first pie segment starts at `-0.0`: Java prints `-0.00`, JS prints `0.00` |
| Decimals always use a point | A German default locale would produce `12,5` and break the SVG |
| Rounding starts from the exact binary value | `String.format` rounds `1.005` to `1.01`, JavaScript's `toFixed` to `1.00` |
| Tick steps are found by multiplying by ten | `Math.pow` may differ in the last bit between the two |

## Swapping the painter

`install()` is one `renderer.register("chart", …)` call. Registering your own
handler afterwards replaces it, which is how you swap in a charting library or
a different look:

```js
installChart(renderer);
renderer.register("chart", (node) => `<div class="sui-chart" id="${node.id}">…</div>`);
```

The handler receives the node and returns an HTML string, exactly like the
built-in renderers — see [adding a node type of your
own](./how-it-works.md#step-5--add-a-node-type-of-your-own); the mechanism is
identical, except that `chart` already has a Java model, so you only supply the
drawing half.

## Styling

Colours come from CSS variables with literal fallbacks, so a chart restyles from
your stylesheet and still renders standalone:

```css
:root {
  --sui-chart-1: #0f766e;
  --sui-chart-2: #b91c1c;
  /* … up to --sui-chart-6 */
}
```

Layout (size, legend placement, the empty state, the hover's animation and
the tooltip) lives in `/sui-ext/chart/chart.css` — override the `.sui-chart*`
classes to change it: `.sui-chart-tip` is the tooltip, `.is-active` marks the
hovered column or segment, `.sui-chart--hover` the chart while one is.

## See also

- **[Diagram extension](./diagram-extension.md)** — the other extension, built
  the same way.
- **[Node vocabulary → Extending](./node-vocabulary.md#extending-the-vocabulary)**
  — writing your own.
- **[Triggers & actions](./triggers.md)** — refreshing a chart via a patch.
