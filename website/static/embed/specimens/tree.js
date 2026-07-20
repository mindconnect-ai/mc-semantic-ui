// Specimen: UiTree — a small file explorer. Two levels, two folders open by
// default; expand/collapse is handled entirely client-side by the bus.
const file = (id, name) => ({
  type: "tree-node", id, label: name, icon: "document",
  onClick: { behavior: "INVOKE", handler: "openFile" }
});

export const node = {
  type: "tree", id: "sp-tree", title: "File explorer",
  nodes: [
    { type: "tree-node", id: "t-src", label: "src", icon: "folder", open: true, children: [
      { type: "tree-node", id: "t-main", label: "main", icon: "folder", open: true, children: [
        file("t-app", "app.ts"),
        file("t-boot", "boot.ts"),
        { type: "tree-node", id: "t-cmp", label: "components", icon: "folder", children: [
          file("t-btn", "Button.ts"),
          { type: "tree-node", id: "t-tree", label: "Tree.ts", icon: "document", selected: true,
            onClick: { behavior: "INVOKE", handler: "openFile" } }
        ]}
      ]},
      { type: "tree-node", id: "t-test", label: "test", icon: "folder", children: [
        file("t-spec", "app.spec.ts")
      ]}
    ]},
    file("t-readme", "README.md"),
    file("t-pom", "pom.xml")
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("openFile", (ctx) => ({
    patches: [], toasts: [ { level: "INFO", message: `Opened ${ctx.sourceElement?.textContent?.trim() || "file"}` } ]
  }));
}
