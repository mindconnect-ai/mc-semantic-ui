---
title: Chart
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `chart` — chart data as a node

**`UiChart`** carries a chart *payload* — a chart type, a label axis and one or
more named series — through the same typed, serializable model as every other
node.

:::info An extension node
`chart` is **not** part of the core vocabulary. It ships in
**[mc-semantic-ui-ext-chart](../chart-extension.md)** together with the painters
that draw it, exactly like [`diagram`](../diagram-extension.md).

That pairing is the rule the project follows: **a node type and the ability to
render it belong in the same place.** The core owns only what it can actually
draw, so it never promises a picture it can't produce — and never has to depend
on a charting library.

Add the module and this node is available in the Java model, in Jackson, in the
browser renderer and in server-side rendering.
:::

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=chart"
  title="Live: UiChart"
  loading="lazy"
  style={{width: '100%', height: '270px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — three `chart` nodes with identical data, drawn by the chart extension
(`install(renderer)`, nothing else). Without it these would be empty boxes.
Hover a bar or a segment for its value.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id — also the DOM `id` and the patch target. |
| `title` | `String` | Optional heading rendered above the chart (`<h2>`). |
| `chartType` | `LINE` · `BAR` · `PIE` · `DONUT` · `AREA` | Which chart the payload is meant to be. A hint for the handler; the core does not interpret it. |
| `data` | `UiChart.ChartData` | The payload. |
| `cssClass` | `String` | Extra CSS class on the wrapper `<div>`. |

`UiChart.ChartData` and its nested `Series`:

| Field | Type | Meaning |
|---|---|---|
| `data.labels` | `List<String>` | Category labels — the x axis, or the pie slice names. |
| `data.series` | `List<ChartData.Series>` | One or more series. |
| `series[].name` | `String` | Series name, for legends and tooltips. |
| `series[].values` | `List<Number>` | The values, positionally aligned with `labels`. |

There are no colour, axis, legend or scale fields. Anything of that kind is the
handler's business, not the model's.

## Building one

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

## Notes

**Out of the box you get a box.** The core TypeScript renderer produces
`<div class="sui-chart" id="…" data-chart='{"chartType":…,"data":…}'>` plus the title
— no SVG, no canvas, no library. Rendering it as-is and expecting a chart is the one
mistake to avoid.

**Replacing the painter.** The extension registers a handler for the `chart`
type; registering your own afterwards replaces it, which is how you swap in a
charting library or a different look:

```js
renderer.register("chart", (node) => `<div class="sui-chart" id="${node.id}">…</div>`);
```

The handler receives the node and returns an HTML string, exactly like the built-in
renderers. See [adding a node type of your own](../how-it-works.md#step-5--add-a-node-type-of-your-own)
— the mechanism is identical, except that `chart` already has a Java model, so you
only supply the drawing half. The widget demo does precisely this to produce real
charts from inline SVG.

**Two ways to hydrate.** Either register a renderer (as above), or leave the
placeholder alone and let an addon read `data-chart` off the mounted element after
render — the attribute exists for that second style, which is what a library that
wants to own a real DOM node (Chart.js, ECharts) usually needs.

**`data` is intentionally minimal.** Labels plus named numeric series is the common
denominator every charting library accepts. If your chart needs more (stacking,
dual axes, time scales), carry it in your own extension node type rather than
stretching `UiChart`.

**Charts nest.** A `chart` is a plain node, so it fits anywhere a node fits —
including a [`tree-node`](./tree-node.md)'s `content` slot or a
[`detail`](./detail.md) panel.

## See also

- **[Chart extension](../chart-extension.md)** — the official painter for this node.
- **[Diagram extension](../diagram-extension.md)** — a graph node that brings its own type.
- **[How it works — step 5](../how-it-works.md#step-5--add-a-node-type-of-your-own)** — registering renderers.
- **[`tree-node`](./tree-node.md)** — a common host for an embedded chart.
- **[Triggers & actions](../triggers.md)** — refreshing a chart via a patch.
