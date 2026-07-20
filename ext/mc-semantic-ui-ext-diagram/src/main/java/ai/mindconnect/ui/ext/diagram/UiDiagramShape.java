package ai.mindconnect.ui.ext.diagram;

/**
 * Built-in shape identifiers known to the diagram renderer. These are the
 * <em>geometric primitives</em> the extension ships handlers for — kept
 * deliberately narrow and domain-agnostic so the extension stays reusable.
 *
 * <h2>Why constants, not an enum</h2>
 * {@link UiDiagramNode#shape} is a plain {@code String}, not an enum, so
 * consumers can register their own shape names at runtime via the
 * front-end registry ({@code registerShape()} in {@code extension.ts}).
 * Closing the type over a Java enum here would force every new shape
 * into a JAR rebuild even though the rendering side is already plug-able.
 *
 * <h2>Domain-specific shapes</h2>
 * Workflow-flavoured concepts like "decision", "fork", "event-start" are
 * <em>not</em> primitives — they're domain concepts that <em>map onto</em>
 * a primitive plus styling. Those live in
 * {@code mc-workflow-ui-diagram}'s {@code WorkflowShapes} helper, not here.
 *
 * <p>To render with one of these constants:
 * <pre>{@code
 *   var n = new UiDiagramNode();
 *   n.setShape(UiDiagramShape.ROUNDED_RECT);
 *   n.setShapeClass("wf-action");
 * }</pre>
 */
public final class UiDiagramShape {

    private UiDiagramShape() {}

    /** Rectangle with rounded corners. Generic "block" / "action" look. */
    public static final String ROUNDED_RECT = "rounded-rect";

    /** Sharp-cornered rectangle. Use for tables, raw containers, etc. */
    public static final String RECT = "rect";

    /** Circle inscribed in the node's bounding box (uses min(w, h)/2 as radius). */
    public static final String CIRCLE = "circle";

    /** Diamond / rhombus — used for decisions, merges, choice points. */
    public static final String DIAMOND = "diamond";

    /**
     * Thin horizontal (or vertical, depending on aspect ratio) bar.
     * Used for fork / join markers in process notations.
     */
    public static final String BAR = "bar";

    /**
     * Rounded-rect container with the label anchored to its top edge
     * (header band) instead of the centre. Use when the node wraps child
     * nodes — the centre is where the children draw, so a centred label
     * would overlap them. Used for if-branch and foreach-loop containers.
     */
    public static final String CONTAINER_WITH_HEADER = "container-with-header";
}
