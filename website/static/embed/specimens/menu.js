// Specimen: UiMenu — a collapsible sidebar next to a content panel.
// The hamburger cycles expanded → rail → hidden entirely in the event bus
// (no server round-trip); in PUSH mode the content beside it reflows wider.
export const node = {
  type: "stack", id: "sp-menu-shell", direction: "HORIZONTAL", gap: 16, children: [
    { type: "menu", id: "sp-menu", title: "Admin", state: "EXPANDED", mode: "PUSH", side: "LEFT",
      items: [
        { type: "menu-item", id: "sp-m-dash", label: "Dashboard", icon: "grid",
          selected: true, onClick: { behavior: "INVOKE", handler: "nav" } },
        { type: "menu-item", id: "sp-m-cat", label: "Catalog", icon: "folder", open: true, children: [
          { type: "menu-item", id: "sp-m-prod", label: "Products", icon: "document",
            onClick: { behavior: "INVOKE", handler: "nav" } },
          { type: "menu-item", id: "sp-m-cust", label: "Customers", icon: "show",
            onClick: { behavior: "INVOKE", handler: "nav" } }
        ] },
        { type: "menu-item", id: "sp-m-orders", label: "Orders", icon: "download", badge: "12",
          onClick: { behavior: "INVOKE", handler: "nav" } },
        { type: "menu-item", id: "sp-m-help", label: "Help", icon: "info", href: "#help" }
      ] },
    { type: "stack", id: "sp-menu-body", gap: 8, children: [
      { type: "text", id: "sp-menu-page", text: "Dashboard" },
      { type: "text", id: "sp-menu-hint",
        text: "Click the hamburger to cycle expanded, rail and hidden. In the rail, hover Catalog for a fly-out." }
    ] }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("nav", (ctx) => {
    const el = ctx.sourceElement;
    const label = el?.querySelector(".sui-menu-label")?.textContent?.trim()
        || el?.textContent?.trim() || "Page";
    return {
      patches: [ { op: "REPLACE", targetId: "sp-menu-page",
                   node: { type: "text", id: "sp-menu-page", text: label } } ],
      toasts: [ { level: "INFO", message: `Navigated to ${label}`, durationMs: 1800 } ]
    };
  });
}
