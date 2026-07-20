// Specimen: sortable columns without a sortTrigger — the browser reorders the
// rows it already has. Deliberately unsorted data so the first click is
// visible, and a capped maxHeight so the sticky header can be seen too.
export const node = {
  type: "table", id: "sortable", title: "Products", maxHeight: "220px",
  columns: [
    { type: "column", id: "c-sku",   label: "SKU",   dataKey: "sku",   sortable: true },
    { type: "column", id: "c-name",  label: "Name",  dataKey: "name",  sortable: true },
    { type: "column", id: "c-price", label: "Price", dataKey: "price", sortable: true },
    { type: "column", id: "c-note",  label: "Note",  dataKey: "note",  sortable: true }
  ],
  rows: [
    { type: "row", id: "p3", data: { sku: "C-3", name: "Doohickey", price: "7.50",  note: "clearance" } },
    { type: "row", id: "p1", data: { sku: "A-1", name: "Widget",    price: "19.00", note: "" } },
    { type: "row", id: "p5", data: { sku: "E-5", name: "Apparatus", price: "120.00", note: "made to order" } },
    { type: "row", id: "p2", data: { sku: "B-2", name: "Gadget",    price: "49.00", note: "" } },
    { type: "row", id: "p4", data: { sku: "D-4", name: "Thing",     price: "3.00",  note: "bulk only" } }
  ]
};
