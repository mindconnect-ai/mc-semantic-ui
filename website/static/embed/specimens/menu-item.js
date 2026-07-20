// Specimen: UiMenuItem — the entries themselves. An item never renders alone,
// so this is one small UiMenu plus one UiMenuButton whose items show every
// menu-item feature: nesting, selected, badge, divider, danger and an
// inherited `confirm` (a UiAction field that works unchanged here).
export const node = {
  type: "stack", id: "sp-mi", direction: "HORIZONTAL", gap: 16, children: [
    { type: "menu", id: "sp-mi-menu", title: "Items", state: "EXPANDED", toggle: false,
      items: [
        { type: "menu-item", id: "sp-mi-sel", label: "Selected leaf", icon: "grid",
          selected: true, onClick: { behavior: "INVOKE", handler: "pick" } },
        { type: "menu-item", id: "sp-mi-badge", label: "With badge", icon: "download", badge: "7",
          onClick: { behavior: "INVOKE", handler: "pick" } },
        { type: "menu-item", id: "sp-mi-group", label: "Group", icon: "folder", open: true, children: [
          { type: "menu-item", id: "sp-mi-c1", label: "Child one", icon: "document",
            onClick: { behavior: "INVOKE", handler: "pick" } },
          { type: "menu-item", id: "sp-mi-c2", label: "Child two", icon: "document",
            onClick: { behavior: "INVOKE", handler: "pick" } }
        ] },
        { type: "menu-item", id: "sp-mi-href", label: "Plain link (href)", icon: "info",
          href: "#help" }
      ] },
    { type: "menu-button", id: "sp-mi-btn", label: "Popover items", align: "START", items: [
      { type: "menu-item", id: "sp-mi-p-edit", label: "Rename", icon: "edit",
        onClick: { behavior: "INVOKE", handler: "pick" } },
      { type: "menu-item", id: "sp-mi-p-dl", label: "Download", icon: "download",
        onClick: { behavior: "INVOKE", handler: "pick" } },
      { type: "menu-item", id: "sp-mi-p-sep", divider: true },
      { type: "menu-item", id: "sp-mi-p-del", label: "Delete", icon: "delete", danger: true,
        confirm: "Delete this item?", onClick: { behavior: "INVOKE", handler: "pick" } }
    ] }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("pick", (ctx) => {
    const el = ctx.sourceElement;
    const label = el?.querySelector(".sui-menu-label")?.textContent?.trim()
        || el?.textContent?.trim() || "item";
    return { patches: [], toasts: [ { level: "INFO", message: `Chose "${label}"`, durationMs: 1800 } ] };
  });
}
