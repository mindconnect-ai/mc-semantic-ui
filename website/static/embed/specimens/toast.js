// Specimen: UiToast — one button per level. A toast is not a node: it rides
// on the response envelope (page.toasts / patch.toasts), so each button here
// returns a patch with no DOM operations and a single toast.
export const node = {
  type: "stack", id: "sp", gap: 12, children: [
    { type: "text", id: "sp-t",
      text: "Toasts stack in the corner and dismiss themselves. Each button below returns a patch that carries nothing but a toast." },
    { type: "stack", id: "sp-levels", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "t-info",  label: "Info",    style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "toastInfo" } },
      { type: "action", id: "t-ok",    label: "Success", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "toastSuccess" } },
      { type: "action", id: "t-warn",  label: "Warning", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "toastWarn" } },
      { type: "action", id: "t-err",   label: "Error",   style: "DANGER",
        onClick: { behavior: "INVOKE", handler: "toastError" } }
    ]},
    { type: "stack", id: "sp-extra", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "t-titled", label: "With a title", style: "SECONDARY", appearance: "LINK",
        onClick: { behavior: "INVOKE", handler: "toastTitled" } },
      { type: "action", id: "t-sticky", label: "Sticky (durationMs: 0)", style: "SECONDARY", appearance: "LINK",
        onClick: { behavior: "INVOKE", handler: "toastSticky" } }
    ]}
  ]
};

export function install(renderer, bus) {
  const toast = (t) => () => ({ patches: [], toasts: [ t ] });
  bus.registerClientHandler("toastInfo",    toast({ level: "INFO",    message: "Saved to drafts" }));
  bus.registerClientHandler("toastSuccess", toast({ level: "SUCCESS", message: "Changes published" }));
  bus.registerClientHandler("toastWarn",    toast({ level: "WARN",    message: "Storage almost full" }));
  bus.registerClientHandler("toastError",   toast({ level: "ERROR",   message: "Upload failed" }));
  bus.registerClientHandler("toastTitled",  toast({ level: "ERROR", title: "Upload failed",
                                                   message: "product-list.csv is larger than 5 MB" }));
  bus.registerClientHandler("toastSticky",  toast({ level: "INFO", durationMs: 0,
                                                   message: "This one stays until you close it" }));
}
