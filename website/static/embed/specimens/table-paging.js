// Specimen: paging with no backend. The Prev/Next buttons fire an INVOKE
// trigger; the handler slices the data it already has and REPLACEs the table.
//
// The same shape works against a server — swap the INVOKE trigger for
// UiTrigger.go("/products?page={page}") and return the page from a controller.
const ALL = [
  { sku: "A-1", name: "Widget",     price: "€ 19.00" },
  { sku: "B-2", name: "Gadget",     price: "€ 49.00" },
  { sku: "C-3", name: "Doohickey",  price: "€ 7.50"  },
  { sku: "D-4", name: "Thing",      price: "€ 3.00"  },
  { sku: "E-5", name: "Apparatus",  price: "€ 120.00"},
  { sku: "F-6", name: "Contraption",price: "€ 64.00" },
  { sku: "G-7", name: "Gizmo",      price: "€ 12.00" },
];
const SIZE = 3;

// {page} is substituted into the trigger's url before dispatch — for an INVOKE
// trigger that url is just where the page number rides along.
const pageTrigger = { behavior: "INVOKE", handler: "goPage", url: "{page}" };

function tableFor(page) {
  const start = (page - 1) * SIZE;
  return {
    type: "table", id: "paged", title: "Products",
    columns: [
      { type: "column", id: "c-sku",   label: "SKU",   dataKey: "sku"   },
      { type: "column", id: "c-name",  label: "Name",  dataKey: "name"  },
      { type: "column", id: "c-price", label: "Price", dataKey: "price" }
    ],
    rows: ALL.slice(start, start + SIZE).map((d, i) => ({
      type: "row", id: `r${start + i}`, data: d
    })),
    pagination: { page, size: SIZE, total: ALL.length, pageTrigger }
  };
}

export const node = tableFor(1);

export function install(renderer, bus) {
  bus.registerClientHandler("goPage", (ctx) => {
    const page = Number(ctx.trigger?.url) || 1;
    return { patches: [ { op: "REPLACE", targetId: "paged", node: tableFor(page) } ] };
  });
}
