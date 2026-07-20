// Specimen: UiStack — the SAME three buttons in both directions, so the only
// visible difference is `direction`. Then the same row twice to show `gap`.
const label = (id, t) => ({ type: "text", id, text: t });
const buttons = (p) => ([
  { type: "action", id: `${p}-save`,   label: "Save",   style: "PRIMARY"   },
  { type: "action", id: `${p}-cancel`, label: "Cancel", style: "SECONDARY" },
  { type: "action", id: `${p}-delete`, label: "Delete", style: "DANGER"    }
]);

export const node = {
  type: "stack", id: "sp", gap: 18, children: [
    label("l1", "direction: VERTICAL (the default) — one per line"),
    { type: "stack", id: "sp-v", gap: 8, children: buttons("v") },

    label("l2", "direction: HORIZONTAL — the same three buttons, side by side"),
    { type: "stack", id: "sp-h", direction: "HORIZONTAL", gap: 8, children: buttons("h") },

    label("l3", "…and gap is the only other knob — the same row with gap: 24"),
    { type: "stack", id: "sp-h24", direction: "HORIZONTAL", gap: 24, children: buttons("w") }
  ]
};
