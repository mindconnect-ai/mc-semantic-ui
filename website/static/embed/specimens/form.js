// Specimen: UiForm — flat fields, a form-level error banner, action styles and
// a footer link. Save flags every field, Cancel clears them; both buttons use
// behavior "INVOKE", so the whole thing runs in the browser with no backend.
const FIELDS = [
  { type: "field", id: "sf-name",  label: "Name",     fieldType: "TEXT",   editable: true, required: true, placeholder: "e.g. Widget" },
  { type: "field", id: "sf-price", label: "Price",    fieldType: "NUMBER", editable: true, value: 19.0, step: "0.01" },
  { type: "field", id: "sf-cat",   label: "Category", fieldType: "SELECT", editable: true, value: "tools",
    options: [ { value: "tools", label: "Tools" }, { value: "toys", label: "Toys" }, { value: "home", label: "Home" } ] },
  { type: "field", id: "sf-active", label: "Active",  fieldType: "BOOLEAN", editable: true, value: true }
];

const ERRORS = {
  "sf-name":   "Name is required.",
  "sf-price":  "Price must be greater than 0.",
  "sf-cat":    "Pick a category.",
  "sf-active": "This must be confirmed."
};

function makeForm(errored) {
  return {
    type: "form", id: "sp-form", title: "New product",
    formError: errored ? "Please fix the errors below." : null,
    fields: FIELDS.map(f => errored ? { ...f, validationError: ERRORS[f.id] } : f),
    actions: [
      { type: "action", id: "sf-save", label: "Save", style: "PRIMARY",
        onClick: { behavior: "INVOKE", handler: "sf-validate", payload: "sp-form" } },
      { type: "action", id: "sf-cancel", label: "Cancel", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "sf-reset" } }
    ],
    links: [ { type: "link", id: "sf-help", rel: "ref", href: "#", label: "Need help?" } ]
  };
}

export const node = makeForm(false);

export function install(renderer, bus) {
  bus.registerClientHandler("sf-validate", () => ({
    patches: [ { op: "REPLACE", targetId: "sp-form", node: makeForm(true) } ],
    toasts:  [ { level: "ERROR", message: "Please fix the errors below", durationMs: 2500 } ]
  }));
  bus.registerClientHandler("sf-reset", () => ({
    patches: [ { op: "REPLACE", targetId: "sp-form", node: makeForm(false) } ],
    toasts:  [ { level: "INFO", message: "Changes discarded", durationMs: 2000 } ]
  }));
}
