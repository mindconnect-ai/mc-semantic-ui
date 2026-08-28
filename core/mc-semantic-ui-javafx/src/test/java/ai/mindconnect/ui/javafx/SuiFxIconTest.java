package ai.mindconnect.ui.javafx;

import ai.mindconnect.ui.javafx.icons.SpriteIconResolver;
import ai.mindconnect.ui.javafx.icons.SuiFxIcon;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sprite resolver builds no scene graph of its own, so these run without
 * an FX thread — only {@link SuiFxIcon#inherit} needs a live control, and that
 * is covered over in {@code SuiFxRendererTest}.
 */
class SuiFxIconTest {

    private final SpriteIconResolver resolver = new SpriteIconResolver();

    @Test
    void everySymbolInTheSpriteRebuildsAsShapes() {
        var names = resolver.names();
        assertThat(names).hasSizeGreaterThan(2000);

        var unbuildable = names.stream()
                .filter(n -> {
                    var icon = resolver.resolve(n);
                    return icon == null || icon.getChildrenUnmodifiable().isEmpty();
                })
                .toList();

        // The whole Lucide set, not a curated subset: if a future sprite adds
        // an SVG primitive this resolver has no mapping for, this is what says
        // so — and names the icons that came out blank.
        assertThat(unbuildable).isEmpty();
    }

    @Test
    void aPathOnlySymbolBecomesSvgPaths() {
        var icon = resolver.resolve("brain");

        assertThat(icon).isNotNull();
        assertThat(icon.getChildrenUnmodifiable()).isNotEmpty();
        assertThat(icon.getChildrenUnmodifiable()).allMatch(n -> n instanceof SVGPath);
    }

    @Test
    void theOtherSvgPrimitivesMapOntoTheirJavaFxCounterparts() {
        // alarm-clock draws its face with <circle>, and <line> hands.
        assertThat(resolver.resolve("alarm-clock").getChildrenUnmodifiable())
                .anyMatch(n -> n instanceof Circle);
        // A rounded <rect> carries rx; JavaFX wants the diameter, not the radius.
        var square = resolver.resolve("square");
        var rect = (Rectangle) square.getChildrenUnmodifiable().stream()
                .filter(n -> n instanceof Rectangle).findFirst().orElseThrow();
        assertThat(rect.getArcWidth()).isEqualTo(rect.getArcHeight());
        assertThat(rect.getWidth()).isPositive();
    }

    @Test
    void shapesAreStrokedNotFilled() {
        var icon = resolver.resolve("trash-2");

        // Lucide is a stroked set — a filled glyph would come out as a blob.
        assertThat(icon.getChildrenUnmodifiable()).allMatch(n ->
                ((javafx.scene.shape.Shape) n).getFill() == null
                        && ((javafx.scene.shape.Shape) n).getStrokeWidth() == SuiFxIcon.STROKE_WIDTH);
    }

    @Test
    void colourDrivesEveryShapeAtOnce() {
        var icon = resolver.resolve("brain");
        icon.setColor(javafx.scene.paint.Color.RED);

        assertThat(icon.getChildrenUnmodifiable()).allMatch(n ->
                javafx.scene.paint.Color.RED.equals(((javafx.scene.shape.Shape) n).getStroke()));
    }

    @Test
    void sizeScalesTheSharedViewBox() {
        var icon = resolver.resolve("brain");
        icon.setSize(48);

        // Every symbol is drawn on a 24×24 grid, so 48px is exactly 2×.
        assertThat(icon.getTransforms()).singleElement()
                .satisfies(t -> assertThat(((javafx.scene.transform.Scale) t).getX()).isEqualTo(2.0));
    }

    @Test
    void anUnknownTokenResolvesToNothingRatherThanFailing() {
        assertThat(resolver.resolve("no-such-icon-anywhere")).isNull();
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("  ")).isNull();
    }

    @Test
    void aMissingSpriteCostsGlyphsNotTheWindow() {
        var broken = new SpriteIconResolver("/nowhere/icons.svg");

        assertThat(broken.names()).isEmpty();
        assertThat(broken.resolve("brain")).isNull();
    }
}
