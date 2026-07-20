// Specimen: UiDialog — opened by APPENDing a dialog node into the body-level
// #sui-dialogs host, closed by REMOVE-ing it by id (the × and the backdrop do
// that on their own).
export const node = {
  type: "stack", id: "sp", gap: 12, children: [
    { type: "text", id: "sp-t",
      text: "A dialog is a normal UiNode. Open it with a patch; the × and the backdrop close it with no code." },
    { type: "action", id: "sp-open", label: "Delete customer…", style: "PRIMARY", icon: "delete",
      onClick: { behavior: "INVOKE", handler: "openDialog" } }
  ]
};

const dialogNode = {
  type: "dialog", id: "sp-dialog", title: "Delete customer?",
  node: { type: "stack", id: "sp-dlg-body", gap: 14, children: [
    { type: "text", id: "sp-dlg-msg",
      text: "This permanently removes Ada Lovelace and all associated orders. The body of a dialog can be any tree — a form, a table, a detail view." },
    { type: "stack", id: "sp-dlg-actions", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "sp-dlg-cancel", label: "Cancel", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "closeDialog" } },
      { type: "action", id: "sp-dlg-confirm", label: "Delete", style: "DANGER",
        onClick: { behavior: "INVOKE", handler: "confirmDelete" } }
    ]}
  ]}
};

export function install(renderer, bus) {
  bus.registerClientHandler("openDialog", () => ({
    patches: [ { op: "APPEND", targetId: "sui-dialogs", node: dialogNode } ]
  }));
  bus.registerClientHandler("closeDialog", () => ({
    patches: [ { op: "REMOVE", targetId: "sp-dialog" } ]
  }));
  bus.registerClientHandler("confirmDelete", () => ({
    patches: [ { op: "REMOVE", targetId: "sp-dialog" } ],
    toasts:  [ { level: "SUCCESS", message: "Customer deleted" } ]
  }));
}
