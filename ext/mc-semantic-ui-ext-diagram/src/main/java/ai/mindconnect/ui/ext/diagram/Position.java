package ai.mindconnect.ui.ext.diagram;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 2D coordinate of the top-left corner of a {@link UiDiagramNode}, expressed
 * in the diagram's own coordinate space (origin top-left, units = pixels at
 * 1:1 zoom). Whoever builds the {@link UiDiagram} (the server) is expected to
 * fill positions in — the client renderer performs no layout in Etappe 1a.
 *
 * <p>Edges <em>don't</em> carry positions: the renderer always draws an edge
 * from the centre of its source node to the centre of its target node, with
 * a simple polyline routed around its own waypoints (none in 1a).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Position {
    private double x;
    private double y;

    public static Position of(double x, double y) {
        return new Position(x, y);
    }
}
