package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiDrawer;
import javafx.scene.Node;

/**
 * A {@link UiDrawer} — a panel from an edge that minimizes to a handle.
 *
 * <p>{@link UiDrawer.Mode#PUSH}: the drawer itself stands in the layout, like
 * any node, and takes room along its edge's axis. {@link UiDrawer.Mode#OVERLAY}
 * (the default): what stands in the layout is an invisible anchor, and the
 * drawer floats in a layer over the scene, laid on the window's edge
 * ({@link UiDrawer.Scope#VIEWPORT}) or on the edge of the container the anchor
 * is in ({@link UiDrawer.Scope#CONTAINER}), clipped to it — see
 * {@link FxDrawer}.
 *
 * <p>A patch that replaces the drawer keeps the state the user left it in,
 * unless the new model sets one — the same rule the web renderers follow.
 */
public class DrawerRenderer implements FxNodeRenderer<UiDrawer> {

    @Override
    public Node render(UiDrawer node, FxRenderContext ctx) {
        UiDrawer.State kept = null;
        if (node.getState() == null) {
            // Rendering comes before the index moves on: byId is still the
            // node being replaced, if there is one.
            FxDrawer before = FxDrawer.of(ctx.byId(node.getId()));
            if (before != null) kept = before.getState();
        }
        var drawer = new FxDrawer(node, ctx, kept);
        return node.getMode() == UiDrawer.Mode.PUSH ? drawer : drawer.anchor();
    }
}
