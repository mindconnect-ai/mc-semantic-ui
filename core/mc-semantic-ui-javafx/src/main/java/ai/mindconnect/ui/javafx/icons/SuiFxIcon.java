package ai.mindconnect.ui.javafx.icons;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.util.List;

/**
 * One painted icon: the sprite's shapes in a {@link Group}, scaled from the
 * 24×24 viewBox every symbol shares down to {@link #sizeProperty()}.
 *
 * <p>The web icon is {@code 1em} in {@code currentColor} — it inherits the
 * surrounding text's size and colour. JavaFX has no such inheritance, so the
 * two properties here stand in for it and {@link #inherit(Labeled)} wires them
 * to a control's font and text fill. Use it wherever an icon sits beside a
 * label, and the glyph then tracks the label through hover, disable and theme
 * changes instead of freezing at whatever it was built with.
 */
public final class SuiFxIcon extends Group {

    /** Every Lucide symbol is drawn on this grid, so scaling is a single divisor. */
    public static final double VIEWBOX = 24;

    /** Stroke width in viewBox units, straight from the sprite's symbols. */
    public static final double STROKE_WIDTH = 2;

    private static final double DEFAULT_SIZE = 16;

    /** -sui-text-body, so a standalone icon is legible before anyone styles it. */
    private static final Paint DEFAULT_COLOR = Color.web("#334155");

    private final List<Shape> shapes;
    private final ObjectProperty<Paint> color = new SimpleObjectProperty<>(this, "color", DEFAULT_COLOR);
    private final DoubleProperty size = new SimpleDoubleProperty(this, "size", DEFAULT_SIZE);
    private final Scale scale = new Scale();

    SuiFxIcon(String name, List<Shape> shapes) {
        this.shapes = List.copyOf(shapes);
        getStyleClass().add("sui-icon");
        getProperties().put("sui.icon", name);

        for (Shape shape : this.shapes) {
            // Lucide is a stroked set: no fills, round caps and joins. The
            // sprite says so on every symbol; say it once here instead of
            // parsing it back off each element.
            shape.setFill(null);
            shape.setStrokeWidth(STROKE_WIDTH);
            shape.setStrokeLineCap(StrokeLineCap.ROUND);
            shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
            shape.strokeProperty().bind(color);
        }
        getChildren().addAll(this.shapes);

        scale.xProperty().bind(size.divide(VIEWBOX));
        scale.yProperty().bind(size.divide(VIEWBOX));
        getTransforms().add(scale);
        setMouseTransparent(true);
    }

    /** The stroke colour — the stand-in for {@code currentColor}. */
    public ObjectProperty<Paint> colorProperty() {
        return color;
    }

    public void setColor(Paint paint) {
        color.set(paint);
    }

    public Paint getColor() {
        return color.get();
    }

    /** Edge length in pixels — the stand-in for {@code 1em}. */
    public DoubleProperty sizeProperty() {
        return size;
    }

    public void setSize(double px) {
        size.set(px);
    }

    public double getSize() {
        return size.get();
    }

    /**
     * Ties this icon to the control it decorates, the way {@code 1em} and
     * {@code currentColor} tie the web icon to its surroundings: size follows
     * the control's font, colour follows its text fill.
     *
     * @return this icon, so it can be handed straight to {@code setGraphic}
     */
    public SuiFxIcon inherit(Labeled owner) {
        if (owner == null) return this;
        size.bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> owner.getFont() == null ? DEFAULT_SIZE : owner.getFont().getSize(),
                owner.fontProperty()));
        color.bind(owner.textFillProperty());
        return this;
    }
}
