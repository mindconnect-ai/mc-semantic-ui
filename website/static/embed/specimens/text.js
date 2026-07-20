// Specimen: UiText — the simplest leaf node, and the classic patch target.
// The two buttons REPLACE the "cart-total" text node in place; nothing else
// on the page is re-rendered.
export const node = {
  type: "stack", id: "sp", gap: 14, children: [
    { type: "text", id: "sp-intro",
      text: "A plain text node — a string that participates in the node tree." },
    { type: "stack", id: "sp-total-row", direction: "HORIZONTAL", gap: 8, children: [
      { type: "text", id: "sp-total-label", text: "Cart total:" },
      { type: "text", id: "cart-total", text: "€ 49.00" }
    ]},
    { type: "stack", id: "sp-buttons", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "sp-add", label: "Add item", style: "PRIMARY", icon: "add",
        onClick: { behavior: "INVOKE", handler: "addItem" } },
      { type: "action", id: "sp-reset", label: "Reset", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "resetTotal" } }
    ]}
  ]
};

export function install(renderer, bus) {
  let cents = 4900;

  const replaceTotal = () => ({
    patches: [
      { op: "REPLACE", targetId: "cart-total",
        node: { type: "text", id: "cart-total",
                text: "€ " + (cents / 100).toFixed(2) } }
    ]
  });

  bus.registerClientHandler("addItem", () => {
    cents += 1250;
    return replaceTotal();
  });

  bus.registerClientHandler("resetTotal", () => {
    cents = 4900;
    return { ...replaceTotal(), toasts: [ { level: "INFO", message: "Cart reset" } ] };
  });
}
