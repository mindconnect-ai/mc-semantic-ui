// Specimen: UiTable with stackOnMobile — the same table the docs page builds
// in Java. Keep it embedded in a *narrow* frame (< 640px): that is what puts
// the CSS media query into effect and turns every row into a card.
export const node = {
  type: "table", id: "sp-table-stacked", title: "Products",
  stackOnMobile: true,
  rowActions: [
    { type: "action", id: "s-edit", label: "Edit", icon: "edit", appearance: "ICON", style: "SECONDARY",
      onClick: { behavior: "INVOKE", handler: "row", url: "/products/{id}/edit" } }
  ],
  columns: [
    { type: "column", id: "sc-name",  label: "Name",  dataKey: "name" },
    { type: "column", id: "sc-price", label: "Price", dataKey: "price" },
    { type: "column", id: "sc-stock", label: "Stock", dataKey: "stock" }
  ],
  rows: [
    { type: "row", id: "p1", data: { name: "Widget", price: "€ 19.00", stock: "128" } },
    { type: "row", id: "p2", data: { name: "Gadget", price: "€ 49.00", stock: "12" } }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("row", (ctx) => ({
    patches: [], toasts: [{ level: "INFO", message: `Row trigger url: ${ctx.url}`, durationMs: 2500 }]
  }));
}
