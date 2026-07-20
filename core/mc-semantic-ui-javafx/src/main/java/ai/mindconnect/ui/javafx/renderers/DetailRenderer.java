package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiDetail;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Paints {@link UiDetail} as a definition list — label/value rows, then the
 * action bar.
 *
 * <p>The web renderers emit a {@code <dl>} here, not a form: a detail is for
 * <em>reading</em>. So the values render as text no matter what
 * {@link ai.mindconnect.ui.model.UiField#isEditable()} says, and an empty one
 * shows an em dash rather than collapsing the row — a missing value is
 * information too.
 */
public class DetailRenderer implements FxNodeRenderer<UiDetail> {

    private static final String EMPTY = "—";

    @Override
    public Node render(UiDetail node, FxRenderContext ctx) {
        var box = new VBox(12);
        box.setPadding(new Insets(8));

        if (node.getTitle() != null) {
            var title = new Label(node.getTitle());
            title.getStyleClass().add("sui-detail-title");
            box.getChildren().add(title);
        }

        box.getChildren().add(grid(node));

        var footer = footer(node, ctx);
        if (footer != null) box.getChildren().add(footer);

        return box;
    }

    private GridPane grid(UiDetail node) {
        var grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        grid.getStyleClass().add("sui-detail-grid");

        // Labels stay their natural width; values take the rest, so long text
        // wraps instead of stretching the window.
        var labels = new ColumnConstraints();
        labels.setHalignment(HPos.LEFT);
        var values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);

        int row = 0;
        for (var field : node.getFields()) {
            var label = new Label(field.getLabel() != null ? field.getLabel() : field.getTitle());
            label.getStyleClass().add("sui-detail-label");

            var text = field.getValue() == null || field.getValue().toString().isBlank()
                    ? EMPTY
                    : field.getValue().toString();
            var value = new Label(text);
            value.setWrapText(true);
            value.getStyleClass().add(EMPTY.equals(text) ? "sui-empty" : "sui-detail-value");

            grid.addRow(row++, label, value);
        }
        return grid;
    }

    private Node footer(UiDetail node, FxRenderContext ctx) {
        if (node.getActions().isEmpty() && node.getLinks().isEmpty()) return null;

        var footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("sui-detail-footer");
        node.getActions().forEach(a -> footer.getChildren().add(ctx.render(a)));
        node.getLinks().forEach(l -> footer.getChildren().add(ctx.render(l)));
        return footer;
    }
}
