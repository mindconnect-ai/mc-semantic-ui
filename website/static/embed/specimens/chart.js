// Specimen: UiChart, drawn by the official chart extension.
//
// The CORE renders an empty placeholder for this node — deliberately, so it
// never depends on a charting library. The picture below comes from
// mc-semantic-ui-ext-chart, installed with a single call.
import { install as installChart } from "../../sui-ext/chart/extension.js";

const DATA = { labels: ["Q1", "Q2", "Q3", "Q4"], series: [{ name: "Revenue", values: [24, 38, 30, 45] }] };

export const node = {
  type: "stack", id: "sp", direction: "HORIZONTAL", gap: 24, children: [
    { type: "chart", id: "chart-bar",   title: "BAR",   chartType: "BAR",   data: DATA },
    { type: "chart", id: "chart-line",  title: "LINE",  chartType: "LINE",  data: DATA },
    { type: "chart", id: "chart-donut", title: "DONUT", chartType: "DONUT", data: DATA }
  ]
};

export function install(renderer) {
  installChart(renderer);

  // The extension ships its own stylesheet; without it the SVG is unstyled.
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "../sui-ext/chart/chart.css";
  document.head.appendChild(link);
}
