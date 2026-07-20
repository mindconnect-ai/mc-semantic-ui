// Specimen: UiDetail — a read-only record built from UiField rows, with an
// action row and a plain navigation link underneath.
export const node = {
  type: "detail", id: "product-detail", title: "Product",
  fields: [
    { type: "field", id: "d-sku",    label: "SKU",        fieldType: "TEXT",    value: "WID-1024" },
    { type: "field", id: "d-name",   label: "Name",       fieldType: "TEXT",    value: "Gadget" },
    { type: "field", id: "d-price",  label: "Price",      fieldType: "NUMBER",  value: 49.0 },
    { type: "field", id: "d-stock",  label: "In stock",   fieldType: "NUMBER",  value: 12 },
    { type: "field", id: "d-active", label: "Active",     fieldType: "BOOLEAN", value: true },
    { type: "field", id: "d-note",   label: "Note",       fieldType: "TEXT",    value: null }
  ],
  actions: [
    { type: "action", id: "d-edit", label: "Edit", style: "PRIMARY", icon: "edit",
      onClick: { behavior: "INVOKE", handler: "say" } },
    { type: "action", id: "d-del", label: "Delete", style: "DANGER", icon: "delete",
      confirm: "Delete this product?",
      onClick: { behavior: "INVOKE", handler: "say" } }
  ],
  links: [
    { type: "link", id: "d-history", rel: "ref", href: "#", label: "View history" }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("say", (ctx) => ({
    patches: [], toasts: [ { level: "INFO",
                message: `Clicked "${ctx.sourceElement?.textContent?.trim() || "action"}"` } ]
  }));
}
