// Specimen: UiHeader — page chrome. Brand (with logo) on the left, extras and
// the current-user widget on the right. Normally the first child of the page's
// top-level stack, above the content section.
// A monochrome outline mark: the stylesheet recolours header logos to the
// current theme's foreground, so a flat filled shape would come out as a blob.
const LOGO = "data:image/svg+xml;base64," + btoa(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" ' +
  'fill="none" stroke="#4f7cff" stroke-width="2" stroke-linejoin="round">' +
  '<circle cx="12" cy="12" r="10"/><path d="M7 15l5-7 5 7z"/></svg>');

export const node = {
  type: "stack", id: "sp", gap: 12, children: [
    { type: "header", id: "sp-header",
      brand: "Shop Admin",
      brandHref: "#",
      brandLogo: LOGO,
      extras: [
        { type: "action", id: "h-help", label: "Help", icon: "info", appearance: "ICON", style: "SECONDARY",
          onClick: { behavior: "INVOKE", handler: "chrome" } },
        { type: "action", id: "h-new", label: "New order", icon: "add", style: "PRIMARY",
          onClick: { behavior: "INVOKE", handler: "chrome" } }
      ],
      user: { name: "Ada Lovelace", initials: "AL", profileHref: "#" }
    },
    { type: "text", id: "sp-note",
      text: "Everything above is one header node. The content of the page goes below it." }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("chrome", (ctx) => ({
    patches: [], toasts: [ { level: "INFO", message: `${ctx.sourceElement?.getAttribute("aria-label") || ctx.sourceElement?.textContent?.trim() || "Header"} clicked` } ]
  }));
}
