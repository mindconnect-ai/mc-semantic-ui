package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiDrawer;
import ai.mindconnect.ui.model.UiDrawerGroup;
import javafx.scene.Node;

/**
 * A {@link UiDrawerGroup} — several drawers in one strip at an edge, and the
 * rule for how they get on: side by side ({@link UiDrawerGroup.Arrange#SHARE})
 * or on top of each other ({@link UiDrawerGroup.Arrange#STACK}).
 *
 * <p>The group stands where a lone drawer would: in the layout for
 * {@link UiDrawer.Mode#PUSH}, taking the strip's room; as an invisible anchor
 * for {@link UiDrawer.Mode#OVERLAY}, the strip floating in the drawer layer
 * over the window or the anchor's container — see {@link FxDrawerGroup}.
 *
 * <p>A patch that replaces a STACK group keeps the drawer the user brought to
 * the front, unless the new model names one — the rule a drawer's state
 * follows, applied to {@code active}.
 */
public class DrawerGroupRenderer implements FxNodeRenderer<UiDrawerGroup> {

    @Override
    public Node render(UiDrawerGroup node, FxRenderContext ctx) {
        String kept = null;
        if (node.getActive() == null) {
            // Rendering comes before the index moves on: byId is still the
            // group being replaced, if there is one.
            FxDrawerGroup before = FxDrawerGroup.of(ctx.byId(node.getId()));
            if (before != null) kept = before.getActive();
        }
        var group = new FxDrawerGroup(node, ctx, kept);
        return node.getMode() == UiDrawer.Mode.PUSH ? group : group.anchor();
    }
}
