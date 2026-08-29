package ai.mindconnect.ui.javafx.icons;

import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SVG line art, rebuilt as JavaFX geometry.
 *
 * <p>The icon sprite got away with three assumptions, because every symbol in
 * it holds to them: shapes sit directly under the root, the box is always
 * {@code 0 0 24 24}, and the stroke is always 2. A document in the wild keeps
 * none of them — the case that prompted this is a brand logo whose paths are
 * in a group, whose box starts at {@code 160 50}, and whose stroke is 8.
 */
class SvgShapesTest {

    /** The shape of the logo that prompted this: a group, an offset box, its own stroke. */
    private static final String LOGO = """
            <svg width="370" height="240" viewBox="160 50 370 240" xmlns="http://www.w3.org/2000/svg">
              <title>MindConnect logo</title>
              <desc>A brain connected by a cable to a plug.</desc>
              <g fill="none" stroke="currentColor" stroke-width="8"
                 stroke-linecap="round" stroke-linejoin="round">
                <path d="M 200 150 C 170 120 175 80 220 80"/>
                <path d="M 300 100 L 340 140"/>
              </g>
            </svg>
            """;

    @Test
    void shapesInsideAGroupAreFound() {
        var document = SvgDocument.parse(LOGO);

        // The sprite never used a group, so descending had never come up — and
        // not descending finds nothing at all here.
        assertThat(document.shapes()).hasSize(2);
        assertThat(document.shapes()).allMatch(s -> s instanceof SVGPath);
    }

    @Test
    void titleAndDescAreNotShapes() {
        var document = SvgDocument.parse(LOGO);

        // Both are children of the root, and neither draws anything.
        assertThat(document.shapes()).hasSize(2);
    }

    @Test
    void theBoxIsReadFromTheDocument() {
        var document = SvgDocument.parse(LOGO);

        assertThat(document.minX()).isEqualTo(160);
        assertThat(document.minY()).isEqualTo(50);
        assertThat(document.width()).isEqualTo(370);
        assertThat(document.height()).isEqualTo(240);
        // Fitted on the longer side, so nothing is cut off.
        assertThat(document.scaleTo(37)).isEqualTo(0.1);
    }

    @Test
    void aGroupPassesItsStrokeToTheShapesInside() {
        var document = SvgDocument.parse(LOGO);

        // The width is on the <g>, not on any path — inherited as SVG defines.
        assertThat(document.shapes()).allMatch(s -> s.getStrokeWidth() == 8);
        // fill="none" on the group, so the caller's colour goes on the stroke.
        assertThat(document.shapes()).allMatch(s -> s.getFill() == null);
    }

    @Test
    void aFillOnTheShapeIsKept() {
        var document = SvgDocument.parse(
                "<svg viewBox=\"0 0 10 10\"><path d=\"M0 0 L10 10\" fill=\"#ff0000\"/></svg>");

        assertThat(document.shapes()).singleElement()
                .satisfies(s -> assertThat(s.getFill()).isEqualTo(javafx.scene.paint.Color.web("#ff0000")));
    }

    @Test
    void aDocumentWithoutAViewBoxFallsBackToItsSize() {
        var document = SvgDocument.parse(
                "<svg width=\"100\" height=\"50\"><path d=\"M0 0 L10 10\"/></svg>");

        assertThat(document.width()).isEqualTo(100);
        assertThat(document.height()).isEqualTo(50);
    }

    @Test
    void anythingUnreadableComesBackEmptyRatherThanHalfDrawn() {
        // A half-drawn logo is worse than an honest absence.
        assertThat(SvgDocument.parse("not xml at all").isEmpty()).isTrue();
        assertThat(SvgDocument.parse("").isEmpty()).isTrue();
        assertThat(SvgDocument.parse(null).isEmpty()).isTrue();
        // Valid SVG that draws nothing this understands.
        assertThat(SvgDocument.parse("<svg viewBox=\"0 0 1 1\"><text>hi</text></svg>").isEmpty()).isTrue();
    }
}
