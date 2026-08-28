package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiScrollPane;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiScrollPane} as a {@link ScrollPane}. {@code maxHeight} caps
 * the viewport; without one the pane takes the leftover vertical space, which
 * is what the flex-column rule of the web renderers amounts to here.
 *
 * <p>{@code stickToLatest} mirrors the browser's auto-enhance: the pane starts
 * pinned to the newest content and stays there while it grows, until the user
 * scrolls up to read; coming back to the bottom re-arms it. The floating
 * jump-to-latest arrow of the web version has no counterpart here — scrolling
 * back down is the only way back to sticking.
 */
public class ScrollPaneRenderer implements FxNodeRenderer<UiScrollPane> {

    /** Distance from the bottom that still counts as "at the latest", in pixels. */
    static final double AT_BOTTOM_THRESHOLD = 60;

    @Override
    public Node render(UiScrollPane node, FxRenderContext ctx) {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        if (node.getContent() != null) {
            scroll.setContent(ctx.render(node.getContent()));
        }
        double capped = viewportHeight(node.getMaxHeight());
        if (capped > 0) {
            scroll.setPrefViewportHeight(capped);
            scroll.setMaxHeight(capped);
        }
        // No cap means "fill what the parent has left" — a grow hint is the
        // JavaFX way to say that inside the VBox a UiStack paints as.
        VBox.setVgrow(scroll, Priority.ALWAYS);

        if (Boolean.TRUE.equals(node.getStickToLatest())) {
            stickToLatest(scroll);
        }
        return scroll;
    }

    /**
     * The model carries a CSS length. Pixels and bare numbers translate; a
     * viewport-relative unit like {@code 60vh} has no JavaFX equivalent and
     * leaves the pane uncapped, so it fills its parent instead.
     */
    private static double viewportHeight(String maxHeight) {
        if (maxHeight == null) return 0;
        String value = maxHeight.trim().toLowerCase();
        if (value.endsWith("px")) value = value.substring(0, value.length() - 2).trim();
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notAPixelLength) {
            return 0;
        }
    }

    /**
     * Keeps the view glued to the newest content. Sticking is armed while the
     * viewport sits at the bottom and disarms as soon as the user scrolls away
     * from it, so reading back through a feed is not fought by every arriving
     * message.
     */
    private static void stickToLatest(ScrollPane scroll) {
        boolean[] stuck = {true};
        scroll.vvalueProperty().addListener((obs, old, v) -> stuck[0] = atBottom(scroll));

        var onGrow = (javafx.beans.value.ChangeListener<Bounds>) (obs, old, bounds) -> {
            if (stuck[0]) scroll.setVvalue(scroll.getVmax());
        };
        scroll.contentProperty().addListener((obs, old, content) -> {
            if (old != null) old.boundsInLocalProperty().removeListener(onGrow);
            if (content != null) {
                content.boundsInLocalProperty().addListener(onGrow);
                stuck[0] = true;
                scroll.setVvalue(scroll.getVmax());
            }
        });
        if (scroll.getContent() != null) {
            scroll.getContent().boundsInLocalProperty().addListener(onGrow);
            scroll.setVvalue(scroll.getVmax());
        }
    }

    /** Whether the viewport is within {@link #AT_BOTTOM_THRESHOLD} of the end. */
    private static boolean atBottom(ScrollPane scroll) {
        Node content = scroll.getContent();
        if (content == null) return true;
        double hidden = content.getBoundsInLocal().getHeight()
                - scroll.getViewportBounds().getHeight();
        if (hidden <= 0) return true;   // everything fits: there is no "up there"
        double range = scroll.getVmax() - scroll.getVmin();
        double fromBottom = range <= 0 ? 0
                : (scroll.getVmax() - scroll.getVvalue()) / range * hidden;
        return fromBottom < AT_BOTTOM_THRESHOLD;
    }
}
