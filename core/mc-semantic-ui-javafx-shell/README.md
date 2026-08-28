# Semantic UI :: JavaFX shell

Three more node types for the [JavaFX renderer](../mc-semantic-ui-javafx):
`app-shell`, `header` and `iframe`.

```xml
<dependency>
    <groupId>ai.mindconnect</groupId>
    <artifactId>mc-semantic-ui-javafx-shell</artifactId>
</dependency>
```

```java
var renderer = SuiFxRenderer.createDefaultRenderer();
SuiFxShell.install(renderer);          // registers the three renderers
SuiFxShell.style(scene.getRoot());     // adds sui-fx-shell.css
```

## Why a separate module

`iframe` is a `WebView`, and `javafx-web` carries a WebKit build for every
platform it supports — tens of megabytes. An app that wants an app-shell should
not have to ship a browser engine it never opens, so the three types that would
drag it in live here and `mc-semantic-ui-javafx` stays lean.

## What it paints

**`app-shell`** — header on top, menu and page side by side beneath it, footer
at the bottom. The page sits in a slot registered under
`UiAppShell.contentId()`, so a patch can swap it while the header and menu stay
put; that is the desktop counterpart of the web shell's
`data-sui-slot="content"`. A `RIGHT`-sided menu is painted after the page, as on
the web. The header and menu are copied, not mutated, before the shell points
them at each other — the caller may be holding them for the next page.

**`header`** — hamburger, brand, then extras and user pushed to the trailing
edge. The hamburger names its menu by id and resolves it on each press rather
than at build time, so it works even though the header is painted before the
menu exists.

**`iframe`** — a `WebView`. `height` takes pixels; a viewport-relative length
like `60vh` leaves the view uncapped to fill what its parent column has left.

## Two things the desktop cannot follow the browser on

**`sandbox` is not a security boundary here.** It is a list of permissions the
HTML spec defines for an `<iframe>`, and a `WebView` implements none of that
vocabulary. The renderer honours the one distinction it can actually enforce —
a `sandbox` without `allow-scripts` turns JavaScript off — and can do nothing
about the rest. Point a `UiIFrame` at content you trust.

**`UiHeader.ExtrasOverflow.MENU` is not implemented.** The extras row wraps
instead of collapsing into a dropdown, the same call `SectionRenderer` makes
about tab overflow.
