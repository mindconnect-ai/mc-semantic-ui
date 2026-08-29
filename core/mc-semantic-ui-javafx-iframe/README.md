# Semantic UI :: JavaFX iframe

One node type: `iframe`, painted as a `WebView`.

```xml
<dependency>
    <groupId>ai.mindconnect</groupId>
    <artifactId>mc-semantic-ui-javafx-iframe</artifactId>
</dependency>
```

```java
var renderer = SuiFxRenderer.createDefaultRenderer();
SuiFxIFrame.install(renderer);
SuiFxIFrame.style(scene.getRoot());
```

## Why one type gets its own artifact

`javafx-web` carries a WebKit build for every platform it supports — tens of
megabytes. An application that embeds no pages should not ship a browser engine
in order to draw a table, so this single renderer lives apart and
[`mc-semantic-ui-javafx`](../mc-semantic-ui-javafx) stays lean.

Everything else the desktop paints, the app shell and its header included, is
in that module and needs no installing.

## What it does

`height` takes pixels. A viewport-relative length like `60vh` has no JavaFX
equivalent, so the view stays uncapped and fills what its parent column has
left. `src` resolves against the page it arrived on, so a server writing
`/docs/intro` works.

## `sandbox` is not a security boundary here

The attribute is a list of permissions the HTML spec defines for an
`<iframe>`, and a `WebView` implements none of that vocabulary. The renderer
honours the one distinction it can actually enforce — a `sandbox` without
`allow-scripts` turns JavaScript off — and can do nothing about the rest.

Point a `UiIFrame` at content you trust.
