// Specimen: a guarded tab. "Billing" carries selectOnClick: false, so the click
// fires its trigger but the panel does NOT change. The handler decides — here
// it asks first, then selects the tab by patching the section's initialSection.
const panel = (id, text) => ({ type: "text", id, text });

function section(active) {
  return {
    type: "section", id: "sec", initialSection: active,
    sections: [
      { type: "section-entry", id: "overview", title: "Overview",
        content: panel("p1", "An ordinary tab — clicking switches straight away.") },
      { type: "section-entry", id: "reports", title: "Reports",
        content: panel("p2", "Also ordinary.") },
      // The guarded one.
      { type: "section-entry", id: "billing", title: "Billing (guarded)",
        selectOnClick: false,
        onClick: { behavior: "INVOKE", handler: "mayOpenBilling" },
        content: panel("p3", "You got here through the guard — the handler patched initialSection.") }
    ]
  };
}

export const node = {
  type: "stack", id: "wrap", gap: 12, children: [
    { type: "text", id: "log", text: "Click “Billing (guarded)” — it will ask first." },
    section("overview")
  ]
};

export function install(renderer, bus) {
  bus.registerClientHandler("mayOpenBilling", () => {
    const allowed = window.confirm("Leave this tab and open Billing?");
    if (!allowed) {
      return { patches: [], toasts: [{ level: "INFO", message: "Stayed put — the panel never changed." }] };
    }
    // Selecting a tab from the application side: re-render the section with a
    // different initialSection. The morpher keeps the surrounding DOM stable.
    return {
      patches: [ { op: "REPLACE", targetId: "sec", node: section("billing") } ],
      toasts: [ { level: "SUCCESS", message: "Allowed — switched by patch." } ]
    };
  });
}
