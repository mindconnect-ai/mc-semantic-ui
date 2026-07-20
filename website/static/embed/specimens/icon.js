// Specimen: UiIcon — a gallery of real tokens from icons/icons.json (semantic
// aliases and raw sprite ids), status colours, and icons used through the
// `icon` shorthand on an action and a field.

// Semantic aliases (left of the arrow in icons/icons.json).
const ALIASES = [
  "add", "edit", "delete", "save", "search",
  "filter", "refresh", "download", "upload", "settings",
  "user", "users", "calendar", "folder", "document",
  "mail", "lock", "star", "chart", "home"
];

// Raw sprite ids with no alias — the "passthrough" block of icons.json.
const RAW = ["chevron-right", "circle-plus", "circle-alert", "monitor", "smartphone"];

const chip = (token) => ({
  type: "stack", id: `chip-${token}`, direction: "HORIZONTAL", gap: 6, children: [
    { type: "icon", id: `ico-${token}`, name: token, title: token },
    { type: "text", id: `lbl-${token}`, text: token }
  ]
});

const row = (id, tokens) => ({
  type: "stack", id, direction: "HORIZONTAL", gap: 18, children: tokens.map(chip)
});

const rows = [];
for (let i = 0; i < ALIASES.length; i += 5) {
  rows.push(row(`sp-row-${i}`, ALIASES.slice(i, i + 5)));
}
rows.push(row("sp-row-raw", RAW));

export const node = {
  type: "stack", id: "sp", gap: 12, children: [
    ...rows,
    { type: "stack", id: "sp-status", direction: "HORIZONTAL", gap: 18, children: [
      { type: "icon", id: "sp-ok",    name: "success", title: "Success", cssClass: "sui-icon--success" },
      { type: "icon", id: "sp-warn",  name: "warning", title: "Warning", cssClass: "sui-icon--warning" },
      { type: "icon", id: "sp-err",   name: "error",   title: "Error",   cssClass: "sui-icon--danger" },
      { type: "icon", id: "sp-muted", name: "info",    title: "Info",    cssClass: "sui-icon--muted" },
      { type: "icon", id: "sp-emoji", name: "🎉", title: "A legacy emoji is rendered verbatim" }
    ]},
    { type: "stack", id: "sp-inline", direction: "HORIZONTAL", gap: 12, children: [
      { type: "action", id: "sp-btn", label: "Export", style: "SECONDARY", icon: "download" },
      { type: "action", id: "sp-icon-btn", label: "Edit", icon: "edit",
        appearance: "ICON", style: "SECONDARY" },
      { type: "field", id: "sp-field", label: "Search", fieldType: "TEXT",
        editable: true, icon: "search", placeholder: "Filter products…" }
    ]}
  ]
};
