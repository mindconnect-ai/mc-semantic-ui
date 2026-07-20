// Probe: a clickable row containing its own action button.
export const node = {
  type: "stack", id: "probe", gap: 10, children: [
    { type: "text", id: "log", text: "—" },
    { type: "stack", id: "row", gap: 8, direction: "HORIZONTAL", cssClass: "row-box",
      onClick: { behavior: "INVOKE", handler: "say", payload: "ROW" } },
      children: [
        { type: "text", id: "row-t", text: "Row (clickable)" },
        { type: "action", id: "btn", label: "Button", style: "PRIMARY",
          onClick: { behavior: "INVOKE", handler: "say", payload: "BUTTON" } },
        { type: "link", id: "lnk", href: "#", label: "A link",
          onClick: { behavior: "INVOKE", handler: "say", payload: "LINK" } }
      ]}
  ];
export function install(renderer, bus) {
  bus.registerClientHandler("say", (ctx) => ({
    patches: [ { op: "REPLACE", targetId: "log",
                 node: { type: "text", id: "log", text: "fired: " + (ctx.trigger?.payload ?? "?") } } ]
  }));
  const st = document.createElement("style");
  st.textContent = ".row-box{border:1px solid var(--sui-color-border);border-radius:8px;padding:10px;cursor:pointer}";
  document.head.appendChild(st);
}
