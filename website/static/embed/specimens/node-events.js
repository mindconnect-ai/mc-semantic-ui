// Specimen: event triggers are inherited by every node type. Three nodes that
// used to be inert — a stack, a text node and a field — all react, and none of
// them is an action.
export const node = {
  type: "stack", id: "demo", gap: 14, children: [
    { type: "text", id: "log", text: "Nothing yet — interact below." },

    // A layout node as a click target.
    { type: "stack", id: "card", gap: 6, cssClass: "evt-card",
      onClick: { behavior: "INVOKE", handler: "say", payload: "clicked the stack" },
      onHover: { behavior: "INVOKE", handler: "say", payload: "hovering the stack" },
      onLeave: { behavior: "INVOKE", handler: "say", payload: "left the stack" },
      children: [
        { type: "text", id: "card-t", text: "A plain UiStack — click me, or hover." },
        { type: "text", id: "card-s", text: "Its children don't re-fire hover." }
      ]},

    // A text node with a double-click.
    { type: "text", id: "dbl", text: "A UiText with onDblClick.",
      onDblClick: { behavior: "INVOKE", handler: "say", payload: "double-clicked the text" } },

    // A form control — the same onChange it always had, now inherited.
    { type: "field", id: "pick", label: "A field with onChange", fieldType: "SELECT",
      editable: true, value: "a",
      options: [ { value: "a", label: "Alpha" }, { value: "b", label: "Beta" } ],
      onChange: { behavior: "INVOKE", handler: "say", payload: "field changed" } }
  ]
};

export function install(renderer, bus) {
  let n = 0;
  bus.registerClientHandler("say", (ctx) => {
    // The trigger's payload rides along, so one handler serves every node.
    const what = ctx.trigger?.payload ?? "event";
    return {
      patches: [ { op: "REPLACE", targetId: "log",
                   node: { type: "text", id: "log", text: `${++n}. ${what}` } } ]
    };
  });

  const style = document.createElement("style");
  style.textContent = `.evt-card { border: 1px solid var(--sui-color-border); border-radius: 8px;
                                   padding: 12px 14px; cursor: pointer; }
                       .evt-card:hover { background: var(--sui-color-surface-alt); }`;
  document.head.appendChild(style);
}
