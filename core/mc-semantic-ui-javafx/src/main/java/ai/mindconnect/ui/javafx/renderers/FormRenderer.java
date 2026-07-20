package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxFormScope;
import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiForm;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiForm}: title, error banner, the flat {@code fields}, the
 * free-form {@code content} tree, then the actions row.
 *
 * <p>The form opens a {@link FxFormScope} and renders everything below it in
 * that scope, so every field — however deeply nested, in whichever tab —
 * registers into the same payload. That is the JavaFX equivalent of the web
 * client collecting all named controls inside the {@code <form>} element, and
 * it is why a form still submits as one object when it is laid out with stacks
 * and tabs.
 *
 * <p>The scope is also registered on the bus under the form's id, so a trigger
 * built with {@code UiTrigger.invoke("save", "customer-form")} resolves the
 * values from anywhere.
 */
public class FormRenderer implements FxNodeRenderer<UiForm> {

    @Override
    public Node render(UiForm node, FxRenderContext ctx) {
        var scope = new FxFormScope(node.getId());
        var formCtx = ctx.withForm(scope);
        ctx.bus().registerPayloadSource(node.getId(), scope::values);

        var box = new VBox(12);
        box.setPadding(new Insets(8));

        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-form-title");
            box.getChildren().add(title);
        }

        if (node.getFormError() != null) {
            var error = new Label(node.getFormError());
            error.getStyleClass().add("sui-form-error");
            error.setWrapText(true);
            box.getChildren().add(error);
        }

        node.getFields().forEach(field -> box.getChildren().add(formCtx.render(field)));

        if (node.getContent() != null) {
            node.getContent().forEach(child -> box.getChildren().add(formCtx.render(child)));
        }

        if (!node.getActions().isEmpty()) {
            var actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.getStyleClass().add("sui-form-actions");
            node.getActions().forEach(action -> actions.getChildren().add(formCtx.render(action)));
            box.getChildren().add(actions);

            // "Submitting the form" — what submitOnChange / submitOnEnter fire.
            // The primary action is the submit if there is one, else the first.
            var submit = node.getActions().stream()
                    .filter(a -> a.getStyle() == UiAction.Style.PRIMARY)
                    .findFirst()
                    .orElse(node.getActions().get(0));
            scope.onSubmit(() -> ctx.bus().dispatch(submit.getOnClick(), submit, formCtx));
        }

        if (node.getLinks() != null && !node.getLinks().isEmpty()) {
            var links = new HBox(8);
            links.setAlignment(Pos.CENTER_LEFT);
            node.getLinks().forEach(link -> links.getChildren().add(formCtx.render(link)));
            box.getChildren().add(links);
        }

        return box;
    }
}
