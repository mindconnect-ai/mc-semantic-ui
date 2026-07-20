---
title: JavaFX desktop client
sidebar_position: 1
---

# JavaFX desktop client

The `UiNode` tree you already describe for the browser also renders as a
**native desktop application**. Same model, same triggers, same vocabulary —
a different painter.

This is not a web view in a window. `UiTable` becomes a real JavaFX `TableView`,
`UiField` a real `TextField`, `ComboBox` or `DatePicker`. Nothing about the model
was web-shaped to begin with; the browser was simply the first renderer.

![The demo's Orders tab: a UiTable with row actions, a menu button and a progress bar](/img/semantic-ui/javafx/orders-table.png)

*A `UiTable` with sortable columns, per-row actions, a `UiMenuButton` and a
`UiProgress` — all painted from the same tree the browser would get.*

```xml
<dependency>
  <groupId>ai.mindconnect</groupId>
  <artifactId>mc-semantic-ui-javafx</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## A complete small app

Everything below is one file. The tree in `ui()` is ordinary `UiNode` data —
hand the very same object to `SuiServerRenderer` and you get HTML instead.

```java
public class MiniApp extends Application {

    // The overlay is the host ("the DOM"); the renderer paints into it; the bus
    // drives the renderer — the same split as the browser's renderer + event bus.
    private final SuiFxOverlay   overlay  = new SuiFxOverlay();
    private final SuiFxRenderer  renderer = new SuiFxRenderer().attach(overlay);
    private final SuiFxEventBus  bus      = new SuiFxEventBus(renderer);

    /** Plain data. Nothing here is JavaFX-specific. */
    private UiNode ui() {
        return UiStack.of(
                UiText.of("Every field below ends up in one submit."),
                UiForm.of("customer-form", "Customer details")
                        .field(UiField.text("name", "Name", "Ada Lovelace")
                                .asEditable().asRequired())
                        .field(UiField.text("email", "E-mail", "ada@example.com")
                                .asEditable())
                        .action(UiAction.primary("save", "Save").onClick(
                                UiTrigger.invoke("saveCustomer", "customer-form"))));
    }

    @Override
    public void start(Stage stage) {
        // A handler is plain Java. It runs off the FX thread by default, so a
        // slow save never freezes the window.
        bus.registerClientHandler("saveCustomer", ctx ->
                bus.toast(UiToast.success("Saved " + ctx.string("name"))));

        renderer.mount(ui());

        // The overlay loads sui-fx.css itself, and toasts already land on it —
        // no stylesheet wiring, no setOverlay call.
        stage.setTitle("Semantic UI — JavaFX");
        stage.setScene(new Scene(overlay, 520, 360));
        stage.show();
    }
}
```

:::note Launch it from a separate class
Start the app from a `main` in a class that does **not** extend `Application`:

```java
public final class MiniLauncher {
    public static void main(String[] args) { Application.launch(MiniApp.class, args); }
}
```

A main class that extends `Application` needs `javafx.graphics` as a *named
module* and fails on the classpath with *"JavaFX runtime components are
missing"*. The separate launcher avoids that entirely.
:::

`ctx.string("name")` reads from the form payload. The bus collects it the way
the browser does: every named field inside the enclosing `UiForm`, across
arbitrary nesting — and across unselected tabs, because tab panels are painted
eagerly.

## Renderer, bus and overlay

The same split as the browser. `SuiFxRenderer` is the desktop counterpart of
`SuiRenderer`, `SuiFxEventBus` of `SuiEventBus`.

| | |
|---|---|
| `renderer.attach(overlay)` | binds the renderer to its host surface |
| `renderer.mount(UiNode)` | paints a tree into the host, returns the `Node` |
| `renderer.applyPatch(UiPatch)` | applies `REPLACE`/`APPEND`/`CLEAR`/`REMOVE` to the live scene |
| `new SuiFxEventBus(renderer)` | the bus that drives the renderer |
| `bus.registerClientHandler(name, handler)` | a local Java handler for `INVOKE` |
| `bus.toast(UiToast)` / `bus.showDialog(UiDialog)` | feedback and modals |

The renderer owns mounting and patching (as in the browser); the bus owns
triggers, handlers and feedback. `bus.applyPatch` also exists — it applies the
node ops through the renderer and shows any toasts the patch carries.

Handlers run on a background thread by default; the bus hops back to the FX
thread on its own when it applies the result. Pass `FxHandlerThread.FX` for the
rare handler that must run on the UI thread.

Busy state is shown at three levels: the clicked control, a global scrim
(delayed 250 ms, so fast handlers never make it flash), and the declarative
`UiAction.loading` flag.

## The server can drive it, too

Because triggers and `UiPatch` are the same objects the browser uses, an
endpoint that already answers a browser can answer the desktop client without
any change — and without a handler on the client at all:

```java
// No registerClientHandler(...) anywhere: APPLY_RESPONSE does the work.
UiTrigger.api("GET", "/api/inventory")
```

If that endpoint returns a `UiPatch` whose operation targets `inventory-panel`,
the client replaces exactly that panel. The demo does this over a real socket.

## What is supported

18 node types render today:

| | |
|---|---|
| Layout | `UiStack`, `UiSection` (tabs), `UiFieldGroup` |
| Data | `UiTable` (sorting, row actions, pagination), `UiTree`, `UiDetail`, `UiList` |
| Input | `UiForm`, `UiField` (text, textarea, number, boolean, date, select, multiselect, file), `UiUpload` |
| Action | `UiAction`, `UiLink`, `UiMenu`, `UiMenuButton` |
| Feedback | `UiText`, `UiDialog`, `UiSpinner`, `UiProgress`, toasts |

Anything else paints a visible placeholder instead of throwing, so an unknown
node degrades rather than taking the window down. Not painted yet: `UiAppShell`,
`UiHeader`, `UiIcon`, `UiPage`.

{/* Markdown image syntax inside the columns on purpose: a raw <img src="/img/…">
    would keep that literal path and miss the site's baseUrl. */}

<div class="row margin-bottom--md">
<div class="col col--6">

![A UiForm with text, select, date, boolean and textarea fields](/img/semantic-ui/javafx/customer-form.png)

*A `UiForm`: every named control inside it lands in one payload, however deeply
nested.*

</div>
<div class="col col--6">

![Actions, links, spinners, progress bars and a dialog button](/img/semantic-ui/javafx/widgets.png)

*Actions, links, spinners and progress — the declarative kind, driven by the
model rather than by code.*

</div>
</div>

![Every field type, in a scroll pane](/img/semantic-ui/javafx/field-types.png)

*Every `UiField` type. Tab content sits in its own scroll pane, so a long form
behaves like a web page rather than being clipped.*

Six of the seven trigger behaviours work: `APPLY_RESPONSE`, `INVOKE`, `PATCH`,
`DOWNLOAD`, `OPEN_IN_TAB`, `UPLOAD`. `STREAM` is not implemented — it is
registered as a behaviour that throws, so you get a clear error rather than
silence, and can register your own.

## Styling

`sui-fx.css` is the JavaFX counterpart of `sui.css`. Every node carries a
`sui-<type>` style class, so the selectors read like the web ones:

```css
.sui-table { -fx-background-color: -sui-surface; }
.sui-action.is-loading { -fx-opacity: 0.6; }
```

## Run the demo

```bash
mvn -pl core/mc-semantic-ui-javafx javafx:run
```

Ten tabs covering every supported node, a long-running handler with live
progress, drag-and-drop upload, and an embedded HTTP server whose endpoints
answer with `UiPatch` JSON.

See the [module README](https://github.com/mindconnect-ai/mc-semantic-ui/tree/main/core/mc-semantic-ui-javafx)
for the current limitations.
