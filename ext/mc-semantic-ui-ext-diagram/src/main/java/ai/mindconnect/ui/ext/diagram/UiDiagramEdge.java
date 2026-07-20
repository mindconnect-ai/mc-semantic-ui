package ai.mindconnect.ui.ext.diagram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

import java.util.List;

/**
 * Directed connection between two {@link UiDiagramNode}s. References nodes by
 * {@link #id} (not by index) so the wire form stays robust against client-side
 * reordering and future patch operations.
 *
 * <p>Edge classification matters for the future editor: {@link Kind#STRUCTURAL}
 * edges are dictated by the workflow's tree structure (e.g. decision → branch
 * → merge) and must not be deletable on their own; {@link Kind#FLOW} edges are
 * the normal sequencing arrows along a steps list and are the user-mutable
 * ones.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiDiagramEdge {

    public enum Kind {
        /** Sequential flow between consecutive steps. User-deletable. */
        FLOW("flow"),
        /** Forced by the workflow's structure (decision branches, fork/join). Not directly deletable. */
        STRUCTURAL("structural");

        private final String wire;
        Kind(String wire) { this.wire = wire; }
        @JsonValue public String wire() { return wire; }
    }

    /** Stable, unique identifier within the owning {@link UiDiagram}. */
    private String id;

    /** {@code id} of the source {@link UiDiagramNode}. */
    private String source;

    /** {@code id} of the target {@link UiDiagramNode}. */
    private String target;

    /** Optional label drawn near the edge (e.g. {@code "true"}/{@code "false"} for decisions). */
    private String label;

    /** Classification — defaults to {@link Kind#FLOW}. */
    private Kind kind = Kind.FLOW;

    /**
     * Container that owns this edge in the SVG transform tree — the closest
     * ancestor that contains both endpoints. {@code null} means top-level
     * (the edge sits in the diagram's root frame). The renderer emits the
     * edge inside this container's transform, so the edge rides along when
     * the container is dragged, and {@link #waypoints} are interpreted in
     * the same local frame.
     */
    private String ownerNodeId;

    /**
     * Intermediate waypoints, in the {@link #ownerNodeId owner container's}
     * local coordinate system. When null or empty the edge is drawn as a
     * straight line from source to target; otherwise the renderer threads a
     * polyline through these points before arriving at the target's
     * boundary.
     *
     * <p>Owner-local rather than absolute so a container drag moves the
     * waypoints along with the rest of its contents through SVG transform
     * inheritance — no separate per-waypoint bookkeeping needed.
     */
    private List<Position> waypoints;

    public static UiDiagramEdge flow(String id, String source, String target) {
        var e = new UiDiagramEdge();
        e.id = id;
        e.source = source;
        e.target = target;
        e.kind = Kind.FLOW;
        return e;
    }

    public static UiDiagramEdge structural(String id, String source, String target, String label) {
        var e = new UiDiagramEdge();
        e.id = id;
        e.source = source;
        e.target = target;
        e.label = label;
        e.kind = Kind.STRUCTURAL;
        return e;
    }
}
