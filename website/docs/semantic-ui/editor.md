---
title: The visual editor
sidebar_position: 10
---

# The visual editor

Design Semantic UI screens visually — **entirely in the browser, no backend, no
install**. The editor is a fully client-side app: projects and pages live in
`localStorage`, you edit and preview live, and export a runnable app when you're
done.

<p>
  <a href="pathname:///editor/"><b>▶ Open the visual editor ↗</b></a> — runs in
  your browser, nothing to set up.
</p>

It's embedded right here — this is the real editor, not a screenshot. Pick a
project, click a node in the tree, edit its JSON, watch the preview update:

<iframe
  src="/mc-semantic-ui/editor/index.html"
  title="The semantic-ui visual editor, running live"
  loading="lazy"
  style={{width: '100%', height: '640px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

:::tip More room
The editor is a full three-panel app — it's much nicer
[full-screen ↗](pathname:///editor/).
:::

## What it is

Three resizable panels, all driven by the same `SuiRenderer` as production (which
is why it was small to build — the UI model and the renderer are separate):

- **Tree** — outline of the UiNode tree, with add (`+`) and delete (`×`).
- **Property panel** — a **Monaco** JSON editor for the selected node with live
  **schema validation** generated from the real `UiNode` classes (wrong enum
  values, type mismatches and unknown node types are underlined inline); edits
  apply on blur or Ctrl/Cmd-Enter.
- **Live preview** — the actual tree rendered through the SUI renderer. Clicks on
  any rendered element select the corresponding tree node; selecting a tab
  switches the preview into that tab.

## Download a runnable app

This is the part people miss: the editor doesn't just draw mock-ups — it
**exports a working application** you can download and run. Pick a target and
you get a complete project:

| Export | What you get | Run it with |
|---|---|---|
| **Static** | `index.html` + `app.js` + the `sui/` runtime — no backend | open the file, or any static file server |
| **Spring Boot** | a Maven project with a controller per page returning `UiPage` | `mvn spring-boot:run` |
| **Node.js** | an Express server serving each page as `UiPage` JSON | `npm install && npm start` |

The output is **plain, hand-written semantic-ui code** — not a bundled copy of
the editor, and with no dependency back on it. So it's a real starting point:
design the screens visually, export, then keep building in your IDE.

It's also the fastest way to scaffold any of the
[three setups](./overview.md#choose-your-path).

## Embed it in your own app (optional)

The same editor is also an embeddable **Spring Boot starter** — add the
dependency, provide an `EditorContentStore`, and `/editor` is live inside your
app. A standalone showcase lives in `editor/mc-sui-editor-app`:

```bash
cd editor/mc-sui-editor-app
mvn spring-boot:run
# then open http://localhost:8080/editor
```

Its sample content exercises every node type (header, tabs, a table with a
SKU-link cell template, a form with various field kinds, multi-select rows, …) —
a good orientation when you first embed the editor.
