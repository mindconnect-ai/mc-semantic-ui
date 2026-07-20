---
title: Chart extension
sidebar_position: 2
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# The chart extension

`chart` is a core node, but the core deliberately draws nothing — it emits a
placeholder so it never has to depend on a charting library. **This module is
the drawing.** Add it and the same `chart` JSON you already have starts
rendering, in the browser *and* on the server.

<iframe
  src="/mc-semantic-ui/embed/chart-extension.html"
  title="Live: the chart extension"
  loading="lazy"
  style={{width: '100%', height: '520px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — four chart types from one data set. Hover a bar or a segment for its
value; those tooltips are native SVG `<title>`s, not JavaScript.*

## What you get

| | |
|---|---|
| Types | `BAR`, `LINE`, `AREA`, `PIE`, `DONUT` |
| Output | plain SVG — no canvas, no charting library, no runtime dependency |
| Works without JavaScript | **yes**, when rendered server-side |
| Adds a node type | **no** — it paints the core's existing `chart` |

That last row is the difference from the [diagram
extension](./diagram-extension.md), which brings its own node type. Here the
model is already in the core; the module supplies only the two painters and a
stylesheet.

## Client-side

```html
<link rel="stylesheet" href="/sui/sui.css">
<link rel="stylesheet" href="/sui-ext/chart/chart.css">

<script type="module">
  import { createDefaultRenderer } from "/sui/renderer.js";
  import { install as installChart } from "/sui-ext/chart/extension.js";

  const renderer = createDefaultRenderer().attach(document.getElementById("app"));
  installChart(renderer);        // replaces the core's placeholder handler

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

## Server-side

Add the dependency; nothing else to configure:

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-ext-chart</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

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

The module ships a `templates/sui/chart.hbs` that shadows the core's placeholder
template, plus the Handlebars helper it needs — contributed through
`SuiHelperContributor`, found either as a Spring bean or via `ServiceLoader`. So
it works in a plain-Java app too, with no Spring on the classpath.

:::tip A chart that survives JavaScript being off
The server-rendered output is static SVG with `<title>` tooltips — no script tag
anywhere. That makes it the rare chart that still works in an email client, a
PDF export, or a hardened browser.
:::

## Both renderers draw the same picture

The painter exists twice — `ChartPainter.java` and `chart/extension.ts` — because
the project renders from both sides. They are held to **byte-identical output**:
every chart type is rendered through both and diffed.

Three formatting rules exist only to keep that true, and each is pinned by a
test because each one actually broke once:

| Rule | Why |
|---|---|
| Whole numbers print without `.0` | Java would write `30.0` where JS writes `30` — visible in tooltips |
| Negative zero is collapsed | The first pie segment starts at `-0.0`: Java prints `-0.00`, JS prints `0.00` |
| Decimals always use a point | A German default locale would produce `12,5` and break the SVG |

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

Layout (size, legend placement, the empty state) lives in
`/sui-ext/chart/chart.css` — override the `.sui-chart*` classes to change it.

## See also

- **[`chart`](./elements/chart.md)** — the node itself: fields, data shape.
- **[Diagram extension](./diagram-extension.md)** — the other extension, and how
  it differs.
- **[Node vocabulary → Extending](./node-vocabulary.md#extending-the-vocabulary)**
  — writing your own.
