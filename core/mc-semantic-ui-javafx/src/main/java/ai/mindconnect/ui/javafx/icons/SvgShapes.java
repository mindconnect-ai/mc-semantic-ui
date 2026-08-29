package ai.mindconnect.ui.javafx.icons;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds SVG line art as JavaFX shapes.
 *
 * <p>JavaFX draws raster images and its own geometry, and nothing in between:
 * there is no SVG support, and the libraries that add it bring a rendering
 * engine with them. But a large part of what an application actually shows as
 * SVG — icons, and a surprising number of logos — is line art: paths and a few
 * primitives, one colour, no gradients. That much maps onto the scene graph
 * one element for one shape, and the result is real geometry: it scales
 * without blurring and takes its colour from whatever draws it.
 *
 * <p><b>What is not supported</b>, and cannot cheaply be: gradients, text,
 * masks, clip paths, embedded CSS, {@code <use>} references, and filters. A
 * document using any of them will come out wrong or empty rather than
 * approximated — a half-drawn logo is worse than an honest absence.
 *
 * <p>Presentation attributes are inherited down the tree the way SVG defines,
 * so a {@code <g stroke="currentColor" stroke-width="8">} passes its stroke to
 * every path inside it. {@code currentColor} is left for the caller to resolve;
 * see {@link SuiFxIcon}.
 */
public final class SvgShapes {

    /** Stroke and fill as they stand at one point in the tree. */
    record Style(Double strokeWidth, boolean stroked, boolean filled, Paint fill) {

        static final Style ROOT = new Style(null, false, false, null);

        /** This style with {@code element}'s own presentation attributes over it. */
        Style with(Element element) {
            var width = attr(element, "stroke-width");
            var stroke = attr(element, "stroke");
            var fillAttr = attr(element, "fill");

            return new Style(
                    // Boxed deliberately: a ternary mixing Double and double
                    // unboxes both sides, so the inherited null would NPE
                    // rather than being carried through as "not set".
                    width == null ? strokeWidth : Double.valueOf(parse(width, strokeWidth)),
                    stroke == null ? stroked : !"none".equals(stroke),
                    fillAttr == null ? filled : !"none".equals(fillAttr),
                    fillAttr == null || "none".equals(fillAttr) || isKeyword(fillAttr)
                            ? fill : colour(fillAttr));
        }

        private static boolean isKeyword(String value) {
            // currentColor is the caller's to decide; anything else we take as
            // written, and give up quietly on what we cannot parse.
            return "currentColor".equals(value) || "inherit".equals(value);
        }

        private static Paint colour(String value) {
            try {
                return Color.web(value);
            } catch (IllegalArgumentException notAColour) {
                return null;
            }
        }
    }

    private SvgShapes() {
    }

    /**
     * Every shape under {@code root}, descending into groups.
     *
     * @param root the {@code <svg>}, {@code <symbol>} or {@code <g>} to walk
     */
    public static List<Shape> of(Element root) {
        var shapes = new ArrayList<Shape>();
        collect(root, Style.ROOT.with(root), shapes);
        return shapes;
    }

    private static void collect(Element parent, Style style, List<Shape> out) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element element)) continue;

            var own = style.with(element);
            if ("g".equals(element.getTagName())) {
                // A group carries style and nothing else; the icons never used
                // one, which is why descending had not come up before.
                collect(element, own, out);
                continue;
            }
            var shape = shape(element);
            if (shape != null) out.add(apply(shape, own));
        }
    }

    private static Shape apply(Shape shape, Style style) {
        if (style.strokeWidth() != null) shape.setStrokeWidth(style.strokeWidth());
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // A fill of its own wins; otherwise the shape stays unfilled and the
        // caller's colour goes on the stroke.
        shape.setFill(style.filled() ? style.fill() : null);
        return shape;
    }

    private static Shape shape(Element el) {
        return switch (el.getTagName()) {
            case "path"     -> path(attr(el, "d"));
            case "circle"   -> new Circle(num(el, "cx"), num(el, "cy"), num(el, "r"));
            case "ellipse"  -> new Ellipse(num(el, "cx"), num(el, "cy"), num(el, "rx"), num(el, "ry"));
            case "line"     -> new Line(num(el, "x1"), num(el, "y1"), num(el, "x2"), num(el, "y2"));
            case "rect"     -> rect(el);
            case "polyline" -> points(new Polyline(), el);
            case "polygon"  -> points(new Polygon(), el);
            default         -> null;   // title, desc, defs, anything unsupported
        };
    }

    private static Shape path(String d) {
        if (d == null || d.isBlank()) return null;
        var path = new SVGPath();
        path.setContent(d);
        return path;
    }

    private static Shape rect(Element el) {
        var rect = new Rectangle(num(el, "x"), num(el, "y"), num(el, "width"), num(el, "height"));
        // SVG rx/ry are radii; JavaFX arcWidth/arcHeight are diameters.
        double rx = num(el, "rx");
        double ry = el.hasAttribute("ry") ? num(el, "ry") : rx;
        rect.setArcWidth(rx * 2);
        rect.setArcHeight(ry * 2);
        return rect;
    }

    private static Shape points(Shape shape, Element el) {
        var raw = attr(el, "points");
        if (raw == null) return null;
        var coords = shape instanceof Polyline line ? line.getPoints() : ((Polygon) shape).getPoints();
        for (String part : raw.trim().split("[\\s,]+")) {
            if (!part.isBlank()) {
                try {
                    coords.add(Double.parseDouble(part));
                } catch (NumberFormatException skip) {
                    return null;
                }
            }
        }
        return coords.isEmpty() ? null : shape;
    }

    /** An attribute, or {@code null} when absent — the DOM returns "" for both. */
    private static String attr(Element el, String name) {
        return el.hasAttribute(name) ? el.getAttribute(name) : null;
    }

    static double num(Element el, String name) {
        return parse(attr(el, name), 0.0);
    }

    static double parse(String raw, Double fallback) {
        if (raw == null || raw.isBlank()) return fallback == null ? 0 : fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback == null ? 0 : fallback;
        }
    }
}
