// Specimen: UiSpinner — the three sizes, a labelled spinner, and a spinner
// used as a placeholder for content that has not arrived yet.
export const node = {
  type: "stack", id: "sp", gap: 16, children: [
    { type: "stack", id: "sp-sizes", direction: "HORIZONTAL", gap: 24, children: [
      { type: "spinner", id: "sp-sm", size: "SM", title: "Loading" },
      { type: "spinner", id: "sp-md", size: "MD", title: "Loading" },
      { type: "spinner", id: "sp-lg", size: "LG", title: "Loading" }
    ]},
    { type: "stack", id: "sp-labelled", direction: "HORIZONTAL", gap: 24, children: [
      { type: "spinner", id: "sp-lbl", label: "Loading…" },
      { type: "spinner", id: "sp-lbl-lg", size: "LG", label: "Importing products…" }
    ]},
    { type: "stack", id: "sp-auto", direction: "HORIZONTAL", gap: 8, children: [
      { type: "action", id: "sp-busy", label: "Saving…", style: "PRIMARY", loading: true },
      { type: "text", id: "sp-auto-t",
        text: "← no spinner node here: a busy control is the action's own loading state." }
    ]}
  ]
};
