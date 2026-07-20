// Specimen: UiList — items with an icon and a description, per-item actions,
// a rich labelNode, a collapsible item carrying a whole node as content, and
// pagination.
export const node = {
  type: "list", id: "activity", title: "Recent activity",
  actions: [
    { type: "action", id: "l-refresh", label: "Refresh", style: "SECONDARY",
      onClick: { behavior: "INVOKE", handler: "say" } }
  ],
  items: [
    { id: "l1", label: "Order #1024 shipped", icon: "success",
      description: "web-frontend · 2 minutes ago",
      onClick: { behavior: "INVOKE", handler: "say" },
      actions: [
        { type: "action", id: "l1-show", label: "Show", icon: "show",
          appearance: "ICON", style: "SECONDARY",
          onClick: { behavior: "INVOKE", handler: "say" } }
      ]
    },
    { id: "l2", label: "Stock running low", icon: "warning",
      description: "Gadget · 12 left",
      onClick: { behavior: "INVOKE", handler: "say" },
      actions: [
        { type: "action", id: "l2-order", label: "Reorder", style: "PRIMARY",
          onClick: { behavior: "INVOKE", handler: "say" } }
      ]
    },
    { id: "l3", label: "Import job",
      labelNode: { type: "stack", id: "l3-head", direction: "HORIZONTAL", gap: 8, children: [
        { type: "icon", id: "l3-icon", name: "document" },
        { type: "text", id: "l3-title", text: "Import job" },
        { type: "text", id: "l3-badge", text: "· running" }
      ]},
      description: "3 of 8 files processed"
    },
    { id: "l4", label: "Nightly build",
      collapseSummary: "Nightly build — 3 warnings (click to expand)",
      collapseOpen: false,
      content: { type: "stack", id: "l4-body", gap: 6, children: [
        { type: "text", id: "l4-1", text: "Build passed in 2 m 14 s." },
        { type: "text", id: "l4-2", text: "3 non-blocking lint warnings in mc-semantic-ui-core." }
      ]}
    }
  ],
  pagination: { page: 1, size: 4, total: 42 }
};

export function install(renderer, bus) {
  bus.registerClientHandler("say", (ctx) => ({
    patches: [], toasts: [ { level: "INFO",
                message: `Clicked "${ctx.sourceElement?.textContent?.trim() || "item"}"` } ]
  }));
}
