// Specimen: UiTable — columns, rows, header actions, MULTI selection with a
// pre-checked row, per-row actions with {id} substitution, and pagination.
export const node = {
  type: "table", id: "sp-table", title: "Products",
  selectMode: "MULTI", selectedRowIds: ["p2"], selectedRowId: "p2",
  stackOnMobile: true,
  actions: [
    { type: "action", id: "t-add", label: "New", style: "PRIMARY", icon: "add",
      onClick: { behavior: "INVOKE", handler: "tbl" } },
    { type: "action", id: "t-export", label: "Export", style: "SECONDARY", icon: "download",
      onClick: { behavior: "INVOKE", handler: "tbl" } }
  ],
  rowActions: [
    { type: "action", id: "r-edit", label: "Edit", icon: "edit", appearance: "ICON", style: "SECONDARY",
      onClick: { behavior: "INVOKE", handler: "row", url: "/products/{id}/edit" } },
    { type: "action", id: "r-del", label: "Delete", icon: "delete", appearance: "ICON", style: "DANGER",
      confirm: "Delete this product?",
      onClick: { behavior: "INVOKE", handler: "row", url: "/products/{id}" } }
  ],
  columns: [
    { type: "column", id: "col-name",  label: "Name",  dataKey: "name" },
    { type: "column", id: "col-price", label: "Price", dataKey: "price" },
    { type: "column", id: "col-stock", label: "Stock", dataKey: "stock" }
  ],
  rows: [
    { type: "row", id: "p1", data: { name: "Widget", price: "€ 19.00", stock: "128" } },
    { type: "row", id: "p2", data: { name: "Gadget", price: "€ 49.00", stock: "12" } },
    { type: "row", id: "p3", data: { name: "Gizmo",  price: "€ 99.00", stock: "0" } }
  ],
  pagination: {
    page: 1, size: 3, total: 57,
    pageTrigger: { behavior: "INVOKE", handler: "page", url: "/products?page={page}" }
  }
};

export function install(renderer, bus) {
  const toast = (message) => ({ patches: [], toasts: [{ level: "INFO", message, durationMs: 2500 }] });
  bus.registerClientHandler("tbl", (ctx) =>
    toast(`Header action: ${ctx.sourceElement?.textContent?.trim() || "action"}`));
  // Row actions share one trigger template; the renderer substituted {id}
  // with the row's own id before the click ever reached the bus.
  bus.registerClientHandler("row", (ctx) => toast(`Row trigger url: ${ctx.url}`));
  bus.registerClientHandler("page", (ctx) => toast(`Page trigger url: ${ctx.url}`));
}
