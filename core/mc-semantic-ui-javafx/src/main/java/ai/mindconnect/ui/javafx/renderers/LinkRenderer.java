package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxEventBus;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;

/**
 * Paints {@link UiLink} as a {@link Hyperlink}.
 *
 * <p>Where it goes follows the model: an explicit {@code onClick} wins, else
 * the {@code href} is navigated. An {@link UiLink#isExternal() external} link
 * opens in the desktop browser ({@code OPEN_IN_TAB}) — in a rich client that
 * is the only sensible reading of "external", since there is no tab to open
 * it in otherwise.
 */
public class LinkRenderer implements FxNodeRenderer<UiLink> {

    @Override
    public Node render(UiLink node, FxRenderContext ctx) {
        var label = node.getLabel() != null ? node.getLabel() : node.getTitle();
        var link = new Hyperlink(label != null ? label : node.getHref());

        link.setOnAction(e -> ctx.bus().dispatch(triggerFor(node), node, ctx));
        link.getProperties().put(SuiFxEventBus.CLICK_HANDLED_KEY, Boolean.TRUE);
        return link;
    }

    private UiTrigger triggerFor(UiLink node) {
        if (node.getOnClick() != null) return node.getOnClick();
        if (node.getHref() == null) return null;
        return node.isExternal()
                ? UiTrigger.openInTab(node.getHref())
                : UiTrigger.go(node.getHref());
    }
}
