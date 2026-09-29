package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.javafx.renderers.FxDrawer;
import ai.mindconnect.ui.javafx.renderers.FxDrawerGroup;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiCustom;
import ai.mindconnect.ui.model.UiDrawer;
import ai.mindconnect.ui.model.UiDrawerGroup;
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
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
 * form's button bar, a drawer, a group of drawers, a plugin's own node type,
 * and the sizes of a RICHTEXT field.
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

    // ── UiDrawerGroup ─────────────────────────────────────────────────────

    /** A drawer that says RIGHT and wants a grip: in a group, the group has the say. */
    private static UiDrawer member(String id, String title) {
        return UiDrawer.of(id, title, UiStack.of(UiText.of(id + "-t", title + " content")))
                .edge(UiDrawer.Edge.RIGHT).resizable()
                .onStateChange(UiTrigger.api("POST", "/" + id + "/{state}"));
    }

    private static UiDrawerGroup dock(UiDrawerGroup.Arrange arrange, UiDrawer... drawers) {
        return UiDrawerGroup.of("dock", drawers)
                .edge(UiDrawer.Edge.BOTTOM).scope(UiDrawer.Scope.CONTAINER).arrange(arrange)
                .size("50%").resizable()
                .onActiveChange(UiTrigger.api("POST", "/dock/front/{id}"));
    }

    /** Paints {@code tree} into an 800×600 scene with the stylesheet, laid out. */
    private static StackPane sceneOf(SuiFxEventBus bus, UiNode tree) {
        return onFx(() -> {
            var painted = bus.renderer().mount(tree);
            var stack = new StackPane(painted);
            var scene = new Scene(stack, 800, 600);
            SuiFxStyles.install(scene);
            stack.applyCss();
            stack.layout();
            stack.layout();
            return stack;
        });
    }

    private static FxDrawerGroup groupIn(StackPane root) {
        return onFx(() -> root.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("sui-drawer-layer"))
                .flatMap(layer -> ((Pane) layer).getChildren().stream())
                .filter(n -> n instanceof FxDrawerGroup).map(n -> (FxDrawerGroup) n)
                .findFirst().orElse(null));
    }

    private static void relayout(StackPane root) {
        onFx(() -> { root.applyCss(); root.layout(); root.layout(); return null; });
    }

    private static Color pixel(StackPane root, int x, int y) {
        return onFx(() -> {
            WritableImage shot = root.getScene().snapshot(null);
            return shot.getPixelReader().getColor(x, y);
        });
    }

    /** Whether anything but {@code bg} is painted in the rectangle — a handle's text and frame are. */
    private static boolean painted(StackPane root, Color bg, int x, int y, int w, int h) {
        return onFx(() -> {
            var reader = root.getScene().snapshot(null).getPixelReader();
            for (int i = x; i < x + w; i += 2) {
                for (int j = y; j < y + h; j += 2) {
                    if (!reader.getColor(i, j).equals(bg)) return true;
                }
            }
            return false;
        });
    }

    @Test
    void aShareGroupDividesTheStripAndAMinimizedOneGivesItsRoom() {
        var bus = new SuiFxEventBus();
        var root = sceneOf(bus, UiStack.of(UiText.of("t", "content"),
                dock(UiDrawerGroup.Arrange.SHARE, member("chat", "Chat"), member("files", "Files"))));
        var group = groupIn(root);
        assertThat(group).as("an overlay group floats in the drawer layer").isNotNull();
        assertThat(group.arrange()).isEqualTo(UiDrawerGroup.Arrange.SHARE);
        var drawers = group.drawers();
        assertThat(drawers).hasSize(2);
        var chat = drawers.get(0);
        var files = drawers.get(1);

        // The group's edge, not the drawer's own; and no grip of its own.
        assertThat(chat.getStyleClass()).contains("sui-drawer-bottom", "sui-drawer-overlay").doesNotContain("sui-drawer-right", "sui-drawer-push");
        assertThat(onFx(() -> chat.getChildrenUnmodifiable().stream().anyMatch(n -> n.getStyleClass().contains("sui-drawer-resize")))).isFalse();
        assertThat(onFx(() -> group.getChildren().stream().anyMatch(n -> n.getStyleClass().contains("sui-drawer-resize")))).as("the strip's grip").isTrue();

        // Side by side, each half the width, the full height of the strip: 50% of 600 on the bottom edge.
        assertThat(onFx(group::getHeight)).isEqualTo(600.0);
        assertThat(onFx(chat::getLayoutY)).isEqualTo(300.0);
        assertThat(onFx(chat::getHeight)).isEqualTo(300.0);
        assertThat(onFx(chat::getLayoutX)).isEqualTo(0.0);
        assertThat(onFx(chat::getWidth)).isEqualTo(400.0);
        assertThat(onFx(files::getLayoutX)).isEqualTo(400.0);
        assertThat(onFx(files::getWidth)).isEqualTo(400.0);
        // And painted there: the header's surface-alt in the strip, the page above it.
        Color bg = pixel(root, 700, 100);
        assertThat(pixel(root, 200, 290)).as("above the strip").isEqualTo(bg);
        assertThat(pixel(root, 200, 312)).as("chat's header").isNotEqualTo(bg);
        assertThat(pixel(root, 600, 312)).as("files' header").isNotEqualTo(bg);

        onFx(() -> { files.setState(UiDrawer.State.MINIMIZED, true); return null; });
        relayout(root);
        assertThat(onFx(chat::getWidth)).as("the other takes its room").isEqualTo(800.0);
        assertThat(onFx(() -> files.getLayoutY() + files.getHeight())).as("its handle at the edge").isEqualTo(600.0);
        assertThat(onFx(files::getWidth)).isEqualTo(onFx(() -> files.handle().prefWidth(-1)));
        assertThat(onFx(files::getViewOrder)).as("a handle above the open panels").isLessThan(onFx(chat::getViewOrder));

        onFx(() -> { chat.setState(UiDrawer.State.MINIMIZED, true); return null; });
        relayout(root);
        assertThat(pixel(root, 200, 312)).as("nothing open: no strip").isEqualTo(bg);
        int hx = onFx(files::getLayoutX).intValue(), hy = onFx(files::getLayoutY).intValue();
        int hw = onFx(files::getWidth).intValue(), hh = onFx(files::getHeight).intValue();
        assertThat(painted(root, bg, hx, hy, hw, hh)).as("the handle is").isTrue();
        assertThat(onFx(files::getLayoutX)).as("handles line up from the far end, the second beside the first").isLessThan(onFx(chat::getLayoutX));
    }

    @Test
    void aStackShowsTheFrontDrawerAndTheBarsOfTheOthers() {
        var bus = new SuiFxEventBus();
        var sent = new ArrayList<String>();
        onFx(() -> bus.registerBehavior("APPLY_RESPONSE", ctx -> {
            sent.add(ctx.trigger().getMethod() + " " + ctx.trigger().getUrl());
            return CompletableFuture.completedFuture(null);
        }));
        var root = sceneOf(bus, UiStack.of(UiText.of("t", "content"),
                dock(UiDrawerGroup.Arrange.STACK, member("a", "A"), member("b", "B"), member("c", "C")).active("b")));
        var group = groupIn(root);
        var a = group.drawers().get(0);
        var b = group.drawers().get(1);
        var c = group.drawers().get(2);

        // The active one in front with its content, the others as bars above it.
        assertThat(group.front()).isSameAs(b);
        assertThat(a.isPeeking()).isTrue();
        assertThat(b.isPeeking()).isFalse();
        assertThat(c.isPeeking()).isTrue();
        double barA = onFx(a::getHeight), barC = onFx(c::getHeight);
        assertThat(barA).isGreaterThan(0).isLessThan(100);
        assertThat(onFx(a::getLayoutY)).isEqualTo(300.0);
        assertThat(onFx(c::getLayoutY)).isEqualTo(300.0 + barA);
        assertThat(onFx(b::getLayoutY)).isEqualTo(300.0 + barA + barC);
        assertThat(onFx(b::getHeight)).isEqualTo(300.0 - barA - barC);
        assertThat(onFx(() -> a.content().isVisible())).as("a bar hides its body").isFalse();
        assertThat(onFx(() -> b.content().isVisible())).isTrue();

        // Pressing a bar brings that drawer forward and tells the server.
        onFx(() -> group.front(a, true));
        relayout(root);
        assertThat(group.front()).isSameAs(a);
        assertThat(group.getActive()).isEqualTo("a");
        assertThat(b.isPeeking()).isTrue();
        assertThat(onFx(() -> a.content().isVisible())).isTrue();
        waitFor(() -> sent.size() == 1);
        assertThat(sent).containsExactly("POST /dock/front/a");

        // Minimizing the front one leaves the next open one in front — without a word.
        onFx(() -> { a.setState(UiDrawer.State.MINIMIZED, true); return null; });
        assertThat(group.front()).isSameAs(b);
        assertThat(b.isPeeking()).isFalse();
        waitFor(() -> sent.size() == 2);
        assertThat(sent.get(1)).isEqualTo("POST /a/MINIMIZED");

        // Opening from the handle puts it in front.
        onFx(() -> { a.setState(UiDrawer.State.OPEN, true); return null; });
        assertThat(group.front()).isSameAs(a);
        waitFor(() -> sent.size() == 4);
        assertThat(sent.subList(2, 4)).containsExactlyInAnyOrder("POST /a/OPEN", "POST /dock/front/a");

        // Not the user (a patch): who is in front settles, nothing is sent.
        onFx(() -> { c.setState(UiDrawer.State.MINIMIZED, false); c.setState(UiDrawer.State.OPEN, false); return null; });
        assertThat(group.front()).isSameAs(a);
        assertThat(c.isPeeking()).isTrue();
        assertThat(sent).hasSize(4);
    }

    @Test
    void aReplaceKeepsWhoIsInFrontAndPaintsAReplacedDrawerAsAnItemOfTheStrip() {
        var bus = new SuiFxEventBus();
        var root = sceneOf(bus, UiStack.of(UiText.of("t", "content"),
                dock(UiDrawerGroup.Arrange.STACK, member("a", "A"), member("b", "B"))));
        var group = groupIn(root);
        onFx(() -> { group.front(group.drawers().get(1), true); group.drawers().get(0).setState(UiDrawer.State.MINIMIZED, true); return null; });

        // The group replaced without naming a front: the user's choice stands, and so do the states.
        onFx(() -> { bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("dock",
                dock(UiDrawerGroup.Arrange.STACK, member("a", "A"), member("b", "B"))))); return null; });
        waitFor(() -> FxDrawerGroup.of(bus.context().byId("dock")) != group);
        var again = onFx(() -> FxDrawerGroup.of(bus.context().byId("dock")));
        assertThat(again.getActive()).isEqualTo("b");
        assertThat(again.drawers().get(0).getState()).isEqualTo(UiDrawer.State.MINIMIZED);
        assertThat(again.drawers().get(1).getState()).isEqualTo(UiDrawer.State.OPEN);

        // Named, it wins.
        onFx(() -> { bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("dock",
                dock(UiDrawerGroup.Arrange.STACK, member("a", "A").open(), member("b", "B")).active("a")))); return null; });
        var named = onFx(() -> FxDrawerGroup.of(bus.context().byId("dock")));
        assertThat(named.getActive()).isEqualTo("a");
        assertThat(named.front().id()).isEqualTo("a");

        // One drawer replaced, on its own terms (RIGHT, overlay): painted again as one of the strip.
        onFx(() -> { bus.applyPatch(UiPatch.of().patch(UiPatch.Operation.replace("b",
                UiDrawer.of("b", "B2", UiText.of("b-t2", "new"))))); return null; });
        waitFor(() -> {
            var d = FxDrawer.of(bus.context().byId("b"));
            return d != null && d.group() == named && d.getParent() == named;
        });
        var b2 = onFx(() -> FxDrawer.of(bus.context().byId("b")));
        assertThat(b2.getStyleClass()).contains("sui-drawer-bottom").doesNotContain("sui-drawer-right");
        assertThat(named.drawers()).hasSize(2);
        assertThat(named.drawers().get(1)).isSameAs(b2);
        assertThat(b2.isPeeking()).as("behind a, which is in front").isTrue();
    }

    @Test
    void aPushGroupTakesTheStripsRoomAndOnlyItsHandlesWhenNothingIsOpen() {
        var bus = new SuiFxEventBus();
        var root = sceneOf(bus, UiStack.of(UiText.of("t", "content"),
                dock(UiDrawerGroup.Arrange.SHARE, member("chat", "Chat"), member("files", "Files").minimized())
                        .mode(UiDrawer.Mode.PUSH)));
        var group = onFx(() -> find(root, FxDrawerGroup.class));
        assertThat(group).isNotNull();
        assertThat(onFx(() -> group.getParent() instanceof VBox)).as("in the layout, not in the layer").isTrue();
        var chat = group.drawers().get(0);
        var files = group.drawers().get(1);
        double column = onFx(() -> ((Pane) group.getParent()).getHeight());
        assertThat(onFx(group::getHeight)).isEqualTo(column / 2);
        // The handle stands in the row at its own size; the open one has the rest.
        double hw = onFx(() -> files.handle().prefWidth(-1));
        assertThat(onFx(files::getWidth)).isEqualTo(hw);
        assertThat(onFx(chat::getWidth)).isEqualTo(800.0 - hw);
        assertThat(files.getStyleClass()).contains("sui-drawer-push");

        onFx(() -> { chat.setState(UiDrawer.State.MINIMIZED, true); return null; });
        relayout(root);
        double hh = onFx(() -> files.handle().prefHeight(-1));
        assertThat(onFx(group::getHeight)).as("nothing open: the strip is its handles").isEqualTo(hh);
        assertThat(onFx(chat::getLayoutX)).isEqualTo(0.0);
        assertThat(onFx(files::getLayoutX)).isEqualTo(onFx(chat::getWidth));
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
