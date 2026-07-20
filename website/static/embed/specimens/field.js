// Specimen: UiField — one node per input. Six field types side by side, a
// read-only field (editable omitted), a field carrying a validationError, and
// a BOOLEAN with an onChange trigger that reports the new value.
export const node = {
  type: "stack", id: "sp", gap: 14, children: [
    { type: "stack", id: "sp-cols", direction: "HORIZONTAL", gap: 20, children: [

      { type: "stack", id: "sp-col-a", gap: 10, children: [
        { type: "field", id: "fx-name", label: "Name", fieldType: "TEXT",
          editable: true, required: true, placeholder: "e.g. Widget",
          hint: "Shown in the product list." },
        { type: "field", id: "fx-price", label: "Price", fieldType: "NUMBER",
          editable: true, value: 19.0, min: "0", step: "0.01" },
        { type: "field", id: "fx-launch", label: "Launch date", fieldType: "DATE",
          editable: true, value: "2026-07-06", min: "2026-01-01" },
        { type: "field", id: "fx-sku", label: "SKU", fieldType: "TEXT",
          value: "WGT-0042" }
      ]},

      { type: "stack", id: "sp-col-b", gap: 10, children: [
        { type: "field", id: "fx-desc", label: "Description", fieldType: "TEXTAREA",
          editable: true, placeholder: "Describe the product…",
          validationError: "Add at least a short description." },
        { type: "field", id: "fx-cat", label: "Category", fieldType: "SELECT",
          editable: true, value: "tools", options: [
            { value: "tools", label: "Tools" },
            { value: "toys",  label: "Toys" },
            { value: "home",  label: "Home" }
          ]},
        { type: "field", id: "fx-active", label: "Active", fieldType: "BOOLEAN",
          editable: true, value: true,
          onChange: { behavior: "INVOKE", handler: "fx-toggled" } }
      ]}
    ]}
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("fx-toggled", (ctx) => {
    const on = ctx.sourceElement?.checked;
    // A UiPatch is recognised by its `patches` array — keep it, even when empty.
    return { patches: [], toasts: [ { level: "INFO", message: `Active is now ${on ? "on" : "off"}`, durationMs: 2000 } ] };
  });
}
