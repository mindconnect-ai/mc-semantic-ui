---
title: Rendering modes
sidebar_position: 8
---

# Rendering modes

The same `UiPage` tree renders several ways. On the web the mode is chosen by
content negotiation and whether the SPA bootstrap script is loaded — never by
changing the controller. Off the web, the same tree drives a native JavaFX
desktop client.

## Server-side rendering (SSR)

The controller assembles the UiNode tree; `SuiServerRenderer` turns it into HTML
on the JVM. The browser gets a finished page — no JavaScript required, forms and
links work natively.

![SSR render flow](/img/semantic-ui/render-flow-ssr.svg)

## Single-page app (SPA)

With the bootstrap script loaded, the `SuiEventBus` intercepts clicks on
`[data-trigger]` elements. The same controller returns the new `UiPage` as JSON;
the browser-side `SuiRenderer` runs each node through its TypeScript handler, and
**Idiomorph** diffs the result against the live DOM — focus and scroll position
preserved.

![SPA render flow](/img/semantic-ui/render-flow-spa.svg)

## Partial updates with `UiPatch`

A controller doesn't have to return the whole page. A `UiPatch` is a tiny diff
of operations (`REPLACE`, `APPEND`, `CLEAR`, `REMOVE`) addressing nodes by `id`.
The SPA renderer applies it via `applyPatch(...)` — ideal for chatty
interactions and streaming without re-shipping the full tree.

![UiPatch flow](/img/semantic-ui/render-flow-patch.svg)

Streaming chat is a natural fit: each token batch is an `APPEND` patch targeting
the message node.

![Chat stream patch](/img/semantic-ui/chat-stream-patch.svg)

## Off the web entirely: JavaFX

The same tree also renders as a **native desktop client**. `UiTable` becomes a
real JavaFX `TableView`, `UiField` a real `TextField` — no web view involved.
Triggers and `UiPatch` are the same objects, so an endpoint that already answers
a browser can answer the desktop client unchanged.

See **[JavaFX desktop client](./javafx.md)**.

## Not a full page? Embed an island

You don't need a full SPA shell either — you can mount a single tree into a
`<div>` on any existing page. See **[Embed as a UI island](./ui-island.md)**.
