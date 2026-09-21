package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.model.UiDrawer;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The painted drawer: a handle for when it is minimized and a panel (header
 * with minimize and close, the content, a resize grip) for when it is open.
 *
 * <p>The content is rendered once, when the drawer is. Opening, minimizing
 * and closing only show and hide the parts — {@link #setState} — so whatever
 * the content holds (typed text, a widget's state) is still there.
 */
public final class FxDrawer extends Region {

    /** Where a drawer's node keeps its drawer, for {@link #of}. */
    private static final String KEY = "sui-fx-drawer";
    private static final String LAYER_CLASS = "sui-drawer-layer";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern LENGTH = Pattern.compile("(\\d+(?:\\.\\d+)?)(px|rem|em|vh|vw|dvh|svh|lvh|%)");

    private final UiDrawer model;
    private final FxRenderContext ctx;
    private final UiDrawer.Edge edge;
    private final Button handle = new Button();
    private final VBox panel = new VBox();
    private final Region grip = new Region();
    private final Region anchor = new Region();
    private UiDrawer.State state;
    /** A size the user dragged to, in pixels; overrides {@code size}. */
    private Double dragged;
    /** Along its edge, where a minimized handle sits: set by the layer, so handles line up. */
    private double handleOffset;

    FxDrawer(UiDrawer model, FxRenderContext ctx, UiDrawer.State kept) {
        this.model = model;
        this.ctx = ctx;
        this.edge = model.getEdge() != null ? model.getEdge() : UiDrawer.Edge.RIGHT;
        this.state = model.getState() != null ? model.getState() : kept != null ? kept : UiDrawer.State.OPEN;
        getStyleClass().addAll("sui-drawer", "sui-drawer-" + edge.name().toLowerCase(),
                model.getMode() == UiDrawer.Mode.PUSH ? "sui-drawer-push" : "sui-drawer-overlay");
        getProperties().put(KEY, this);
        anchor.getProperties().put(KEY, this);
        anchor.setManaged(false);
        anchor.setVisible(false);
        // Clicks on the empty rest of an overlay's area reach what lies below.
        setPickOnBounds(false);

        buildHandle();
        buildPanel();
        getChildren().addAll(panel, handle);
        if (model.isResizable()) buildGrip();
        applyState(false);

        anchor.sceneProperty().addListener((o, was, now) -> {
            if (was != null) Layer.remove(this);
            if (now != null) Layer.add(this, now);
        });
    }

    /** The drawer behind a node this renderer painted — the drawer itself or its anchor — or null. */
    public static FxDrawer of(Node node) {
        return node == null ? null : (FxDrawer) node.getProperties().get(KEY);
    }

    /** What stands in the layout for an overlay drawer: invisible, taking no room. */
    Node anchor() {
        return anchor;
    }

    public UiDrawer.State getState() {
        return state;
    }

    /** The painted content — the same node for the drawer's whole life. */
    public Node content() {
        return panel.getChildren().size() > 1 ? panel.getChildren().get(1) : null;
    }

    public Button handle() {
        return handle;
    }

    public UiDrawer model() {
        return model;
    }

    /**
     * Moves the drawer to {@code next} — parts shown and hidden, nothing
     * rendered — and, when it was the user, tells the server: onStateChange
     * with {state} filled in, and onClose for a close.
     */
    public void setState(UiDrawer.State next, boolean byUser) {
        if (next == state) return;
        state = next;
        applyState(true);
        if (!byUser) return;
        fire(model.getOnStateChange(), next);
        if (next == UiDrawer.State.CLOSED) fire(model.getOnClose(), next);
    }

    private void fire(UiTrigger trigger, UiDrawer.State next) {
        if (trigger == null || ctx.bus() == null) return;
        UiTrigger copy = MAPPER.convertValue(trigger, UiTrigger.class);
        if (copy.getUrl() != null) copy.setUrl(copy.getUrl().replace("{state}", next.name()));
        ctx.bus().dispatch(copy, model, ctx);
    }

    // ── parts ─────────────────────────────────────────────────────────────

    private void buildHandle() {
        handle.setText(SuiFxText.first(model.getTitle(), "Open"));
        handle.getStyleClass().add("sui-drawer-handle");
        Icons.lead(handle, model.getIcon(), ctx);
        if (model.getBadge() != null) {
            // Icon, title, badge — in that order, as on the web: the title
            // moves into the graphic so the badge can follow it.
            var title = new Label(handle.getText());
            title.getStyleClass().add("sui-drawer-handle-title");
            var badge = new Label(model.getBadge());
            badge.getStyleClass().add("sui-drawer-badge");
            var row = new HBox(6);
            if (handle.getGraphic() != null) row.getChildren().add(handle.getGraphic());
            row.getChildren().addAll(title, badge);
            row.setAlignment(Pos.CENTER_LEFT);
            handle.setText(null);
            handle.setGraphic(row);
            handle.setAccessibleText(model.getTitle());
        }
        handle.setOnAction(e -> {
            setState(UiDrawer.State.OPEN, true);
            focusInside();
        });
    }

    private void buildPanel() {
        panel.getStyleClass().add("sui-drawer-panel");
        panel.setFillWidth(true);

        var title = new Label(SuiFxText.first(model.getTitle(), ""));
        title.getStyleClass().add("sui-drawer-title");
        Icons.lead(title, model.getIcon(), ctx);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        var minimize = button("—", "Minimize", "minus");
        minimize.setOnAction(e -> {
            setState(UiDrawer.State.MINIMIZED, true);
            handle.requestFocus();
        });
        var header = new HBox(6, title, minimize);
        if (model.isClosable()) {
            var close = button("×", "Close", "x");
            close.setOnAction(e -> setState(UiDrawer.State.CLOSED, true));
            header.getChildren().add(close);
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 6, 6, 12));
        header.getStyleClass().add("sui-drawer-header");

        Node body = model.getContent() != null ? ctx.render(model.getContent()) : new Region();
        var scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sui-drawer-body");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(header, scroll);
        // Escape inside minimizes — it does not close.
        panel.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ESCAPE || state != UiDrawer.State.OPEN) return;
            e.consume();
            setState(UiDrawer.State.MINIMIZED, true);
            handle.requestFocus();
        });
    }

    private Button button(String fallback, String label, String icon) {
        var b = new Button();
        b.getStyleClass().add("sui-drawer-btn");
        Icons.lead(b, icon, ctx);
        if (b.getGraphic() == null) b.setText(fallback);
        b.setTooltip(new Tooltip(label));
        b.setAccessibleText(label);
        return b;
    }

    private void buildGrip() {
        grip.getStyleClass().add("sui-drawer-resize");
        boolean vertical = edge == UiDrawer.Edge.TOP || edge == UiDrawer.Edge.BOTTOM;
        grip.setCursor(vertical ? Cursor.V_RESIZE : Cursor.H_RESIZE);
        grip.setOnMouseDragged(e -> {
            Bounds area = area();
            if (area == null) return;
            var p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double px = switch (edge) {
                case BOTTOM -> getHeight() - p.getY();
                case TOP -> p.getY();
                case LEFT -> p.getX();
                case RIGHT -> getWidth() - p.getX();
            };
            dragged = Math.max(0, px);
            requestLayout();
            Layer.relayout(this);
        });
        getChildren().add(grip);
    }

    private void applyState(boolean animate) {
        boolean open = state == UiDrawer.State.OPEN;
        boolean closed = state == UiDrawer.State.CLOSED;
        panel.setVisible(open);
        panel.setManaged(open);
        handle.setVisible(state == UiDrawer.State.MINIMIZED);
        handle.setManaged(state == UiDrawer.State.MINIMIZED);
        grip.setVisible(open);
        setVisible(!closed);
        setManaged(!closed);
        getStyleClass().removeAll("sui-drawer-open", "sui-drawer-minimized", "sui-drawer-closed");
        getStyleClass().add("sui-drawer-" + state.name().toLowerCase());
        if (open && animate) slideIn();
        requestLayout();
        Layer.relayout(this);
    }

    /** Slides the panel in from its edge; a JavaFX app has no reduced-motion query, so it is short. */
    private void slideIn() {
        var t = new TranslateTransition(Duration.millis(180), panel);
        switch (edge) {
            case BOTTOM -> t.setFromY(40);
            case TOP -> t.setFromY(-40);
            case LEFT -> t.setFromX(-40);
            case RIGHT -> t.setFromX(40);
        }
        t.setToX(0);
        t.setToY(0);
        t.play();
    }

    /** Into the drawer: its first control a user can use, else the panel. */
    private void focusInside() {
        Node first = firstFocusable(panel.getChildren().size() > 1 ? panel.getChildren().get(1) : null);
        if (first != null) first.requestFocus(); else panel.requestFocus();
    }

    private static Node firstFocusable(Node node) {
        if (node == null || !node.isVisible() || node.isDisabled()) return null;
        if (node instanceof ScrollPane sp) return firstFocusable(sp.getContent());
        if (node instanceof Control c && c.isFocusTraversable() && !(node instanceof ScrollPane)) return c;
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                Node hit = firstFocusable(child);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    // ── size and layout ───────────────────────────────────────────────────

    /** The size along the edge's axis, in pixels, for an area of {@code extent}. */
    double size(double extent) {
        Scene scene = getScene() != null ? getScene() : anchor.getScene();
        double sw = scene != null ? scene.getWidth() : extent, sh = scene != null ? scene.getHeight() : extent;
        double size = dragged != null ? dragged : px(model.getSize(), extent, sw, sh, extent * 0.4);
        double min = px(model.getMinSize(), extent, sw, sh, 0);
        double max = px(model.getMaxSize(), extent, sw, sh, extent);
        return Math.max(min, Math.min(max, Math.min(size, extent)));
    }

    /** A CSS length in pixels: % of the area, vh/vw of the window, em/rem at 16px. */
    static double px(String length, double extent, double sceneWidth, double sceneHeight, double fallback) {
        if (length == null) return fallback;
        Matcher m = LENGTH.matcher(length.trim());
        if (!m.matches()) return fallback;
        double v = Double.parseDouble(m.group(1));
        return switch (m.group(2)) {
            case "%" -> extent * v / 100;
            case "vh", "dvh", "svh", "lvh" -> sceneHeight * v / 100;
            case "vw" -> sceneWidth * v / 100;
            case "em", "rem" -> v * 16;
            default -> v;
        };
    }

    private boolean alongY() {
        return edge == UiDrawer.Edge.TOP || edge == UiDrawer.Edge.BOTTOM;
    }

    @Override
    protected double computePrefWidth(double height) {
        if (model.getMode() != UiDrawer.Mode.PUSH || alongY()) return super.computePrefWidth(height);
        if (state != UiDrawer.State.OPEN) return handle.prefWidth(-1);
        double extent = getParent() instanceof Region r && r.getWidth() > 0 ? r.getWidth() : 800;
        return size(extent);
    }

    @Override
    protected double computePrefHeight(double width) {
        if (model.getMode() != UiDrawer.Mode.PUSH || !alongY()) return super.computePrefHeight(width);
        if (state != UiDrawer.State.OPEN) return handle.prefHeight(-1);
        double extent = getParent() instanceof Region r && r.getHeight() > 0 ? r.getHeight() : 600;
        return size(extent);
    }

    @Override
    protected double computeMinWidth(double height) {
        return model.getMode() == UiDrawer.Mode.PUSH && !alongY() ? computePrefWidth(height) : 0;
    }

    @Override
    protected double computeMinHeight(double width) {
        return model.getMode() == UiDrawer.Mode.PUSH && alongY() ? computePrefHeight(width) : 0;
    }

    /**
     * PUSH: the panel fills the drawer, a minimized one is its handle.
     * OVERLAY: the drawer spans its whole area (window or container) and lets
     * clicks through; the panel sits on the edge with its size, the handle at
     * the edge beside the handles before it.
     */
    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        double gripThickness = 6;
        if (model.getMode() == UiDrawer.Mode.PUSH) {
            panel.resizeRelocate(0, 0, w, h);
            handle.autosize();
            handle.relocate(0, 0);
            placeGrip(0, 0, w, h, gripThickness);
            return;
        }
        double s = size(alongY() ? h : w);
        double px = 0, py = 0, pw = w, ph = h;
        switch (edge) {
            case BOTTOM -> { py = h - s; ph = s; }
            case TOP -> ph = s;
            case LEFT -> pw = s;
            case RIGHT -> { px = w - s; pw = s; }
        }
        panel.resizeRelocate(px, py, pw, ph);
        placeGrip(px, py, pw, ph, gripThickness);

        handle.autosize();
        double hw = handle.getWidth(), hh = handle.getHeight(), gap = 16;
        switch (edge) {
            case BOTTOM -> handle.relocate(w - gap - handleOffset - hw, h - hh);
            case TOP -> handle.relocate(w - gap - handleOffset - hw, 0);
            case LEFT -> handle.relocate(0, gap + handleOffset);
            case RIGHT -> handle.relocate(w - hw, gap + handleOffset);
        }
    }

    private void placeGrip(double x, double y, double w, double h, double t) {
        if (!getChildren().contains(grip)) return;
        switch (edge) {
            case BOTTOM -> grip.resizeRelocate(x, y - t / 2, w, t);
            case TOP -> grip.resizeRelocate(x, y + h - t / 2, w, t);
            case LEFT -> grip.resizeRelocate(x + w - t / 2, y, t, h);
            case RIGHT -> grip.resizeRelocate(x - t / 2, y, t, h);
        }
    }

    /** The area an overlay drawer lies over, in its layer's coordinates. */
    Bounds area() {
        if (!(getParent() instanceof Pane layer)) return null;
        if (model.getScope() != UiDrawer.Scope.CONTAINER || anchor.getParent() == null) {
            return layer.getLayoutBounds();
        }
        Parent container = anchor.getParent();
        Bounds inScene = container.localToScene(container.getLayoutBounds());
        return inScene == null ? null : layer.sceneToLocal(inScene);
    }

    // ── the layer overlay drawers float in ───────────────────────────────

    /**
     * One per scene: a pane over the scene's content (under its toasts), in
     * which the overlay drawers are laid on their areas after every layout
     * pass. A scene whose root is not a {@link StackPane} — a dialog window's
     * — gets one around its root the first time a drawer needs it.
     */
    static final class Layer {

        private Layer() {
        }

        static void add(FxDrawer drawer, Scene scene) {
            Pane layer = layerOf(scene);
            if (!layer.getChildren().contains(drawer)) layer.getChildren().add(drawer);
            relayout(drawer);
        }

        static void remove(FxDrawer drawer) {
            if (drawer.getParent() instanceof Pane layer) layer.getChildren().remove(drawer);
        }

        static void relayout(FxDrawer drawer) {
            if (drawer.getParent() instanceof Pane layer) layer.requestLayout();
        }

        private static Pane layerOf(Scene scene) {
            Parent root = scene.getRoot();
            StackPane stack;
            if (root instanceof StackPane sp) {
                stack = sp;
            } else {
                stack = new StackPane();
                stack.getStylesheets().setAll(root.getStylesheets());
                scene.setRoot(stack);
                stack.getChildren().add(root);
            }
            for (Node child : stack.getChildren()) {
                if (child.getStyleClass().contains(LAYER_CLASS)) return (Pane) child;
            }
            Pane layer = new Pane() {
                @Override
                protected void layoutChildren() {
                    place(this);
                }
            };
            layer.getStyleClass().add(LAYER_CLASS);
            layer.setPickOnBounds(false);
            // Over the content, under what the overlay keeps above it (the busy
            // scrim, the toasts): right after the first child.
            stack.getChildren().add(Math.min(1, stack.getChildren().size()), layer);
            // A container moves without the layer being told (a scroll, a
            // resized sibling): lay the drawers again after every pulse, which
            // costs nothing when nothing moved.
            scene.addPostLayoutPulseListener(() -> {
                if (!layer.getChildren().isEmpty()) place(layer);
            });
            return layer;
        }

        /** Every drawer on its area; minimized handles at one edge of one area lined up. */
        private static void place(Pane layer) {
            Map<String, Double> taken = new HashMap<>();
            List<Node> drawers = new ArrayList<>(layer.getChildren());
            for (Node n : drawers) {
                if (!(n instanceof FxDrawer d)) continue;
                Bounds a = d.area();
                if (a == null || a.getWidth() <= 0 || a.getHeight() <= 0) {
                    d.setVisible(false);
                    continue;
                }
                d.setVisible(d.state != UiDrawer.State.CLOSED);
                if (d.getLayoutX() != a.getMinX() || d.getLayoutY() != a.getMinY()
                        || d.getWidth() != a.getWidth() || d.getHeight() != a.getHeight()) {
                    d.resizeRelocate(a.getMinX(), a.getMinY(), a.getWidth(), a.getHeight());
                }
                if (d.model.getScope() == UiDrawer.Scope.CONTAINER) {
                    if (!(d.getClip() instanceof Rectangle r) || r.getWidth() != a.getWidth() || r.getHeight() != a.getHeight()) {
                        d.setClip(new Rectangle(a.getWidth(), a.getHeight()));
                    }
                } else if (d.getClip() != null) {
                    d.setClip(null);
                }
                if (d.state == UiDrawer.State.MINIMIZED) {
                    String key = System.identityHashCode(d.model.getScope() == UiDrawer.Scope.CONTAINER ? d.anchor.getParent() : layer)
                            + ":" + d.edge;
                    double offset = taken.getOrDefault(key, 0.0);
                    if (d.handleOffset != offset) { d.handleOffset = offset; d.requestLayout(); }
                    d.handle.autosize();
                    double extent = d.alongY() ? d.handle.getWidth() : d.handle.getHeight();
                    taken.put(key, offset + extent + 8);
                }
                d.layout();
            }
        }
    }
}
