// Specimen: UiRow. A row cannot render on its own — it only exists inside a
// UiTable, so the specimen is a minimal table showing what a row carries:
// a stable `id`, a `data` map keyed by the columns' dataKeys, the highlight
// driven by the table's selectedRowId, checkbox selection via selectedRowIds,
// and a row action whose {id} placeholder resolves to the row's own id.
export const node = {
  type: "table", id: "sp-row-table", title: "Rows",
  selectMode: "MULTI", selectedRowIds: ["r2", "r3"], selectedRowId: "r2",
  rowActions: [
    { type: "action", id: "r-who", label: "Who am I?", style: "SECONDARY",
      onClick: { behavior: "INVOKE", handler: "who", url: "/rows/{id}" } }
  ],
  columns: [
    { type: "column", id: "col-id",   label: "Row id", dataKey: "id" },
    { type: "column", id: "col-user", label: "User",   dataKey: "user" },
    { type: "column", id: "col-role", label: "Role",   dataKey: "role" }
  ],
  rows: [
    // "id" inside data is promoted to the row's UiNode id by UiRow.of(...).
    { type: "row", id: "r1", data: { id: "r1", user: "ada",   role: "admin"  } },
    // r2 is both checked (selectedRowIds) and highlighted (selectedRowId).
    { type: "row", id: "r2", data: { id: "r2", user: "linus", role: "editor" } },
    // r3 is only checked — "missing" is not a column dataKey, so it is
    // simply never rendered.
    { type: "row", id: "r3", data: { id: "r3", user: "grace", role: "viewer", missing: "unused" } }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("who", (ctx) => ({
    patches: [], toasts: [{ level: "INFO", message: `Row action fired for ${ctx.url}`, durationMs: 2500 }]
  }));
}
