package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.model.UiUpload;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints {@link UiUpload} as a drop zone with a browse button.
 *
 * <p>Both routes end in the same place: dropping files on the zone and picking
 * them through the button dispatch {@link UiUpload#getOnUpload()} with the
 * files attached, which the bus's {@code UPLOAD} behaviour posts as
 * multipart. That mirrors the web renderer, where the drop handler and the
 * hidden file input both feed the same trigger.
 *
 * <p>The drag-over highlight is a style class ({@code sui-upload--dragover}),
 * the same name the web renderer uses — so a theme styles both the same way.
 */
public class UploadRenderer implements FxNodeRenderer<UiUpload> {

    static final String DRAGOVER_CLASS = "sui-upload--dragover";

    @Override
    public Node render(UiUpload node, FxRenderContext ctx) {
        var box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("sui-upload-zone");

        if (node.getLabel() != null) {
            var label = new Label(node.getLabel());
            label.getStyleClass().add("sui-upload-label");
            box.getChildren().add(label);
        }

        var prompt = new Label(node.getDropText() != null
                ? node.getDropText()
                : "Drag files here or");
        prompt.getStyleClass().add("sui-upload-prompt");

        var browse = new Button(node.getButtonLabel() != null ? node.getButtonLabel() : "Browse…");
        browse.getStyleClass().add("sui-upload-browse");
        browse.setOnAction(e -> {
            var files = choose(node, browse);
            if (!files.isEmpty()) fire(node, files, ctx);
        });

        var row = new HBox(8, prompt, browse);
        row.setAlignment(Pos.CENTER);
        box.getChildren().add(row);

        if (node.getHint() != null) {
            var hint = new Label(node.getHint());
            hint.getStyleClass().add("sui-upload-hint");
            hint.setWrapText(true);
            box.getChildren().add(hint);
        }

        wireDragAndDrop(node, box, ctx);
        return box;
    }

    private void wireDragAndDrop(UiUpload node, VBox zone, FxRenderContext ctx) {
        zone.setOnDragOver(e -> {
            if (hasFiles(e)) {
                // Accepting the drag is what makes the following drop fire at
                // all — the JavaFX equivalent of preventDefault() on dragover.
                e.acceptTransferModes(TransferMode.COPY);
                if (!zone.getStyleClass().contains(DRAGOVER_CLASS)) {
                    zone.getStyleClass().add(DRAGOVER_CLASS);
                }
            }
            e.consume();
        });
        zone.setOnDragExited(e -> {
            zone.getStyleClass().remove(DRAGOVER_CLASS);
            e.consume();
        });
        zone.setOnDragDropped(e -> {
            var files = hasFiles(e) ? e.getDragboard().getFiles() : List.<File>of();
            boolean accepted = !files.isEmpty();
            if (accepted) fire(node, limit(node, files), ctx);
            zone.getStyleClass().remove(DRAGOVER_CLASS);
            e.setDropCompleted(accepted);
            e.consume();
        });
    }

    private static boolean hasFiles(DragEvent e) {
        return e.getDragboard().hasFiles();
    }

    private List<File> choose(UiUpload node, Node owner) {
        var chooser = new FileChooser();
        if (node.getAccept() != null) {
            var globs = new ArrayList<String>();
            for (var token : node.getAccept().split(",")) {
                var t = token.trim();
                if (t.startsWith(".")) globs.add("*" + t);
            }
            if (!globs.isEmpty()) {
                chooser.getExtensionFilters()
                        .add(new FileChooser.ExtensionFilter(node.getAccept(), globs));
            }
        }
        var window = owner.getScene() == null ? null : owner.getScene().getWindow();
        if (node.isMultiple()) {
            var picked = chooser.showOpenMultipleDialog(window);
            return picked == null ? List.of() : picked;
        }
        var picked = chooser.showOpenDialog(window);
        return picked == null ? List.of() : List.of(picked);
    }

    /** A single-file zone takes the first of a multi-file drop, not all of them. */
    private List<File> limit(UiUpload node, List<File> files) {
        return node.isMultiple() || files.size() <= 1 ? List.copyOf(files) : List.of(files.get(0));
    }

    private void fire(UiUpload node, List<File> files, FxRenderContext ctx) {
        if (node.getOnUpload() == null || files.isEmpty()) return;
        ctx.bus().dispatch(node.getOnUpload(), node, ctx, files);
    }
}
