// Specimen: UiSection — the tabbed mode (three tabs, client-side switching)
// and, in the last tab, the collapsible mode.
const text = (id, t) => ({ type: "text", id, text: t });

export const node = {
  type: "stack", id: "sp", gap: 16, children: [
    { type: "section", id: "sp-tabs", title: "Product", initialSection: "t-overview",
      sections: [
        { type: "section-entry", id: "t-overview", title: "Overview", icon: "info",
          content: { type: "stack", id: "c-overview", gap: 6, children: [
            text("o1", "Every section-entry is one tab plus its panel."),
            text("o2", "Switching tabs happens in the browser — no request.")
          ]}},
        { type: "section-entry", id: "t-stock", title: "Stock", icon: "grid",
          content: { type: "detail", id: "c-stock", fields: [
            { type: "field", id: "f-sku",   label: "SKU",      fieldType: "TEXT", value: "A-1" },
            { type: "field", id: "f-qty",   label: "On hand",  fieldType: "TEXT", value: "128" },
            { type: "field", id: "f-ware",  label: "Warehouse", fieldType: "TEXT", value: "Hamburg" }
          ]}},
        { type: "section-entry", id: "t-history", title: "History", icon: "document",
          content: text("h1", "All panels are in the DOM; the inactive ones are hidden.") }
      ]},
    { type: "section", id: "sp-collapse",
      collapseSummary: "Advanced settings", collapseOpen: false,
      sections: [
        { type: "section-entry", id: "adv-body",
          content: { type: "stack", id: "c-adv", gap: 6, children: [
            text("a1", "With collapseSummary set, the same node renders as a native <details> disclosure — no JavaScript involved."),
            text("a2", "collapseOpen: false means it starts closed.")
          ]}}
      ]}
  ]
};
