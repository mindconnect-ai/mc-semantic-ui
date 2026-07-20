// Specimen: UiMenuButton — both trigger shapes. The icon-only kebab is the
// standard per-row context menu; the labelled one is a dropdown with a nested
// submenu. Opening, closing (outside click / Escape) and positioning are
// handled by the event bus.
export const node = {
  type: "stack", id: "sp-mb", gap: 14, children: [
    { type: "stack", id: "sp-mb-row", direction: "HORIZONTAL", gap: 16, children: [
      // Icon-only kebab — a context menu, opening rightwards.
      { type: "menu-button", id: "sp-mb-kebab", align: "START", items: [
        { type: "menu-item", id: "sp-mb-k-ren", label: "Rename", icon: "edit",
          onClick: { behavior: "INVOKE", handler: "act" } },
        { type: "menu-item", id: "sp-mb-k-dl", label: "Download", icon: "download",
          onClick: { behavior: "INVOKE", handler: "act" } },
        { type: "menu-item", id: "sp-mb-k-sep", divider: true },
        { type: "menu-item", id: "sp-mb-k-del", label: "Delete", icon: "delete", danger: true,
          confirm: "Delete this file?", onClick: { behavior: "INVOKE", handler: "act" } }
      ] },
      // Labelled dropdown — with a nested submenu (menu-item children).
      { type: "menu-button", id: "sp-mb-actions", label: "Actions", icon: "grid", align: "START", items: [
        { type: "menu-item", id: "sp-mb-a-exp", label: "Export CSV", icon: "download",
          onClick: { behavior: "INVOKE", handler: "act" } },
        { type: "menu-item", id: "sp-mb-a-add", label: "New record", icon: "add",
          onClick: { behavior: "INVOKE", handler: "act" } },
        { type: "menu-item", id: "sp-mb-a-move", label: "Move to", icon: "folder", children: [
          { type: "menu-item", id: "sp-mb-a-m1", label: "Inbox", icon: "folder",
            onClick: { behavior: "INVOKE", handler: "act" } },
          { type: "menu-item", id: "sp-mb-a-m2", label: "Archive", icon: "folder",
            onClick: { behavior: "INVOKE", handler: "act" } }
        ] },
        { type: "menu-item", id: "sp-mb-a-sep", divider: true },
        { type: "menu-item", id: "sp-mb-a-help", label: "Help", icon: "info", href: "#help" }
      ] }
    ] },
    { type: "text", id: "sp-mb-hint",
      text: "Left: the icon-only kebab (a row context menu). Right: a labelled dropdown with a submenu." }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("act", (ctx) => {
    const el = ctx.sourceElement;
    const label = el?.querySelector(".sui-menu-label")?.textContent?.trim()
        || el?.textContent?.trim() || "item";
    return { patches: [], toasts: [ { level: "INFO", message: `Chose "${label}"`, durationMs: 1800 } ] };
  });
}
