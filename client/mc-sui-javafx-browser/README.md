# Semantic UI :: JavaFX browser

A browser for `UiNode` servers. Type a url, and whatever the server sends back
is painted by the JavaFX renderer.

```bash
mvn -pl client/mc-sui-javafx-browser javafx:run
```

Or start on a page:

```bash
mvn -pl client/mc-sui-javafx-browser javafx:run -Dsui.url=http://localhost:8080/ui
```

## What it is for

It knows nothing about any particular application — no endpoint list, no model
classes of its own, no assumptions about what a page contains. It fetches one
url; everything after that is driven by the triggers in the tree the server
sent.

That makes it a test of the premise the whole repo rests on. Point it at an
endpoint the SPA renderer already drives and see whether the same JSON comes up
the same way on the desktop. Where it does not, the gap is a renderer gap worth
knowing about — which is more useful to find here than in an application.

## How it works

Navigation is an ordinary `APPLY_RESPONSE` GET — the same trigger a link inside
the page would fire. A typed url and a clicked link take exactly the same
route, deliberately: a client that fetched its own way would be testing
something the real one never does.

The address bar is this client's answer to the one thing a desktop window
lacks. A `UiPage` may carry a `navigate` hint, which in a browser is a history
push; here it lands in the address bar via `SuiFxEventBus.setNavigateHandler`.
Back and forward are the browser's own state, and taking a new turn drops the
forward branch, as an address bar does.

It installs
[`mc-semantic-ui-javafx-iframe`](../../core/mc-semantic-ui-javafx-iframe),
since a browser has no say in what a page embeds. The app shell itself needs
nothing — it is part of the renderer.

## What a server has to send

A `UiPage`, or a bare `UiNode`, as JSON:

```json
{"type":"page","node":{"type":"stack","children":[
  {"type":"text","id":"body","text":"hello"}
]}}
```

Streaming works too: a trigger with `"behavior":"STREAM"` opens its url as
Server-Sent Events, and `event: patch` frames are applied as they arrive.
