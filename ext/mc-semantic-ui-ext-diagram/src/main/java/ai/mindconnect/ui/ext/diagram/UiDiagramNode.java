package ai.mindconnect.ui.ext.diagram;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One vertex of a {@link UiDiagram}. Note that {@code UiDiagramNode} is
 * <em>not</em> a {@code UiNode} subtype: it is an internal piece of a diagram
 * and never rendered at the top level of the SUI tree.
 *
 * <h2>Shape vs. styling</h2>
 * {@link #shape} picks the <em>geometry</em> (rectangle, circle, diamond)
 * and is constrained to identifiers the front-end renderer knows about
 * (see {@link UiDiagramShape} for the built-in primitives, or any name a
 * consumer registered via the JS {@code registerShape()} API). Everything
 * domain-specific — colour, marker, "double border" — is layered on top
 * via {@link #shapeClass}, {@link #marker}, and {@link #borderWidth}.
 *
 * <p>Two flavours coexist in the same list, distinguished by {@link #synthetic}:
 * <ul>
 *   <li><b>Source nodes</b> ({@code synthetic = false}) — back a real
 *       domain object. {@link #stepRef} (or the equivalent in non-workflow
 *       domains) carries the unique reference so editors can resolve clicks.</li>
 *   <li><b>Synthetic nodes</b> ({@code synthetic = true}) — render-time
 *       scaffolding (start, end, merge, fork, join). Editors must treat
 *       these as non-deletable.</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiDiagramNode {

    /** Stable, unique identifier within the owning {@link UiDiagram}. */
    private String id;

    /** Visible text label (e.g. step name, condition expression). */
    private String label;

    /**
     * Shape identifier — one of the built-in primitives from
     * {@link UiDiagramShape} or any name a consumer registered on the
     * front-end via {@code registerShape()}. Stays a free {@code String}
     * (rather than an enum) so consumers can add shapes without forcing a
     * JAR rebuild of this module.
     */
    private String shape;

    /**
     * Optional extra CSS class applied to the {@code <g>} that wraps the
     * shape. Use this for domain-level styling (fill colour, dashed border,
     * etc.) without the renderer having to know about the domain. The
     * built-in primitives don't ship colour, only structure — so a
     * "decision" diamond gets its yellow fill from a {@code wf-decision}
     * class living in the workflow module, not from here.
     */
    private String shapeClass;

    /**
     * Single-character (or short-text) badge drawn in the bottom-right
     * corner of the shape. Used by the workflow renderer for the "+" on
     * a collapsed subprocess; could carry a check mark, a number, etc.
     * Null means no marker.
     */
    private String marker;

    /**
     * Optional stroke width override for the shape outline. Used to express
     * "double-bordered" looks (e.g. workflow call activities) without
     * needing a dedicated shape primitive. Null leaves the renderer's
     * default (typically 1.5).
     */
    private Integer borderWidth;

    /**
     * Top-left corner of the rendered shape, expressed in the
     * <em>parent's</em> coordinate system: for a top-level node (one of
     * {@code UiDiagram.nodes}) this is the diagram-absolute position; for a
     * child node (one of some other node's {@link #children}) it is the
     * offset from that parent's top-left corner. The renderer composes
     * absolute positions by walking the parent chain.
     */
    private Position position;

    /**
     * Optional explicit width. When set, overrides the shape registry's
     * default size — used by containers (which must size to fit their
     * children) and any consumer that wants a custom-sized shape.
     * Null falls back to the shape's registered default size.
     */
    private Integer width;

    /** Optional explicit height. Same semantics as {@link #width}. */
    private Integer height;

    /**
     * Nested child nodes, rendered inside this node's bounding box. Each
     * child's {@link #position} is interpreted relative to this node's
     * top-left corner, not the diagram origin.
     *
     * <p>A node with {@code children} is a <em>container</em>: it draws its
     * own shape first (the "frame") and the children on top. The graph's
     * edges (which live at {@link UiDiagram#edges} top-level) can connect
     * any two nodes in the tree regardless of nesting — edges crossing
     * container boundaries are fine.
     *
     * <p>Null or empty means this node is a leaf.
     */
    private List<UiDiagramNode> children;

    /**
     * When {@code true}, this node was introduced by the builder for layout
     * reasons (start, end, merge, fork, join) and has no backing domain
     * object. Editors must not offer "delete" / "edit properties" on
     * synthetic nodes.
     */
    private boolean synthetic;

    /**
     * Domain reference this node represents. Only set when {@link #synthetic}
     * is {@code false}. For workflows this is the step name; other domains
     * may use whatever string handle makes sense for their editor.
     */
    private String stepRef;

    /**
     * Free-form metadata propagated verbatim to the client. Used today to
     * carry the step type (e.g. {@code "stepType": "code"}) so the editor can
     * pick the right property form. Kept as a string map to stay schema-free.
     */
    private Map<String, String> data;

    // ── Capability flags ───────────────────────────────────────────────────
    //
    // Explicit booleans the server uses to tell the editor which gestures
    // this node accepts. Default to true so a plain step (just constructed,
    // no decoration) behaves like a normal editable node; the builder
    // flips them off for scaffolding (start/end markers, branch frames,
    // if's join, the if's diamond itself, etc). Keeping the policy on the
    // node — instead of pattern-matching on stepRef prefixes / synthetic
    // flag / shape — means new node kinds slot in without touching the
    // editor's hit-test code paths.

    /** True if a click on this node should select it in the editor. */
    private boolean canSelect = true;
    /** True if the user can drag this node to reposition it. */
    private boolean canDrag = true;
    /** True if the editor should expose an "insert step after this" gesture
     *  (the plus button below the node). */
    private boolean canInsertAfter = true;
    /** True if the editor should expose a "delete this step" gesture
     *  (the × button at the top-right of the node). */
    private boolean canDelete = true;

    // ── Convenience setters ────────────────────────────────────────────────

    public UiDiagramNode put(String key, String value) {
        if (this.data == null) {
            this.data = new LinkedHashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    /** Sets {@link #shapeClass} and returns this for fluent chaining. */
    public UiDiagramNode withShapeClass(String cls) {
        this.shapeClass = cls;
        return this;
    }

    /** Sets {@link #marker} and returns this for fluent chaining. */
    public UiDiagramNode withMarker(String marker) {
        this.marker = marker;
        return this;
    }

    /** Sets {@link #borderWidth} and returns this for fluent chaining. */
    public UiDiagramNode withBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    /** Sets {@link #width} + {@link #height} together and returns this. */
    public UiDiagramNode withSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /** Adds a child node and returns this for fluent chaining. */
    public UiDiagramNode addChild(UiDiagramNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
        return this;
    }

    /**
     * Factory shortcut: {@code id}, {@code shape}, {@code label} in one call.
     * The shape is a free string — pass a {@link UiDiagramShape} constant
     * for the built-ins.
     */
    public static UiDiagramNode of(String id, String shape, String label) {
        var n = new UiDiagramNode();
        n.id = id;
        n.shape = shape;
        n.label = label;
        return n;
    }
}
