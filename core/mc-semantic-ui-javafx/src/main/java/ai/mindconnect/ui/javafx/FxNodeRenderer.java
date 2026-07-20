package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.model.UiNode;
import javafx.scene.Node;

/**
 * Paints one {@link UiNode} subtype into a JavaFX {@link Node}. The JavaFX
 * counterpart of a TS renderer in {@code renderers/*.ts} and of a
 * {@code templates/sui/{type}.hbs} template — same model, third output.
 *
 * <p>Renderers are registered per model class in
 * {@link SuiFxRenderer#register(Class, FxNodeRenderer)}. They only paint;
 * the common {@link UiNode} plumbing (id indexing, css class, the
 * {@code onClick}/{@code onHover}/… triggers) is wired by
 * {@link SuiFxRenderer} around the result.
 *
 * @param <T> the model type this renderer draws
 */
@FunctionalInterface
public interface FxNodeRenderer<T extends UiNode> {

    /**
     * @param node the model node to paint
     * @param ctx  the surrounding render context — use {@link FxRenderContext#render(UiNode)}
     *             for children so they get the same indexing and form scope
     * @return the JavaFX node; never {@code null}
     */
    Node render(T node, FxRenderContext ctx);
}
