// Specimen: UiProgress — determinate and indeterminate bars, status colours,
// rings, and a button that advances the live bar with a REPLACE patch.
export const node = {
  type: "stack", id: "sp", gap: 14, children: [
    { type: "progress", id: "sp-live", value: 20 },
    { type: "progress", id: "sp-done", value: 100, status: "SUCCESS" },
    { type: "progress", id: "sp-warn", value: 80, status: "WARNING", showValue: false },
    { type: "progress", id: "sp-ind" },
    { type: "stack", id: "sp-rings", direction: "HORIZONTAL", gap: 24, children: [
      { type: "progress", id: "sp-ring", value: 45, variant: "CIRCLE" },
      { type: "progress", id: "sp-ring-ok", value: 100, variant: "CIRCLE", status: "SUCCESS" },
      { type: "progress", id: "sp-ring-ind", variant: "CIRCLE" },
      { type: "action", id: "sp-advance", label: "Advance +20%", style: "SECONDARY",
        onClick: { behavior: "INVOKE", handler: "advance" } }
    ]}
  ]
};

export function install(renderer, bus) {
  let value = 20;
  bus.registerClientHandler("advance", () => {
    value = value >= 100 ? 0 : Math.min(100, value + 20);
    return {
      patches: [
        { op: "REPLACE", targetId: "sp-live",
          node: { type: "progress", id: "sp-live", value,
                  status: value >= 100 ? "SUCCESS" : "NORMAL" } },
        { op: "REPLACE", targetId: "sp-ring",
          node: { type: "progress", id: "sp-ring", value, variant: "CIRCLE" } }
      ]
    };
  });
}
