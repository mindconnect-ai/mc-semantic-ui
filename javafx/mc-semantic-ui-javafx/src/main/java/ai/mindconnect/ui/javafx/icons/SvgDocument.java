package ai.mindconnect.ui.javafx.icons;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javafx.scene.shape.Shape;

/**
 * One parsed SVG: its shapes and the box they are drawn in.
 *
 * <p>The box matters as much as the shapes. An icon sprite's symbols all share
 * {@code viewBox="0 0 24 24"}, so the resolver could scale by a single divisor
 * and never think about it. A document in the wild does not: a logo may be
 * {@code viewBox="160 50 370 240"} — an origin away from zero, and not square.
 * Ignoring that draws the picture in the wrong place at the wrong aspect, which
 * looks like the parser failing rather than the caller mis-scaling.
 */
public record SvgDocument(List<Shape> shapes, double minX, double minY,
                          double width, double height) {

    /** A square 24 box, which is what every symbol in the icon sprite uses. */
    public static final SvgDocument EMPTY = new SvgDocument(List.of(), 0, 0, 24, 24);

    /** Parses {@code markup}, or {@link #EMPTY} when it cannot be read. */
    public static SvgDocument parse(String markup) {
        if (markup == null || markup.isBlank()) return EMPTY;
        try (InputStream in = new ByteArrayInputStream(markup.getBytes(StandardCharsets.UTF_8))) {
            var factory = DocumentBuilderFactory.newInstance();
            // These documents come from our own jar or the host's own server,
            // but refusing external entities costs nothing.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            var root = factory.newDocumentBuilder().parse(in).getDocumentElement();
            return of(root);
        } catch (Exception notSvg) {
            return EMPTY;
        }
    }

    /** Reads {@code root}'s own box, falling back to width/height, then to 24. */
    public static SvgDocument of(Element root) {
        var shapes = SvgShapes.of(root);
        var box = viewBox(root);
        if (box != null) return new SvgDocument(shapes, box[0], box[1], box[2], box[3]);

        var width = SvgShapes.num(root, "width");
        var height = SvgShapes.num(root, "height");
        if (width > 0 && height > 0) return new SvgDocument(shapes, 0, 0, width, height);
        return new SvgDocument(shapes, 0, 0, 24, 24);
    }

    private static double[] viewBox(Element root) {
        if (!root.hasAttribute("viewBox")) return null;
        var parts = root.getAttribute("viewBox").trim().split("[\\s,]+");
        if (parts.length != 4) return null;
        try {
            var box = new double[4];
            for (int i = 0; i < 4; i++) box[i] = Double.parseDouble(parts[i]);
            return box[2] > 0 && box[3] > 0 ? box : null;
        } catch (NumberFormatException notNumbers) {
            return null;
        }
    }

    /** The scale that makes this fit a {@code size}-by-{@code size} square. */
    public double scaleTo(double size) {
        return size / Math.max(width, height);
    }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }
}
