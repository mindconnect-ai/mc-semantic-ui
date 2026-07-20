// Specimen: the app-shell node.
//
// Compare with what this used to take: three nested stacks plus five CSS rules
// the app had to know about. Here there is one node, three slots, and no CSS.
const nav = [
  { type: "menu-item", id: "nav-dash", label: "Dashboard", icon: "grid", selected: true,
    onClick: { behavior: "INVOKE", handler: "go" } },
  { type: "menu-item", id: "nav-cat", label: "Catalog", icon: "folder", open: true, children: [
    { type: "menu-item", id: "nav-prod", label: "Products",  icon: "document",
      onClick: { behavior: "INVOKE", handler: "go" } },
    { type: "menu-item", id: "nav-cust", label: "Customers", icon: "document",
      onClick: { behavior: "INVOKE", handler: "go" } }
  ]},
  { type: "menu-item", id: "nav-ord", label: "Orders", icon: "download", badge: "12",
    onClick: { behavior: "INVOKE", handler: "go" } }
];

export const node = {
  type: "app-shell", id: "shell",
  header: { type: "header", id: "shell-hdr", brand: "Acme Admin",
            user: { name: "Ada Lovelace", initials: "AL" } },
  menu: { type: "menu", id: "nav", title: "Acme", state: "EXPANDED",
          mode: "RESPONSIVE", items: nav },
  footer: { type: "text", id: "shell-foot", text: "Acme Admin · v2.4.0 · All systems operational" },
  content: { type: "stack", id: "page", gap: 12, children: [
    { type: "text", id: "page-h", text: "Dashboard" },
    { type: "text", id: "page-t",
      text: "Click a menu entry: only this panel is patched — the header and the sidebar are never re-rendered. Click the burger to collapse the sidebar." }
  ]}
};

export function install(renderer, bus) {
  // The content container has a predictable id (<shell-id>-content), which is
  // what makes "navigate without touching the chrome" a one-line patch.
  bus.registerClientHandler("go", (ctx) => {
    // .sui-menu-label excludes the badge; textContent would give "Orders12".
    const el = ctx.sourceElement;
    const label = el?.querySelector(".sui-menu-label")?.textContent?.trim()
        || el?.textContent?.trim() || "Dashboard";
    return {
      patches: [ { op: "REPLACE", targetId: "shell-content", node: {
        type: "stack", id: "page", gap: 12, children: [
          { type: "text", id: "page-h", text: label },
          { type: "text", id: "page-t", text: `This panel was replaced by a UiPatch targeting "shell-content".` }
        ]
      } } ]
    };
  });
}
