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

    private final SuiFxEventBus bus = new SuiFxEventBus();

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

        // The overlay turns toasts into cards and gives slow dispatches a busy
        // scrim. Without it, toasts fall back to modal alerts.
        var overlay = new SuiFxOverlay(new BorderPane(bus.mount(ui())));
        bus.setOverlay(overlay);

        var scene = new Scene(overlay, 520, 360);
        scene.getStylesheets().add(
                MiniApp.class.getResource("/sui-fx/sui-fx.css").toExternalForm());

        stage.setTitle("Semantic UI — JavaFX");
        stage.setScene(scene);
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

## The event bus

`SuiFxEventBus` is the desktop counterpart of the browser's `SuiEventBus`. It
owns the mounted tree, resolves triggers and applies patches.

| | |
|---|---|
| `mount(UiNode)` | paints a tree and returns the JavaFX `Node` |
| `registerClientHandler(name, handler)` | a local Java handler for `INVOKE` |
| `applyPatch(UiPatch)` | applies `REPLACE`/`APPEND`/`CLEAR`/`REMOVE` to the live scene |
| `toast(UiToast)` / `showDialog(UiDialog)` | feedback and modals |

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
