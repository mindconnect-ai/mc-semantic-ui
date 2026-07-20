// Specimen: UiColumn. A column cannot render on its own — it only exists
// inside a UiTable, so the specimen is a minimal table whose columns show
// the three things a column controls: label vs. dataKey, the `sortable`
// flag, and cellTemplate (a link cell and an action cell).
export const node = {
  type: "table", id: "sp-col-table", title: "Columns",
  columns: [
    // Plain column: label on the header, dataKey into the row data map.
    { type: "column", id: "col-sku", label: "SKU", dataKey: "sku", sortable: true },
    // cellTemplate: a UiLink whose href and label are {dataKey}-substituted
    // per row. Every string in the template is substituted, ids get a
    // per-row __<row.id> suffix.
    { type: "column", id: "col-name", label: "Name", dataKey: "name",
      cellTemplate: { type: "link", id: "c-name", rel: "ref", href: "#{sku}", label: "{name}" } },
    // cellTemplate can be any node — here a stack of one action, giving
    // inline per-cell controls that know the row's own id.
    { type: "column", id: "col-open", label: "", dataKey: "id",
      cellTemplate: { type: "stack", id: "c-open", direction: "HORIZONTAL", gap: 6, children: [
        { type: "action", id: "c-open-btn", label: "Open {sku}", style: "SECONDARY", icon: "show",
          onClick: { behavior: "INVOKE", handler: "open", url: "/products/{id}" } }
      ] } }
  ],
  rows: [
    { type: "row", id: "p1", data: { sku: "WID-1", name: "Widget" } },
    { type: "row", id: "p2", data: { sku: "GAD-2", name: "Gadget" } },
    { type: "row", id: "p3", data: { sku: "GIZ-3", name: "Gizmo"  } }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("open", (ctx) => ({
    patches: [], toasts: [{ level: "INFO", message: `Cell template resolved to ${ctx.url}`, durationMs: 2500 }]
  }));
}
