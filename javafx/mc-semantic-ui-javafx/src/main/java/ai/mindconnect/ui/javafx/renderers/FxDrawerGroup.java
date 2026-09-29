package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.model.UiDrawer;
import ai.mindconnect.ui.model.UiDrawerGroup;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The painted {@link UiDrawerGroup}: a strip at one edge holding several
 * drawers, and the rule for how they share it.
 *
 * <p>The group is the thing at the edge — it spans its area like a lone
 * overlay drawer would, or stands in the layout like a PUSH one — with the
 * strip's size along the edge's axis. The drawers inside are painted by
 * {@link DrawerRenderer} as everywhere else, with the group's edge, scope
 * and mode written over their own and no grip of their own, and laid out by
 * the group instead of themselves: they are items of the strip. Their wiring
 * (the handle opens, the header's buttons minimize and close, the states
 * survive a patch) is the drawer's own and works unchanged.
 *
 * <ul>
 *   <li>{@link UiDrawerGroup.Arrange#SHARE} — the open drawers divide the
 *       strip in equal parts along the edge. A minimized one drops to its
 *       handle and the others take its room.</li>
 *   <li>{@link UiDrawerGroup.Arrange#STACK} — one column whatever the edge:
 *       the drawer in front fills it, the other open ones are their header
 *       bars above it, and pressing a bar brings that drawer forward.
 *       Opening a drawer from its handle puts it in front; minimizing the
 *       front one leaves the next open one there. A switch the user made is
 *       reported through {@code onActiveChange} with {@code {id}} filled
 *       in.</li>
 * </ul>
 *
 * <p>Minimized handles stay at the edge of the area: for an overlay group
 * lined up along it as a lone drawer's would be, above the open panels; for
 * a PUSH group in the strip's row, at their own size.
 */
public final class FxDrawerGroup extends Pane implements FxFloating {

    /** Where a group's node keeps its group, for {@link #of}. */
    private static final String KEY = "sui-fx-drawer-group";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NEXT_CLASS = "sui-drawer-next";
    private static final double GRIP = 6;

    private final UiDrawerGroup model;
    private final FxRenderContext ctx;
    private final UiDrawer.Edge edge;
    private final UiDrawer.Scope scope;
    private final UiDrawer.Mode mode;
    private final UiDrawerGroup.Arrange arrange;
    /** The strip's background, border and shadow: one for all the drawers. */
    private final Region strip = new Region();
    private final Region grip = new Region();
    private final Region anchor = new Region();
    /** STACK: the id of the drawer that should be in front. */
    private String active;
    /** STACK: the drawer in front, or null when none is open. */
    private FxDrawer front;
    /** A size the user dragged to, in pixels; overrides {@code size}. */
    private Double dragged;

    FxDrawerGroup(UiDrawerGroup model, FxRenderContext ctx, String keptActive) {
        this.model = model;
        this.ctx = ctx;
        this.edge = model.getEdge() != null ? model.getEdge() : UiDrawer.Edge.RIGHT;
        this.scope = model.getScope() != null ? model.getScope() : UiDrawer.Scope.VIEWPORT;
        this.mode = model.getMode() != null ? model.getMode() : UiDrawer.Mode.OVERLAY;
        this.arrange = model.getArrange() != null ? model.getArrange() : UiDrawerGroup.Arrange.SHARE;
        this.active = model.getActive() != null ? model.getActive() : keptActive;
        getStyleClass().addAll("sui-drawer-group", "sui-drawer-group-" + edge.name().toLowerCase(),
                "sui-drawer-group-" + arrange.name().toLowerCase(),
                mode == UiDrawer.Mode.PUSH ? "sui-drawer-group-push" : "sui-drawer-group-overlay");
        getProperties().put(KEY, this);
        anchor.getProperties().put(KEY, this);
        anchor.setManaged(false);
        anchor.setVisible(false);
        // Clicks on the empty rest of an overlay's area reach what lies below.
        setPickOnBounds(false);

        strip.getStyleClass().add("sui-drawer-group-strip");
        strip.setViewOrder(1);              // behind the drawers
        getChildren().add(strip);
        if (model.getDrawers() != null) {
            for (UiDrawer d : model.getDrawers()) getChildren().add(paint(d));
        }
        if (model.isResizable()) buildGrip();
        getChildren().addListener(this::adopt);
        settle();

        anchor.sceneProperty().addListener((o, was, now) -> {
            if (was != null) FxDrawer.Layer.remove(this);
            if (now != null) FxDrawer.Layer.add(this, now);
        });
    }

    /** The group behind a node this renderer painted — the group itself or its anchor — or null. */
    public static FxDrawerGroup of(Node node) {
        return node == null ? null : (FxDrawerGroup) node.getProperties().get(KEY);
    }

    /** What stands in the layout for an overlay group: invisible, taking no room. */
    Node anchor() {
        return anchor;
    }

    public UiDrawerGroup model() {
        return model;
    }

    public UiDrawerGroup.Arrange arrange() {
        return arrange;
    }

    /** STACK: the id of the drawer in front — the one the user brought there, or the model's. */
    public String getActive() {
        return active;
    }

    /** STACK: the drawer in front, or null when none is open. */
    public FxDrawer front() {
        return front;
    }

    /** The drawers of the strip, in their order — every state, closed ones too. */
    public List<FxDrawer> drawers() {
        var out = new ArrayList<FxDrawer>();
        for (Node n : getChildren()) if (n instanceof FxDrawer d) out.add(d);
        return out;
    }

    /**
     * STACK: brings {@code drawer} to the front — parts shown and hidden,
     * nothing rendered — and, when it was the user, tells the server through
     * {@code onActiveChange} with {@code {id}} filled in. Returns whether
     * anything changed: a drawer that is not open, or is in front already,
     * stays as it is.
     */
    public boolean front(FxDrawer drawer, boolean byUser) {
        if (arrange != UiDrawerGroup.Arrange.STACK || drawer.getState() != UiDrawer.State.OPEN) return false;
        if (drawer == front) return false;
        active = drawer.id();
        settle();
        if (byUser) fire(drawer.id());
        return true;
    }

    /**
     * A drawer of the strip changed its state. A drawer the user opened in a
     * stack is the one they want to see; anything else — a minimize, a
     * close, a patch — settles who is in front without a word to the server.
     */
    void stateChanged(FxDrawer drawer, UiDrawer.State next, boolean byUser) {
        if (byUser && next == UiDrawer.State.OPEN && front(drawer, true)) return;
        settle();
    }

    /**
     * STACK: the drawer in front is the one {@link #active} names when it is
     * open, else the first open one; every other open drawer peeks with its
     * bar. SHARE: nothing to settle. Either way the strip is laid out again.
     */
    private void settle() {
        front = null;
        if (arrange == UiDrawerGroup.Arrange.STACK) {
            List<FxDrawer> open = open();
            front = open.stream().filter(d -> Objects.equals(d.id(), active)).findFirst()
                    .orElse(open.isEmpty() ? null : open.get(0));
            if (front != null) active = front.id();
            // The desktop's data-active: what the screen knows, kept on the
            // model so a MERGE over it and a later read see the same thing.
            model.setActive(active);
        }
        for (FxDrawer d : drawers()) {
            d.setPeeking(arrange == UiDrawerGroup.Arrange.STACK && d.getState() == UiDrawer.State.OPEN && d != front);
        }
        requestLayout();
        FxDrawer.Layer.relayout(this);
    }

    private void fire(String id) {
        UiTrigger trigger = model.getOnActiveChange();
        if (trigger == null || ctx.bus() == null || id == null) return;
        UiTrigger copy = MAPPER.convertValue(trigger, UiTrigger.class);
        if (copy.getUrl() != null) copy.setUrl(copy.getUrl().replace("{id}", id));
        ctx.bus().dispatch(copy, model, ctx);
    }

    // ── the drawers ───────────────────────────────────────────────────────

    /**
     * Paints {@code d} as an item of this strip: through the renderer, so it
     * is indexed and patchable like any node, from a copy that carries the
     * group's edge and scope and no grip of its own.
     */
    private FxDrawer paint(UiDrawer d) {
        Node painted = ctx.render(grouped(d));
        FxDrawer drawer = FxDrawer.of(painted);
        // The copy is a PUSH drawer — that is what an item of a strip is,
        // laid out by its parent — but it should look like what the group
        // is: an overlay group's handles sit flush on the edge.
        if (mode != UiDrawer.Mode.PUSH) {
            drawer.getStyleClass().remove("sui-drawer-push");
            drawer.getStyleClass().add("sui-drawer-overlay");
        }
        drawer.join(this);
        return drawer;
    }

    /**
     * {@code d} with the group's say written over its own: the edge and the
     * scope are the group's, its resizable is ignored — the strip has the
     * grip — and it stands in the strip's layout as a PUSH drawer stands in
     * its parent's. Everything else — title, icon, badge, content, closable,
     * onClose, state and the triggers — is the drawer's.
     */
    private UiDrawer grouped(UiDrawer d) {
        UiDrawer c = new UiDrawer();
        c.setId(d.getId());
        c.setTitle(d.getTitle());
        c.setCssClass(d.getCssClass());
        c.setDisplay(d.getDisplay());
        c.setOnClick(d.getOnClick());
        c.setOnDblClick(d.getOnDblClick());
        c.setOnHover(d.getOnHover());
        c.setOnLeave(d.getOnLeave());
        c.setOnChange(d.getOnChange());
        c.setOnInput(d.getOnInput());
        c.setContent(d.getContent());
        c.setIcon(d.getIcon());
        c.setBadge(d.getBadge());
        c.setState(d.getState());
        c.setSize(d.getSize());
        c.setMinSize(d.getMinSize());
        c.setMaxSize(d.getMaxSize());
        c.setClosable(d.isClosable());
        c.setOnClose(d.getOnClose());
        c.setOnStateChange(d.getOnStateChange());
        c.setEdge(edge);
        c.setScope(scope);
        c.setMode(UiDrawer.Mode.PUSH);
        c.setResizable(false);
        return c;
    }

    /**
     * A patch that replaces or appends a drawer of the strip paints it on
     * its own terms — its own edge, maybe as an overlay's anchor. Painted
     * again as an item of this strip, in its place, so the group stays one
     * truth about where its drawers stand.
     */
    private void adopt(ListChangeListener.Change<? extends Node> change) {
        while (change.next()) {
            if (!change.wasAdded()) continue;
            for (Node added : change.getAddedSubList()) {
                if (added == strip || added == grip) continue;
                if (added instanceof FxDrawer d && d.group() == this) continue;
                // Not from inside the list's own change: the swap is one more.
                Platform.runLater(() -> repaint(added));
            }
        }
    }

    private void repaint(Node painted) {
        int at = getChildren().indexOf(painted);
        if (at < 0) return;
        if (!(painted.getProperties().get(SuiFxEventBus.MODEL_KEY) instanceof UiDrawer d)) return;
        getChildren().set(at, paint(d));
        settle();
    }

    /** The drawers that are not closed, in their order. */
    private List<FxDrawer> items() {
        var out = new ArrayList<FxDrawer>();
        for (FxDrawer d : drawers()) if (d.getState() != UiDrawer.State.CLOSED) out.add(d);
        return out;
    }

    private List<FxDrawer> open() {
        var out = new ArrayList<FxDrawer>();
        for (FxDrawer d : drawers()) if (d.getState() == UiDrawer.State.OPEN) out.add(d);
        return out;
    }

    // ── size and layout ───────────────────────────────────────────────────

    private void buildGrip() {
        grip.getStyleClass().add("sui-drawer-resize");
        grip.setCursor(alongY() ? Cursor.V_RESIZE : Cursor.H_RESIZE);
        grip.setViewOrder(-1);
        grip.setOnMouseDragged(e -> {
            var p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double px = switch (edge) {
                case BOTTOM -> getHeight() - p.getY();
                case TOP -> p.getY();
                case LEFT -> p.getX();
                case RIGHT -> getWidth() - p.getX();
            };
            dragged = Math.max(0, px);
            requestLayout();
            FxDrawer.Layer.relayout(this);
        });
        getChildren().add(grip);
    }

    private boolean alongY() {
        return edge == UiDrawer.Edge.TOP || edge == UiDrawer.Edge.BOTTOM;
    }

    /** The strip's size along the edge's axis, in pixels, for an area of {@code extent}. */
    double size(double extent) {
        Scene scene = getScene() != null ? getScene() : anchor.getScene();
        double sw = scene != null ? scene.getWidth() : extent, sh = scene != null ? scene.getHeight() : extent;
        double size = dragged != null ? dragged : FxDrawer.px(model.getSize(), extent, sw, sh, extent * 0.4);
        double min = FxDrawer.px(model.getMinSize(), extent, sw, sh, 0);
        double max = FxDrawer.px(model.getMaxSize(), extent, sw, sh, extent);
        return Math.max(min, Math.min(max, Math.min(size, extent)));
    }

    private static double handleW(FxDrawer d) {
        return d.handle().prefWidth(-1);
    }

    private static double handleH(FxDrawer d) {
        return d.handle().prefHeight(-1);
    }

    /**
     * PUSH, nothing open: the strip is its handles, and this is their extent
     * along the edge's axis — a row along a horizontal edge is as thick as
     * its tallest handle, a column (a vertical edge, or a stack) as long as
     * all of them.
     */
    private double handlesExtent() {
        double sum = 0, max = 0;
        for (FxDrawer d : items()) {
            if (d.getState() != UiDrawer.State.MINIMIZED) continue;
            double along = alongY() ? handleH(d) : handleW(d);
            sum += along;
            max = Math.max(max, along);
        }
        return alongY() && arrange == UiDrawerGroup.Arrange.STACK ? sum : max;
    }

    @Override
    protected double computePrefWidth(double height) {
        if (mode != UiDrawer.Mode.PUSH || alongY()) return super.computePrefWidth(height);
        if (open().isEmpty()) return handlesExtent();
        double extent = getParent() instanceof Region r && r.getWidth() > 0 ? r.getWidth() : 800;
        return size(extent);
    }

    @Override
    protected double computePrefHeight(double width) {
        if (mode != UiDrawer.Mode.PUSH || !alongY()) return super.computePrefHeight(width);
        if (open().isEmpty()) return handlesExtent();
        double extent = getParent() instanceof Region r && r.getHeight() > 0 ? r.getHeight() : 600;
        return size(extent);
    }

    @Override
    protected double computeMinWidth(double height) {
        return mode == UiDrawer.Mode.PUSH && !alongY() ? computePrefWidth(height) : 0;
    }

    @Override
    protected double computeMinHeight(double width) {
        return mode == UiDrawer.Mode.PUSH && alongY() ? computePrefHeight(width) : 0;
    }

    /**
     * OVERLAY: the group spans its whole area and lets clicks through; the
     * strip lies on the edge with its size when anything is open, and the
     * minimized handles sit at the edge, lined up. PUSH: the group is the
     * strip.
     */
    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        List<FxDrawer> items = items();
        List<FxDrawer> open = open();
        boolean any = !open.isEmpty();
        double sx = 0, sy = 0, sw = w, sh = h;
        if (mode != UiDrawer.Mode.PUSH) {
            double s = any ? size(alongY() ? h : w) : 0;
            switch (edge) {
                case BOTTOM -> { sy = h - s; sh = s; }
                case TOP -> sh = s;
                case LEFT -> sw = s;
                case RIGHT -> { sx = w - s; sw = s; }
            }
        }
        strip.setVisible(any);
        strip.resizeRelocate(sx, sy, sw, sh);
        grip.setVisible(any);
        placeGrip(sx, sy, sw, sh);
        if (arrange == UiDrawerGroup.Arrange.STACK) layoutStack(sx, sy, sw, sh, items);
        else layoutShare(sx, sy, sw, sh, items, open);
        if (mode != UiDrawer.Mode.PUSH) placeHandles(w, h, items);
    }

    /** SHARE: the open drawers in equal parts along the edge; PUSH keeps the handles in the row at their own size. */
    private void layoutShare(double x, double y, double w, double h, List<FxDrawer> items, List<FxDrawer> open) {
        boolean row = alongY();
        List<FxDrawer> inStrip = mode == UiDrawer.Mode.PUSH ? items : open;
        double fixed = 0;
        for (FxDrawer d : inStrip) if (d.getState() != UiDrawer.State.OPEN) fixed += row ? handleW(d) : handleH(d);
        double share = open.isEmpty() ? 0 : Math.max(0, (row ? w : h) - fixed) / open.size();
        double cursor = row ? x : y;
        int nth = 0;
        for (FxDrawer d : inStrip) {
            boolean isOpen = d.getState() == UiDrawer.State.OPEN;
            mark(d, isOpen && nth++ > 0);
            if (row) {
                double iw = isOpen ? share : handleW(d), ih = isOpen ? h : handleH(d);
                d.resizeRelocate(cursor, edge == UiDrawer.Edge.TOP ? y : y + h - ih, iw, ih);
                cursor += iw;
            } else {
                double iw = isOpen ? w : handleW(d), ih = isOpen ? share : handleH(d);
                d.resizeRelocate(edge == UiDrawer.Edge.LEFT ? x : x + w - iw, cursor, iw, ih);
                cursor += ih;
            }
        }
    }

    /**
     * STACK: one column whatever the edge — the bars of the open drawers
     * behind the front one (and, PUSH, the handles) in their order, then the
     * front drawer with what is left.
     */
    private void layoutStack(double x, double y, double w, double h, List<FxDrawer> items) {
        double cursor = y;
        for (FxDrawer d : items) {
            mark(d, false);
            if (d == front) continue;
            if (d.getState() == UiDrawer.State.OPEN) {
                double bh = d.barHeight();
                d.resizeRelocate(x, cursor, w, bh);
                cursor += bh;
            } else if (mode == UiDrawer.Mode.PUSH) {
                double hw = handleW(d), hh = handleH(d);
                d.resizeRelocate(x, cursor, hw, hh);
                cursor += hh;
            }
        }
        if (front != null) front.resizeRelocate(x, cursor, w, Math.max(0, y + h - cursor));
    }

    /** OVERLAY: the minimized handles at the edge of the area, lined up from its far end, above the open panels. */
    private void placeHandles(double w, double h, List<FxDrawer> items) {
        double offset = 0, gap = 16;
        for (FxDrawer d : items) {
            if (d.getState() != UiDrawer.State.MINIMIZED) {
                d.setViewOrder(0);
                continue;
            }
            double hw = handleW(d), hh = handleH(d);
            switch (edge) {
                case BOTTOM -> d.resizeRelocate(w - gap - offset - hw, h - hh, hw, hh);
                case TOP -> d.resizeRelocate(w - gap - offset - hw, 0, hw, hh);
                case LEFT -> d.resizeRelocate(0, gap + offset, hw, hh);
                case RIGHT -> d.resizeRelocate(w - hw, gap + offset, hw, hh);
            }
            offset += (alongY() ? hw : hh) + 8;
            d.setViewOrder(-1);
        }
    }

    /** The rule between neighbours in a SHARE strip: a class on every open drawer after the first. */
    private static void mark(FxDrawer d, boolean next) {
        boolean has = d.getStyleClass().contains(NEXT_CLASS);
        if (next && !has) d.getStyleClass().add(NEXT_CLASS);
        else if (!next && has) d.getStyleClass().remove(NEXT_CLASS);
    }

    private void placeGrip(double x, double y, double w, double h) {
        if (!getChildren().contains(grip)) return;
        switch (edge) {
            case BOTTOM -> grip.resizeRelocate(x, y - GRIP / 2, w, GRIP);
            case TOP -> grip.resizeRelocate(x, y + h - GRIP / 2, w, GRIP);
            case LEFT -> grip.resizeRelocate(x + w - GRIP / 2, y, GRIP, h);
            case RIGHT -> grip.resizeRelocate(x - GRIP / 2, y, GRIP, h);
        }
    }

    // ── floating ──────────────────────────────────────────────────────────

    @Override
    public Bounds area() {
        return FxDrawer.areaOf(this, anchor, scope);
    }

    @Override
    public boolean showing() {
        return !items().isEmpty();
    }

    @Override
    public boolean clipped() {
        return scope == UiDrawer.Scope.CONTAINER;
    }
}
