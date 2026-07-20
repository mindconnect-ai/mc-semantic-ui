// Specimen: what a header does when the room runs out.
//
// Two headers, same extras. The first wraps (the default, and what SSR does
// without JavaScript); the second keeps one row and moves the rest into a "⋯"
// dropdown. Narrow the window to see them diverge.
const extras = (p) => [
  { type: "link", id: `${p}-l1`, href: "#", label: "Dashboard" },
  { type: "link", id: `${p}-l2`, href: "#", label: "Orders" },
  { type: "link", id: `${p}-l3`, href: "#", label: "Customers" },
  { type: "link", id: `${p}-l4`, href: "#", label: "Reports" },
  { type: "link", id: `${p}-l5`, href: "#", label: "Settings" }
];

export const node = {
  type: "stack", id: "demo", gap: 16, children: [
    { type: "text", id: "t1", text: "WRAP (default) — the bar grows a second line:" },
    { type: "header", id: "h-wrap", brand: "Acme Admin",
      extras: extras("w"),
      user: { name: "Ada Lovelace", initials: "AL" } },

    { type: "text", id: "t2", text: "MENU — one row, the rest behind ⋯ :" },
    { type: "header", id: "h-menu", brand: "Acme Admin",
      extras: extras("m"), extrasOverflow: "MENU",
      user: { name: "Ada Lovelace", initials: "AL" } }
  ]
};

// No install() needed: the event bus wires the shared overflow behaviour on
// every mount, so a container marked extrasOverflow: "MENU" just works.
