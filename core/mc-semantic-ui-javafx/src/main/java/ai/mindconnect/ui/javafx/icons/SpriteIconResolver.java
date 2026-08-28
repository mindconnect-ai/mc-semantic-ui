package ai.mindconnect.ui.javafx.icons;

import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default {@link FxIconResolver}: reads the very sprite the browser loads,
 * {@code icons.svg}, and rebuilds its symbols as JavaFX shapes.
 *
 * <p>That the desktop and the browser draw from one file is the point — an
 * icon token means the same glyph in all three renderers, and adding an icon
 * to the sprite lights it up everywhere at once.
 *
 * <p>The sprite is ~670 KB of markup, so it is read once, lazily, into a map of
 * symbol id to its inner markup. Only the requested symbol is ever parsed into
 * shapes, and each call builds fresh ones — a JavaFX node has a single parent,
 * so cached shapes could not be reused across the tree anyway.
 */
public class SpriteIconResolver implements FxIconResolver {

    /** Where core's jar puts the sprite; the same file Spring serves at /sui/icons.svg. */
    public static final String DEFAULT_SPRITE = "/META-INF/resources/sui/icons.svg";

    // Pulled apart with a scan rather than a DOM: holding 2000+ parsed symbols
    // would cost megabytes to answer a handful of lookups.
    private static final Pattern SYMBOL =
            Pattern.compile("<symbol\\s+id=\"([^\"]+)\"[^>]*>(.*?)</symbol>", Pattern.DOTALL);

    private final String spritePath;
    private volatile Map<String, String> symbols;

    public SpriteIconResolver() {
        this(DEFAULT_SPRITE);
    }

    public SpriteIconResolver(String spritePath) {
        this.spritePath = spritePath;
    }

    @Override
    public SuiFxIcon resolve(String name) {
        if (name == null || name.isBlank()) return null;
        var markup = symbols().get(name.trim());
        if (markup == null) return null;
        var shapes = shapes(markup);
        return shapes.isEmpty() ? null : new SuiFxIcon(name, shapes);
    }

    /** The tokens this sprite carries — useful for a picker, and for tests. */
    public java.util.Set<String> names() {
        return java.util.Collections.unmodifiableSet(symbols().keySet());
    }

    private Map<String, String> symbols() {
        var loaded = symbols;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (symbols == null) symbols = load();
            return symbols;
        }
    }

    private Map<String, String> load() {
        try (InputStream in = SpriteIconResolver.class.getResourceAsStream(spritePath)) {
            if (in == null) return Map.of();
            var svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> found = new HashMap<>();
            Matcher m = SYMBOL.matcher(svg);
            while (m.find()) found.put(m.group(1), m.group(2));
            return Map.copyOf(found);
        } catch (IOException e) {
            // A missing or unreadable sprite costs glyphs, not the window.
            return Map.of();
        }
    }

    /**
     * Rebuilds one symbol's body as shapes. Lucide draws with seven SVG
     * primitives and JavaFX has all of them, so nothing here approximates:
     * {@code path} is handed to {@link SVGPath} verbatim, the rest map across
     * one for one.
     */
    private List<Shape> shapes(String markup) {
        List<Shape> shapes = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // Sprite is a build artefact from our own repo, but it costs
            // nothing to refuse external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                    ("<g>" + markup + "</g>").getBytes(StandardCharsets.UTF_8)));
            NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element el) {
                    Shape shape = shape(el);
                    if (shape != null) shapes.add(shape);
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return shapes;
    }

    private Shape shape(Element el) {
        return switch (el.getTagName()) {
            case "path"     -> svgPath(el.getAttribute("d"));
            case "circle"   -> new Circle(num(el, "cx"), num(el, "cy"), num(el, "r"));
            case "ellipse"  -> new Ellipse(num(el, "cx"), num(el, "cy"), num(el, "rx"), num(el, "ry"));
            case "line"     -> new Line(num(el, "x1"), num(el, "y1"), num(el, "x2"), num(el, "y2"));
            case "rect"     -> rect(el);
            case "polyline" -> points(new Polyline(), el);
            case "polygon"  -> points(new Polygon(), el);
            default         -> null;
        };
    }

    private Shape svgPath(String d) {
        if (d == null || d.isBlank()) return null;
        var path = new SVGPath();
        path.setContent(d);
        return path;
    }

    private Shape rect(Element el) {
        var rect = new Rectangle(num(el, "x"), num(el, "y"), num(el, "width"), num(el, "height"));
        // SVG rx/ry are radii; JavaFX arcWidth/arcHeight are diameters.
        double rx = num(el, "rx");
        double ry = el.hasAttribute("ry") ? num(el, "ry") : rx;
        rect.setArcWidth(rx * 2);
        rect.setArcHeight(ry * 2);
        return rect;
    }

    private Shape points(Shape shape, Element el) {
        var raw = el.getAttribute("points");
        if (raw == null || raw.isBlank()) return null;
        var coords = shape instanceof Polyline line ? line.getPoints() : ((Polygon) shape).getPoints();
        for (String part : raw.trim().split("[\\s,]+")) {
            if (!part.isBlank()) coords.add(Double.parseDouble(part));
        }
        return coords.isEmpty() ? null : shape;
    }

    private double num(Element el, String attr) {
        var raw = el.getAttribute(attr);
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
