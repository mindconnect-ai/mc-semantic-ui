// Specimen: UiUpload — a drag-and-drop zone with a browse button. In real
// apps onUpload is a behavior "UPLOAD" trigger that multipart-POSTs the files.
// Here it is an "INVOKE" trigger so the handler can read the File objects
// client-side and list them — no backend, no network.
export const node = {
  type: "stack", id: "sp", gap: 12, children: [
    { type: "upload", id: "up-images", label: "Product images",
      hint: "PNG or JPG, max 5 MB each.",
      name: "files", accept: "image/*", multiple: true,
      dropText: "Drag images here or",
      buttonLabel: "Choose files…",
      onUpload: { behavior: "INVOKE", handler: "up-list" } },
    { type: "text", id: "up-result", text: "No files selected yet." }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("up-list", (ctx) => {
    const files = ctx.files || [];
    const names = files.map(f => `${f.name} (${Math.round(f.size / 1024)} kB)`).join(", ");
    return {
      patches: [ { op: "REPLACE", targetId: "up-result",
                   node: { type: "text", id: "up-result", text: `Selected: ${names}` } } ],
      toasts:  [ { level: "SUCCESS", message: `${files.length} file(s) ready`, durationMs: 2500 } ]
    };
  });
}
