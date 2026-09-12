package ai.mindconnect.ui.javafx.renderers;

import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.model.UiField;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paints {@link UiField} — the input controls.
 *
 * <p>Each field renders as a small block: label, control, then hint or
 * validation error. The control is picked from
 * {@link UiField.FieldType}; a non-editable field renders as read-only text,
 * the same distinction the web renderers make.
 *
 * <p>While painting, the field registers a live value supplier with the
 * enclosing {@link ai.mindconnect.ui.javafx.FxFormScope}, which is what lets a
 * form submit collect everything below it regardless of nesting.
 *
 * <p>First draft: {@code DATETIME} and {@code REFERENCE} render as plain text
 * inputs, and {@code min}/{@code max}/{@code step} are advisory only (they are
 * not yet enforced by the controls).
 */
public class FieldRenderer implements FxNodeRenderer<UiField> {

    @Override
    public Node render(UiField node, FxRenderContext ctx) {
        if (node.getFieldType() == UiField.FieldType.HIDDEN) {
            return hidden(node, ctx);
        }
        var box = new VBox(4);

        var labelText = SuiFxText.first(node.getLabel(), node.getTitle());
        if (labelText != null) {
            var label = new Label(node.isRequired() ? labelText + " *" : labelText);
            label.getStyleClass().add("sui-field-label");
            box.getChildren().add(label);
        }

        Node control;
        Supplier<Object> value;
        if (!node.isEditable()) {
            var readOnly = new Label(display(node.getValue()));
            readOnly.getStyleClass().add("sui-field-readonly");
            readOnly.setWrapText(true);
            control = readOnly;
            // A read-only field still submits its value — the server rendered
            // it, so it is part of the form's state.
            value = node::getValue;
        } else {
            var built = buildControl(node, ctx);
            control = built.control();
            value = built.value();
        }
        control.getStyleClass().add("sui-field-control");
        box.getChildren().add(withTrailing(withLeadingIcon(control, node, ctx), node, ctx));

        if (node.getId() != null && ctx.form() != null) {
            ctx.form().register(node.getId(), value);
        }

        if (node.getValidationError() != null) {
            var error = new Label(node.getValidationError());
            error.getStyleClass().add("sui-field-error");
            error.setWrapText(true);
            box.getChildren().add(error);
        } else if (SuiFxText.present(node.getHint())) {
            var hint = new Label(node.getHint());
            hint.getStyleClass().add("sui-field-hint");
            hint.setWrapText(true);
            box.getChildren().add(hint);
        }

        return box;
    }

    /**
     * A {@code HIDDEN} field: only its value, handed to the form. The node it
     * returns is a placeholder that takes no room — invisible and out of the
     * layout, the {@code <input type="hidden">} of a scene graph.
     */
    private Node hidden(UiField node, FxRenderContext ctx) {
        if (node.getId() != null && ctx.form() != null) {
            ctx.form().register(node.getId(), node::getValue);
        }
        var placeholder = new Region();
        placeholder.setVisible(false);
        placeholder.setManaged(false);
        return placeholder;
    }

    /** A painted control paired with the supplier that reads its current value. */
    /**
     * Lays the field's icon over the input's leading padding, the way
     * {@code .sui-input-icon} does on the web.
     *
     * <p>Decorative and single-line only, as the model says: a text area or a
     * read-only label is handed back untouched. The input takes a style class
     * so the stylesheet, not this method, decides how much room the glyph gets.
     */
    private Node withLeadingIcon(Node control, UiField node, FxRenderContext ctx) {
        if (node.getIcon() == null || !node.isEditable() || !(control instanceof TextField input)) {
            return control;
        }
        var icon = ctx.icon(node.getIcon());
        if (icon == null) return control;

        input.getStyleClass().add("sui-input--with-icon");
        icon.setColor(javafx.scene.paint.Color.web("#94a3b8"));   // -sui-text-subtle, like the placeholder

        var stack = new StackPane(input, icon);
        stack.setAlignment(Pos.CENTER_LEFT);
        StackPane.setMargin(icon, new Insets(0, 0, 0, 10));
        return stack;
    }

    /**
     * Puts the field's trailing action on the control's row — a "Browse…"
     * beside a path field, say.
     *
     * <p>Editable only, as the model says: there is nothing to browse for on a
     * value the user cannot change. The control takes the room and the action
     * keeps its own width, which is what the web's flex row amounts to here.
     */
    private Node withTrailing(Node control, UiField node, FxRenderContext ctx) {
        if (node.getTrailing() == null || !node.isEditable()) return control;

        var row = new HBox(8, control, ctx.render(node.getTrailing()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sui-field-row");
        HBox.setHgrow(control, Priority.ALWAYS);
        if (control instanceof javafx.scene.layout.Region region) region.setMinWidth(0);
        return row;
    }

    private record Bound(Node control, Supplier<Object> value) { }

    private Bound buildControl(UiField node, FxRenderContext ctx) {
        var type = node.getFieldType() == null ? UiField.FieldType.TEXT : node.getFieldType();
        return switch (type) {
            case TEXTAREA -> textArea(node, ctx);
            case BOOLEAN -> checkBox(node, ctx);
            case DATE -> datePicker(node, ctx);
            case SELECT -> node.isExpanded() ? radioGroup(node, ctx) : comboBox(node, ctx);
            case MULTISELECT -> !node.isExpanded() ? multiSelect(node, ctx)
                    : node.isOrderable() ? new OrderableChoices(node, ctx).bound()
                    : checkboxGroup(node, ctx);
            case FILE -> filePicker(node, ctx);
            case NUMBER, CURRENCY, PERCENT -> numberField(node, ctx);
            default -> textField(node, ctx);
        };
    }

    // ── controls ──────────────────────────────────────────────────────────

    private Bound textField(UiField node, FxRenderContext ctx) {
        var input = new TextField(display(node.getValue()));
        input.setPromptText(node.getPlaceholder());
        wireText(input, node, ctx);
        return new Bound(input, () -> emptyToNull(input.getText()));
    }

    private Bound numberField(UiField node, FxRenderContext ctx) {
        var input = new TextField(display(node.getValue()));
        input.setPromptText(node.getPlaceholder());
        wireText(input, node, ctx);
        // Typed values beat strings in a rich client: hand back a number when
        // the text parses, the raw text when it doesn't (so a half-typed
        // "1.2e" still round-trips into the payload).
        return new Bound(input, () -> {
            var text = emptyToNull(input.getText());
            if (text == null) return null;
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return text;
            }
        });
    }

    private Bound textArea(UiField node, FxRenderContext ctx) {
        var input = new TextArea(display(node.getValue()));
        input.setPromptText(node.getPlaceholder());
        input.setWrapText(true);
        input.setPrefRowCount(4);
        wireText(input, node, ctx);

        if (node.isSubmitOnEnter() && ctx.form() != null) {
            // Enter commits, Shift+Enter inserts a newline — the chat-input
            // gesture the web renderers implement too.
            input.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                    e.consume();
                    ctx.form().submit();
                }
            });
        }
        return new Bound(input, () -> emptyToNull(input.getText()));
    }

    private Bound checkBox(UiField node, FxRenderContext ctx) {
        var box = new CheckBox();
        box.setSelected(Boolean.TRUE.equals(node.getValue())
                || "true".equalsIgnoreCase(String.valueOf(node.getValue())));
        onChanged(box.selectedProperty(), node, ctx);
        return new Bound(box, box::isSelected);
    }

    private Bound datePicker(UiField node, FxRenderContext ctx) {
        var picker = new DatePicker(parseDate(node.getValue()));
        picker.setPromptText(node.getPlaceholder());
        onChanged(picker.valueProperty(), node, ctx);
        return new Bound(picker, picker::getValue);
    }

    private Bound comboBox(UiField node, FxRenderContext ctx) {
        var combo = new ComboBox<UiField.Option>();
        combo.setPromptText(node.getPlaceholder());
        combo.setItems(FXCollections.observableArrayList(options(node)));
        combo.setConverter(optionConverter());
        combo.setValue(findOption(node, node.getValue()));
        onChanged(combo.valueProperty(), node, ctx);
        return new Bound(combo, () -> combo.getValue() == null ? null : combo.getValue().getValue());
    }

    private Bound multiSelect(UiField node, FxRenderContext ctx) {
        var list = new ListView<UiField.Option>(FXCollections.observableArrayList(options(node)));
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(UiField.Option item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : optionLabel(item));
            }
        });
        list.setPrefHeight(120);

        for (var selected : node.selectedValues()) {
            var option = findOption(node, selected);
            if (option != null) list.getSelectionModel().select(option);
        }
        list.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<UiField.Option>) c -> fireChange(node, ctx));

        return new Bound(list, () -> list.getSelectionModel().getSelectedItems().stream()
                .map(UiField.Option::getValue)
                .toList());
    }

    /**
     * An expanded SELECT: a radio button per option, one choice — the same value
     * a combo box gives, or null when nothing is chosen. Which option starts
     * selected comes from {@link UiField#choicesInDisplayOrder()}, the rule the
     * web renderers share.
     */
    private Bound radioGroup(UiField node, FxRenderContext ctx) {
        var toggles = new ToggleGroup();
        var box = new VBox(6);
        box.getStyleClass().add("sui-choice-group");
        for (var choice : node.choicesInDisplayOrder()) {
            var radio = new RadioButton(optionLabel(choice.option()));
            radio.setToggleGroup(toggles);
            radio.setUserData(choice.option().getValue());
            radio.setSelected(choice.checked());
            box.getChildren().add(radio);
        }
        onChanged(toggles.selectedToggleProperty(), node, ctx);
        return new Bound(box, () -> toggles.getSelectedToggle() == null
                ? null
                : toggles.getSelectedToggle().getUserData());
    }

    /** An expanded MULTISELECT: a checkbox per option, submitting the checked values as a list. */
    private Bound checkboxGroup(UiField node, FxRenderContext ctx) {
        var box = new VBox(6);
        box.getStyleClass().add("sui-choice-group");
        var checks = new ArrayList<CheckBox>();
        for (var choice : node.choicesInDisplayOrder()) {
            var check = new CheckBox(optionLabel(choice.option()));
            check.setUserData(choice.option().getValue());
            check.setSelected(choice.checked());
            onChanged(check.selectedProperty(), node, ctx);
            checks.add(check);
        }
        box.getChildren().setAll(checks);
        return new Bound(box, () -> checks.stream()
                .filter(CheckBox::isSelected)
                .map(c -> (String) c.getUserData())
                .toList());
    }

    /** How long move clicks must pause before a reorder is announced as a change. */
    static final Duration CHOICE_MOVE_SETTLE = Duration.millis(400);

    /**
     * Where a row of an orderable group belongs after its box was ticked or
     * unticked — the rule of {@code seatAfterToggle} in the web renderer's
     * {@code renderers/choices.ts}, kept identical so a re-render from the
     * server lands where the user already sees the row. Checked rows lead; a
     * tick already among them stays, otherwise it joins their end; an untick
     * returns to its option place among the unchecked rows.
     *
     * @param checked the rows' checked state as shown, the toggled row's new state included
     * @param index   the rows' positions in the field's options
     * @param at      the toggled row
     * @return the toggled row's index in the final order
     */
    static int seatAfterToggle(List<Boolean> checked, List<Integer> index, int at) {
        if (checked.get(at)) {
            for (int i = 0; i < at; i++) {
                if (!checked.get(i)) return (int) checked.stream().filter(c -> c).count() - 1;
            }
            return at;
        }
        int seat = 0;
        for (int i = 0; i < checked.size(); i++) {
            if (i != at && checked.get(i)) seat++;
        }
        int checkedOthers = seat;
        for (int i = 0, others = 0; i < checked.size(); i++) {
            if (i == at) continue;
            if (others++ < checkedOthers) continue;
            if (index.get(i) > index.get(at)) break;
            seat++;
        }
        return seat;
    }

    /**
     * An orderable MULTISELECT: checkboxes in rows with move-up / move-down
     * buttons, the checked rows first, submitted in the order shown. Follows
     * the web renderers' rules — {@link #seatAfterToggle} for ticks, moves only
     * within the checked block, focus kept on the pressed button unless it
     * just reached the edge, and one change per burst of moves.
     */
    private final class OrderableChoices {

        private record Row(HBox node, CheckBox check, Button up, Button down, int index) { }

        private final UiField node;
        private final FxRenderContext ctx;
        private final VBox box = new VBox(2);
        private final List<Row> rows = new ArrayList<>();
        private final javafx.animation.PauseTransition moved = new javafx.animation.PauseTransition(CHOICE_MOVE_SETTLE);

        OrderableChoices(UiField node, FxRenderContext ctx) {
            this.node = node;
            this.ctx = ctx;
            box.getStyleClass().add("sui-choice-group");
            moved.setOnFinished(e -> fireChange(node, ctx));
            // One tooltip per direction, installed on every button of the group.
            var upTip = new Tooltip("Move up");
            var downTip = new Tooltip("Move down");
            for (var choice : node.choicesInDisplayOrder()) {
                var check = new CheckBox(optionLabel(choice.option()));
                check.setUserData(choice.option().getValue());
                check.setSelected(choice.checked());
                var up = moveButton("chevron-up", "Move up", upTip);
                var down = moveButton("chevron-down", "Move down", downTip);
                var spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                var hbox = new HBox(4, check, spacer, up, down);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getStyleClass().add("sui-choice-row");
                var row = new Row(hbox, check, up, down, choice.index());
                rows.add(row);
                check.selectedProperty().addListener((obs, was, now) -> toggled(row));
                up.setOnAction(e -> move(row, -1));
                down.setOnAction(e -> move(row, +1));
            }
            box.getChildren().setAll(rows.stream().map(Row::node).toList());
            refreshMoves();
        }

        Bound bound() {
            return new Bound(box, () -> rows.stream()
                    .filter(r -> r.check().isSelected())
                    .map(r -> (String) r.check().getUserData())
                    .toList());
        }

        private void toggled(Row row) {
            int at = rows.indexOf(row);
            int seat = seatAfterToggle(
                    rows.stream().map(r -> r.check().isSelected()).toList(),
                    rows.stream().map(Row::index).toList(), at);
            if (seat != at) {
                // Taking the row out of the scene drops focus; hand it back so
                // the keyboard stays on the box that was just toggled.
                boolean focused = row.check().isFocused();
                rows.remove(at);
                rows.add(seat, row);
                box.getChildren().remove(row.node());
                box.getChildren().add(seat, row.node());
                if (focused) row.check().requestFocus();
            }
            refreshMoves();
            fireChange(node, ctx);
        }

        private void move(Row row, int direction) {
            int at = rows.indexOf(row);
            int other = at + direction;
            if (other < 0 || other >= rows.size()) return;
            var neighbour = rows.get(other);
            if (!row.check().isSelected() || !neighbour.check().isSelected()) return;
            // The neighbour moves, not the row, so the pressed button keeps focus.
            java.util.Collections.swap(rows, at, other);
            box.getChildren().remove(neighbour.node());
            box.getChildren().add(at, neighbour.node());
            refreshMoves();
            var pressed = direction < 0 ? row.up() : row.down();
            var opposite = direction < 0 ? row.down() : row.up();
            if (!pressed.isVisible()) (opposite.isVisible() ? opposite : row.check()).requestFocus();
            moved.playFromStart();
        }

        /** Shows a row's move buttons only where a move is possible. */
        private void refreshMoves() {
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                boolean checked = row.check().isSelected();
                boolean nextChecked = i + 1 < rows.size() && rows.get(i + 1).check().isSelected();
                row.up().setVisible(checked && i > 0);
                row.down().setVisible(checked && nextChecked);
            }
        }

        private Button moveButton(String icon, String label, Tooltip tooltip) {
            var button = new Button();
            Icons.lead(button, icon, ctx);
            if (button.getGraphic() == null) button.setText(label);
            button.setTooltip(tooltip);
            button.setAccessibleText(label);
            button.getStyleClass().add("sui-choice-move");
            return button;
        }
    }

    private Bound filePicker(UiField node, FxRenderContext ctx) {
        var picked = new ArrayList<File>();
        var chooser = new FileChooser();
        if (node.getAccept() != null) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(node.getAccept(), acceptGlobs(node.getAccept())));
        }

        var button = new javafx.scene.control.Button("Choose…");
        var chosen = new Label(node.getPlaceholder() == null ? "No file selected" : node.getPlaceholder());
        button.setOnAction(e -> {
            var window = button.getScene() == null ? null : button.getScene().getWindow();
            List<File> files = node.isMultiple()
                    ? chooser.showOpenMultipleDialog(window)
                    : single(chooser.showOpenDialog(window));
            if (files == null || files.isEmpty()) return;
            picked.clear();
            picked.addAll(files);
            chosen.setText(files.size() == 1
                    ? files.get(0).getName()
                    : files.size() + " files selected");
            // A file field's change carries the files themselves — that is what
            // an upload trigger or an INVOKE handler needs.
            if (node.getOnChange() != null) {
                ctx.bus().dispatch(node.getOnChange(), node, ctx, List.copyOf(picked));
            }
            if (node.isSubmitOnChange() && ctx.form() != null) ctx.form().submit();
        });

        var row = new HBox(8, button, chosen);
        row.setAlignment(Pos.CENTER_LEFT);
        return new Bound(row, () -> List.copyOf(picked));
    }

    // ── trigger wiring ────────────────────────────────────────────────────

    /**
     * Wires {@code onInput} (per keystroke) and {@code onChange} (on commit —
     * focus loss or Enter), which is the same split the web renderers use.
     */
    private void wireText(TextInputControl input, UiField node, FxRenderContext ctx) {
        if (node.getOnInput() != null) {
            input.textProperty().addListener((obs, old, now) ->
                    ctx.bus().dispatch(node.getOnInput(), node, ctx));
        }
        if (node.getOnChange() != null || node.isSubmitOnChange()) {
            input.focusedProperty().addListener((obs, was, focused) -> {
                if (was && !focused) fireChange(node, ctx);
            });
            if (input instanceof TextField field) {
                field.setOnAction(e -> fireChange(node, ctx));
            }
        }
    }

    /** Fires {@code onChange} when an observable value is committed. */
    private <T> void onChanged(ObservableValue<T> property, UiField node, FxRenderContext ctx) {
        property.addListener((obs, old, now) -> fireChange(node, ctx));
    }

    private void fireChange(UiField node, FxRenderContext ctx) {
        if (node.getOnChange() != null) ctx.bus().dispatch(node.getOnChange(), node, ctx);
        if (node.isSubmitOnChange() && ctx.form() != null) ctx.form().submit();
    }

    // ── value helpers ─────────────────────────────────────────────────────

    private static List<UiField.Option> options(UiField node) {
        return node.getOptions() == null ? List.of() : node.getOptions();
    }

    private static UiField.Option findOption(UiField node, Object value) {
        if (value == null) return null;
        var wanted = value.toString();
        return options(node).stream()
                .filter(o -> wanted.equals(o.getValue()))
                .findFirst()
                .orElse(null);
    }

    private static String optionLabel(UiField.Option option) {
        return SuiFxText.first(option.getLabel(), option.getValue());
    }

    private static StringConverter<UiField.Option> optionConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(UiField.Option option) {
                return option == null ? "" : optionLabel(option);
            }

            @Override
            public UiField.Option fromString(String string) {
                return null; // not editable — the combo picks from the list
            }
        };
    }

    private static LocalDate parseDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Turns an HTML-style {@code accept} list into FileChooser globs. Only the
     * extension form ({@code .pdf,.docx}) maps cleanly; MIME tokens like
     * {@code image/*} have no FileChooser equivalent and widen to everything.
     */
    private static List<String> acceptGlobs(String accept) {
        var globs = new ArrayList<String>();
        for (var token : accept.split(",")) {
            var t = token.trim();
            if (t.startsWith(".")) globs.add("*" + t);
        }
        return globs.isEmpty() ? List.of("*.*") : globs;
    }

    private static List<File> single(File file) {
        return file == null ? List.of() : List.of(file);
    }

    private static String display(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}
