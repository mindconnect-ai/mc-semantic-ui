// Specimen: UiTreeNode. A single node cannot render on its own — it is always
// a row inside a `tree`, so this is a minimal tree that shows off one node's
// features: icon, open, children, selected, onClick, content, and a labelNode
// carrying a whole other node (here: a menu-button, i.e. a per-row context menu).
const item = (id, label, icon) => ({ type: "menu-item", id, label, icon,
  onClick: { behavior: "INVOKE", handler: "rowAction" } });

export const node = {
  type: "tree", id: "sp-tree-node", title: "One node, four features",
  nodes: [
    // 1. plain leaf: label + icon, clickable
    { type: "tree-node", id: "n-leaf", label: "Leaf row — label + icon + onClick", icon: "document",
      onClick: { behavior: "INVOKE", handler: "rowAction" } },

    // 2. labelNode: the row title is a whole node tree, not a string
    { type: "tree-node", id: "n-rich", icon: "folder", label: "Assets", open: true,
      labelNode: { type: "stack", id: "n-rich-lbl", direction: "HORIZONTAL", gap: 8, children: [
        { type: "text", id: "n-rich-name", text: "Assets — labelNode holds any node" },
        { type: "menu-button", id: "n-rich-menu", align: "END", items: [
          item("n-ren", "Rename", "edit"),
          item("n-dl", "Download", "download"),
          { type: "menu-item", id: "n-sep", divider: true },
          { type: "menu-item", id: "n-del", label: "Delete", icon: "delete", danger: true,
            onClick: { behavior: "INVOKE", handler: "rowAction" } }
        ]}
      ]},
      children: [
        { type: "tree-node", id: "n-logo", label: "logo.svg", icon: "document", selected: true,
          onClick: { behavior: "INVOKE", handler: "rowAction" } },
        { type: "tree-node", id: "n-hero", label: "hero.jpg", icon: "document",
          onClick: { behavior: "INVOKE", handler: "rowAction" } }
      ]},

    // 3. content: an arbitrary component rendered inside the expanded row
    { type: "tree-node", id: "n-content", label: "Order #1024 — content is a detail node", icon: "grid",
      content: { type: "detail", id: "n-content-detail", fields: [
        { type: "field", id: "n-cust",  label: "Customer", fieldType: "TEXT",   value: "Grace Hopper" },
        { type: "field", id: "n-total", label: "Total",    fieldType: "NUMBER", value: 249.0 }
      ]}}
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("rowAction", (ctx) => ({
    patches: [], toasts: [ { level: "INFO", message: `${ctx.sourceElement?.textContent?.trim() || "row"} clicked` } ]
  }));
}
