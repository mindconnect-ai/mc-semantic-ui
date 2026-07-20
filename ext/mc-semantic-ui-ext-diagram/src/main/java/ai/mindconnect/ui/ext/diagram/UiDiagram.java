package ai.mindconnect.ui.ext.diagram;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer node for displaying a directed graph as an interactive SVG canvas.
 * The primary use case in this codebase is rendering {@code WorkflowData} as
 * an activity diagram, but the node is intentionally domain-agnostic — any
 * server-side producer can build a {@code UiDiagram}.
 *
 * <p>Lives in its <em>own</em> extension module rather than
 * the smaller extensions because the editor pipeline this module
 * grows into (Phase 2 + 3) brings substantial extra weight: ELK layout,
 * Web-Component event plumbing, and a domain-specific patch type. Co-locating
 * it with the small markdown / jsonviewer extensions would dilute that
 * module's "tiny third-party-backed renderers" focus.
 *
 * <p>Wire shape:
 * <pre>{@code
 * {
 *   "type": "diagram",
 *   "id": "wf-42",
 *   "width": 800,
 *   "height": 600,
 *   "nodes": [ {"id": "n1", "shape": "event-start", "position": {...}}, ... ],
 *   "edges": [ {"id": "e1", "source": "n1", "target": "n2", "kind": "flow"}, ... ]
 * }
 * }</pre>
 *
 * <p>The subtype is registered with Jackson via {@link UiDiagramModule}, which
 * Spring Boot picks up automatically through {@link UiDiagramAutoConfiguration};
 * non-Spring consumers can call {@code mapper.findAndRegisterModules()}
 * to register it via {@code ServiceLoader}.
 *
 * <p>The matching front-end handler lives at {@code src/main/ts/diagram/extension.ts}
 * in the same module and is loaded by the host page via
 * {@code import("/sui-ext/diagram/extension.js")}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("diagram")
public class UiDiagram extends UiNode {

    /** Canvas dimensions in pixels. The client uses these for the SVG viewBox. */
    private Integer width;
    private Integer height;

    /** Vertices of the graph. Order is irrelevant for rendering. */
    private List<UiDiagramNode> nodes = new ArrayList<>();

    /** Directed edges. Reference nodes by {@link UiDiagramNode#getId()}. */
    private List<UiDiagramEdge> edges = new ArrayList<>();

    public UiDiagram addNode(UiDiagramNode node) {
        this.nodes.add(node);
        return this;
    }

    public UiDiagram addEdge(UiDiagramEdge edge) {
        this.edges.add(edge);
        return this;
    }
}
