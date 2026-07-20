// Specimen: UiLink — plain navigation, an icon link, an external link that
// opens in a new tab, and one link that dispatches a trigger through the bus
// instead of navigating.
export const node = {
  type: "stack", id: "sp", gap: 14, children: [
    { type: "stack", id: "sp-plain", direction: "HORIZONTAL", gap: 16, children: [
      { type: "link", id: "back", rel: "back", href: "#", label: "Back to products" },
      { type: "link", id: "next", rel: "next", href: "#", label: "Next page" }
    ]},
    { type: "stack", id: "sp-icons", direction: "HORIZONTAL", gap: 16, children: [
      { type: "link", id: "manual", rel: "ref", href: "#", label: "Download the manual",
        icon: "download" },
      { type: "link", id: "spec", rel: "ref", href: "#", label: "Specification sheet",
        icon: "document" }
    ]},
    { type: "stack", id: "sp-special", direction: "HORIZONTAL", gap: 16, children: [
      { type: "link", id: "site", rel: "ref", href: "https://example.com",
        label: "Vendor website", external: true },
      { type: "link", id: "history", rel: "ref", href: "#", label: "Load history",
        icon: "info", onClick: { behavior: "INVOKE", handler: "loadHistory" } }
    ]},
    { type: "text", id: "sp-slot", text: "" }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("loadHistory", () => ({
    patches: [
      { op: "REPLACE", targetId: "sp-slot",
        node: { type: "text", id: "sp-slot",
                text: "Last edited 12 June 2026 by A. Meyer." } }
    ]
  }));
}
