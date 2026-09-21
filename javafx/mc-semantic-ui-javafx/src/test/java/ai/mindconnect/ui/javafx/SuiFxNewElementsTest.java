package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.javafx.renderers.FxDrawer;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiCustom;
import ai.mindconnect.ui.model.UiDrawer;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The elements the web renderers gained, painted on the desktop: a menu in a
 * form's button bar, a drawer, a plugin's own node type, and the sizes of a
 * RICHTEXT field.
 */
class SuiFxNewElementsTest {

    @BeforeAll
    static void startToolkit() {
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another test class got there first — fine.
        } catch (Throwable noDisplay) {
            assumeTrue(false, "No JavaFX toolkit available here: " + noDisplay);
        }
    }

    // ── UiAction.menu ─────────────────────────────────────────────────────

    private static UiForm composer() {
        return UiForm.of("compose", "New mail")
                .field(UiField.text("subject", "Subject", "Hello").asEditable())
                .action(UiAction.primary("send", "Send").onClick(UiTrigger.api("POST", "/mail/send", "compose")))
                .action(UiAction.menu("ai", "AI",
                                UiMenuItem.of("draft", "Draft with AI").onClick(UiTrigger.api("POST", "/ai/draft", "compose")),
                                UiMenuItem.divider(),
                                UiMenuItem.heading("Quick actions"),
                                UiMenuItem.of("shorten", "Shorten").onClick(UiTrigger.api("POST", "/ai/shorten")),
                                UiMenuItem.of("translate", "Translate").disabled("No service"))
                        .icon("sparkles"));
    }

    @Test
    void aMenuInTheButtonBarIsAMenuButtonLookingLikeItsNeighbours() {
        var bus = new SuiFxEventBus();
        var painted = onFx(() -> bus.renderer().mount(composer()));
        var menu = onFx(() -> find(painted, MenuButton.class));
        assertThat(menu).isNotNull();
        assertThat(menu.getText()).isEqualTo("AI");
        assertThat(menu.getStyleClass()).contains("sui-action-secondary", "sui-action-menu");
        List<MenuItem> items = menu.getItems();
        assertThat(items).hasSize(5);
        assertThat(items.get(1)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(items.get(2).getText()).isEqualTo("Quick actions");
        assertThat(items.get(2).isDisable()).as("a heading is not an entry").isTrue();
        assertThat(items.get(2).getStyleClass()).contains("sui-menu-heading");
        assertThat(items.get(4).isDisable()).as("a disabled entry").isTrue();
    }

    @Test
    void anEntrySendsTheFormNamedOrNot() {
        var bus = new SuiFxEventBus();
        var sent = new ArrayList<String>();
        var payloads = new ArrayList<Map<String, Object>>();
        onFx(() -> bus.registerBehavior("APPLY_RESPONSE", ctx -> {
            sent.add(ctx.trigger().getUrl());
            payloads.add(ctx.payload());
            return CompletableFuture.completedFuture(null);
        }));
        var painted = onFx(() -> bus.renderer().mount(composer()));
        onFx(() -> {
            var items = find(painted, MenuButton.class).getItems();
            items.get(0).fire();   // names the form
            items.get(3).fire();   // names nothing: the form it is in
            return null;
        });
        waitFor(() -> sent.size() == 2);
        assertThat(sent).containsExactly("/ai/draft", "/ai/shorten");
        assertThat(payloads).allSatisfy(p -> assertThat(p).containsEntry("subject", "Hello"));
    }

    // ── UiCustom ──────────────────────────────────────────────────────────

    @Test
    void aPluginsNodeTypePaintsWithItsRendererOrAsAPlaceholder() {
        var bus = new SuiFxEventBus();
        Node missing = onFx(() -> bus.renderer().mount(UiCustom.of("w", "chat-widget").prop("session", "s1")));
        assertThat(((Label) missing).getText()).contains("chat-widget");

        onFx(() -> bus.renderer().registerCustom("chat-widget",
                (node, ctx) -> new Label("chat " + node.prop("session"))));
        Node painted = onFx(() -> bus.renderer().mount(UiCustom.of("w", "chat-widget").prop("session", "s1")));
        assertThat(((Label) painted).getText()).isEqualTo("chat s1");
    }

    // ── UiDrawer ──────────────────────────────────────────────────────────

    private static UiDrawer chat(UiDrawer.Mode mode) {
        return UiDrawer.of("chat", "Draft with AI", UiStack.of(UiField.text("ask", "Ask", null).asEditable()))
                .edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER).mode(mode).size("45%").closable()
                .onStateChange(UiTrigger.api("POST", "/chat/state/{state}"))
                .onClose(UiTrigger.api("DELETE", "/chat"));
    }

    @Test
    void minimizingAndOpeningAgainKeepsTheVerySameContent() {
        var bus = new SuiFxEventBus();
        var painted = onFx(() -> bus.renderer().mount(UiForm.of("f", "F").content(chat(UiDrawer.Mode.PUSH))));
        var drawer = onFx(() -> find(painted, FxDrawer.class));
        var content = drawer.content();
        onFx(() -> {
            find(content, TextField.class).setText("make it friendlier");
            drawer.setState(UiDrawer.State.MINIMIZED, true);
            return null;
        });
        assertThat(drawer.getState()).isEqualTo(UiDrawer.State.MINIMIZED);
        assertThat(drawer.handle().isVisible()).isTrue();
        onFx(() -> { drawer.setState(UiDrawer.State.OPEN, true); return null; });
        assertThat(drawer.content()).as("not rendered again").isSameAs(content);
        assertThat(onFx(() -> find(drawer.content(), TextField.class).getText())).isEqualTo("make it friendlier");
    }

    @Test
    void theUserChangesReachTheServerWithTheStateInTheUrl() {
        var bus = new SuiFxEventBus();
        var sent = new ArrayList<String>();
        onFx(() -> bus.registerBehavior("APPLY_RESPONSE", ctx -> {
            sent.add(ctx.trigger().getMethod() + " " + ctx.trigger().getUrl());
            return CompletableFuture.completedFuture(null);
        }));
        var painted = onFx(() -> bus.renderer().mount(UiStack.of(chat(UiDrawer.Mode.PUSH))));
        var drawer = onFx(() -> find(painted, FxDrawer.class));
        onFx(() -> {
            drawer.setState(UiDrawer.State.MINIMIZED, true);
            drawer.setState(UiDrawer.State.OPEN, true);
            drawer.setState(UiDrawer.State.CLOSED, true);
            drawer.setState(UiDrawer.State.OPEN, false);   // not the user: nothing sent
            return null;
        });
        waitFor(() -> sent.size() == 4);
        assertThat(sent).containsExactly("POST /chat/state/MINIMIZED", "POST /chat/state/OPEN",
                "POST /chat/state/CLOSED", "DELETE /chat");
    }

    @Test
    void aReplaceKeepsTheStateTheUserChoseUnlessThePatchSetsOne() {
        var bus = new SuiFxEventBus();
        var painted = onFx(() -> bus.renderer().mount(UiStack.of(chat(UiDrawer.Mode.PUSH))));
        onFx(() -> { find(painted, FxDrawer.class).setState(UiDrawer.State.MINIMIZED, true); return null; });

        onFx(() -> { bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("chat", chat(UiDrawer.Mode.PUSH)))); return null; });
        waitFor(() -> FxDrawer.of(bus.context().byId("chat")) != null);
        assertThat(onFx(() -> FxDrawer.of(bus.context().byId("chat")).getState())).isEqualTo(UiDrawer.State.MINIMIZED);

        onFx(() -> { bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("chat", chat(UiDrawer.Mode.PUSH).open()))); return null; });
        assertThat(onFx(() -> FxDrawer.of(bus.context().byId("chat")).getState())).isEqualTo(UiDrawer.State.OPEN);
    }

    @Test
    void anOverlayDrawerLiesOnTheEdgeOfItsContainerAndHandlesLineUp() {
        var bus = new SuiFxEventBus();
        var tree = UiStack.of(UiText.of("t", "content"),
                chat(UiDrawer.Mode.OVERLAY),
                UiDrawer.of("log", "Log", UiText.of("l", "…")).edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER).minimized(),
                UiDrawer.of("console", "Console", UiText.of("c", "…")).edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER).minimized());
        var root = onFx(() -> {
            var painted = bus.renderer().mount(tree);
            var stack = new StackPane(painted);
            new Scene(stack, 800, 600);
            stack.applyCss();
            stack.layout();
            stack.layout();
            return stack;
        });
        Pane layer = onFx(() -> (Pane) root.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("sui-drawer-layer")).findFirst().orElse(null));
        assertThat(layer).as("overlay drawers float in a layer").isNotNull();
        var drawers = onFx(() -> layer.getChildren().stream().map(n -> (FxDrawer) n).toList());
        assertThat(drawers).hasSize(3);

        var open = drawers.get(0);
        var panel = onFx(() -> (Parent) open.getChildrenUnmodifiable().get(0));
        assertThat(onFx(open::getHeight)).isEqualTo(600.0);
        assertThat(onFx(panel::getLayoutY)).isEqualTo(600.0 - 270.0);   // 45% of 600, on the bottom edge
        assertThat(onFx(() -> panel.getLayoutBounds().getHeight())).isEqualTo(270.0);

        double logX = onFx(() -> drawers.get(1).handle().getLayoutX());
        double consoleX = onFx(() -> drawers.get(2).handle().getLayoutX());
        assertThat(consoleX).as("side by side, not on top of each other").isLessThan(logX);
    }

    // ── RICHTEXT ──────────────────────────────────────────────────────────

    @Test
    void richTextTakesItsEditorHeightAndShowsReadOnlyAsText() {
        var bus = new SuiFxEventBus();
        var painted = onFx(() -> bus.renderer().mount(UiForm.of("f", "F")
                .field(UiField.richtext("body", "Body", "<p>Hi <b>there</b></p>").asEditable().editorHeight("320px"))
                .field(UiField.richtext("ro", "Read", "<p>One</p><ul><li>two</li></ul><script>x()</script>"))));
        var area = onFx(() -> find(painted, TextArea.class));
        assertThat(area.getPrefHeight()).isEqualTo(320.0);
        var labels = onFx(() -> findAll(painted, Label.class).stream().map(Label::getText).toList());
        assertThat(labels).anySatisfy(t -> assertThat(t).isEqualTo("One\n• two"));
        assertThat(labels).noneSatisfy(t -> assertThat(t).contains("<"));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static <T> T find(Node node, Class<T> type) {
        var all = findAll(node, type);
        return all.isEmpty() ? null : all.get(0);
    }

    private static <T> List<T> findAll(Node node, Class<T> type) {
        var out = new ArrayList<T>();
        collect(node, type, out);
        return out;
    }

    private static <T> void collect(Node node, Class<T> type, List<T> out) {
        if (node == null) return;
        if (type.isInstance(node)) out.add(type.cast(node));
        if (node instanceof javafx.scene.control.ScrollPane sp) collect(sp.getContent(), type, out);
        if (node instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, type, out);
    }

    private static void waitFor(Supplier<Boolean> condition) {
        long until = System.currentTimeMillis() + 5000;
        while (!onFx(condition)) {
            if (System.currentTimeMillis() > until) throw new AssertionError("condition never held");
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    private static <T> T onFx(Supplier<T> work) {
        var result = new AtomicReference<T>();
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { result.set(work.get()); } catch (Throwable t) { error.set(t); } finally { latch.countDown(); }
        });
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("FX thread timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }

    @SuppressWarnings("unused")
    private static UiNode unused() { return null; }
}
