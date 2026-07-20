// Specimen: UiSectionEntry. An entry has no standalone screen presence, so
// this is a small UiSection whose three entries show what an entry carries:
// title, icon, content — and one entry with an onClick trigger that fires
// alongside the panel switch.
const text = (id, t) => ({ type: "text", id, text: t });

export const node = {
  type: "section", id: "sp-entries", initialSection: "e-title",
  sections: [
    { type: "section-entry", id: "e-title", title: "Title only",
      content: text("c1", "id + title + content is the minimum an entry needs.") },
    { type: "section-entry", id: "e-icon", title: "With icon", icon: "folder",
      content: text("c2", "`icon` puts an icon token before the tab title.") },
    { type: "section-entry", id: "e-click", title: "With onClick", icon: "download",
      onClick: { behavior: "INVOKE", handler: "lazyLoad" },
      content: { type: "stack", id: "c3", gap: 6, children: [
        text("c3a", "This tab carries an onClick trigger. It fires in addition to the panel switch — the usual way to lazy-load a panel on first open.")
      ]}}
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("lazyLoad", () => ({
    patches: [], toasts: [ { level: "INFO", message: "onClick fired — the panel switched anyway" } ]
  }));
}
