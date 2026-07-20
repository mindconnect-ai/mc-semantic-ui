// Specimen: UiAction — the three styles, the three appearances, and the
// states (disabled, confirm, declarative loading).
export const node = {
  type: "stack", id: "sp", gap: 14, children: [
    { type: "stack", id: "sp-styles", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "a-primary",   label: "Save",   style: "PRIMARY",
        onClick: { behavior: "INVOKE", handler: "say" } },
      { type: "action", id: "a-secondary", label: "Cancel", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "say" } },
      { type: "action", id: "a-danger",    label: "Delete", style: "DANGER",
        confirm: "Delete this product?",
        onClick: { behavior: "INVOKE", handler: "say" } }
    ]},
    { type: "stack", id: "sp-appearance", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "a-icon-label", label: "Export", style: "SECONDARY", icon: "download",
        onClick: { behavior: "INVOKE", handler: "say" } },
      { type: "action", id: "a-icon-only", label: "Edit", icon: "edit",
        appearance: "ICON", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "say" } },
      { type: "action", id: "a-link", label: "View details", appearance: "LINK",
        onClick: { behavior: "INVOKE", handler: "say" } }
    ]},
    { type: "stack", id: "sp-states", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "a-disabled", label: "Publish", style: "PRIMARY",
        enabled: false, disabledReason: "Fill in the required fields first" },
      { type: "action", id: "a-loading", label: "Importing…", style: "SECONDARY",
        loading: true }
    ]}
  ]
};

export function install(renderer, bus) {
  // NOTE: `patches: []` is required. The bus recognises a UiPatch by
  // Array.isArray(body.patches) — a toast-only object matches neither UiPatch
  // nor UiPage and is discarded with a console warning.
  bus.registerClientHandler("say", (ctx) => ({
    patches: [],
    toasts: [ { level: "INFO", message: `Clicked "${ctx.sourceElement?.textContent?.trim() || "action"}"` } ]
  }));
}
