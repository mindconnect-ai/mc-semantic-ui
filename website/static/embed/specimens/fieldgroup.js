// Specimen: UiFieldGroup — two fieldsets inside one form. The groups are pure
// structure: every field still carries its own name, so the form submits as a
// single flat object regardless of the grouping.
export const node = {
  type: "form", id: "sp-fg-form", title: "Customer",
  content: [
    { type: "fieldgroup", id: "fg-contact", title: "Contact",
      hint: "How we reach this customer.", content: [
        { type: "field", id: "fg-email", label: "E-mail", fieldType: "TEXT",
          editable: true, required: true, value: "ada@example.com" },
        { type: "field", id: "fg-phone", label: "Phone", fieldType: "TEXT",
          editable: true, placeholder: "+49 …" }
      ]},
    { type: "fieldgroup", id: "fg-address", title: "Shipping address", content: [
      { type: "stack", id: "fg-addr-row", direction: "HORIZONTAL", gap: 12, children: [
        { type: "field", id: "fg-zip", label: "ZIP", fieldType: "TEXT",
          editable: true, value: "10115" },
        { type: "field", id: "fg-city", label: "City", fieldType: "TEXT",
          editable: true, value: "Berlin" }
      ]},
      { type: "field", id: "fg-country", label: "Country", fieldType: "SELECT",
        editable: true, value: "de", options: [
          { value: "de", label: "Germany" },
          { value: "at", label: "Austria" },
          { value: "ch", label: "Switzerland" }
        ]}
    ]}
  ],
  actions: [
    { type: "action", id: "fg-save", label: "Save", style: "PRIMARY",
      onClick: { behavior: "INVOKE", handler: "fg-show", payload: "sp-fg-form" } }
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("fg-show", (ctx) => ({
    // A UiPatch is recognised by its `patches` array — keep it, even when empty.
    patches: [],
    toasts: [ { level: "SUCCESS",
      message: `Submitted as one object: ${Object.keys(ctx.payload || {}).join(", ")}`,
      durationMs: 4000 } ]
  }));
}
