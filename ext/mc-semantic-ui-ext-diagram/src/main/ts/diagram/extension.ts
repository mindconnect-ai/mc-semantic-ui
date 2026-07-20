import type { SuiRenderer } from "/sui/renderer.js";

// escapeHtml is inlined (not imported from the core bundle) so the compiled
// extension.js has NO runtime import of /sui/renderer.js. That keeps the bundle
// fully portable: it works when loaded from a CDN or under a path prefix
// (GitHub Pages' /mc-semantic-ui/…), where an absolute /sui/ import would
// resolve against the wrong origin. The `import type` above is erased at
// compile time, so it adds no runtime dependency. Mirrors core's escapeHtml.
const HTML_ESCAPE: Record<string, string> =
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
function escapeHtml(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE[ch]!);
}

/**
 * Wire shape of the {@code diagram} node, mirroring the Java {@code UiDiagram}
 * class. The extension declares the shape inline because the renderer is the
 * only consumer — there is no benefit to a separate shared types module.
 */
interface UiDiagramNodeWire {
    id: string;
    label?: string;
    shape: string;                 // see UiDiagramShape on the Java side, or a custom name
    shapeClass?: string;           // extra CSS class for domain styling
    marker?: string;               // single-char badge in the corner ("+", "✓", …)
    borderWidth?: number;          // override outline stroke-width
    width?: number;                // optional explicit width (containers, custom-sized shapes)
    height?: number;               // optional explicit height
    // Position is relative to the parent node's top-left for nested children;
    // absolute (diagram-coordinate-system) for top-level UiDiagram.nodes.
    position?: { x: number; y: number };
    synthetic?: boolean;
    stepRef?: string;
    data?: Record<string, string>;
    // Capability flags from the server. Default to true when missing so a
    // builder that doesn't know about them still gets sensible-step
    // behaviour; the workflow builder sets them per-node to disable
    // gestures on scaffolding (start/end/branch/merge/etc).
    canSelect?: boolean;
    canDrag?: boolean;
    canInsertAfter?: boolean;
    canDelete?: boolean;
    // Nested children — rendered inside this node's bounding box, with their
    // own positions interpreted relative to this node's top-left corner.
    children?: UiDiagramNodeWire[];
}

interface UiDiagramEdgeWire {
    id: string;
    source: string;
    target: string;
    label?: string;
    kind?: "flow" | "structural";
    /**
     * Container node that owns this edge — the closest ancestor that holds
     * both endpoints in its subtree. Null/undefined means top-level. The
     * renderer emits the edge inside this container's <g transform> so it
     * rides along when the container moves, and {@link waypoints} are
     * interpreted in the same local frame.
     */
    ownerNodeId?: string | null;
    /**
     * Optional intermediate waypoints in the owner container's local
     * coordinate system (top-level when ownerNodeId is null). When present,
     * the edge is drawn as a polyline source → wp[0] → wp[1] → … → target
     * instead of a straight line.
     */
    waypoints?: Array<{ x: number; y: number }>;
}

interface UiDiagramWire {
    type: "diagram";
    id: string;
    title?: string;
    cssClass?: string;
    width?: number;
    height?: number;
    nodes: UiDiagramNodeWire[];
    edges: UiDiagramEdgeWire[];
}

// ── Shape registry ──────────────────────────────────────────────────────────

/**
 * Rendering context passed to a {@link ShapeRenderer}. Carries the
 * geometry (width / height) and the already-HTML-escaped label, plus any
 * per-node styling overrides the node carried on the wire.
 *
 * <p>Renderers should treat {@code label} as pre-escaped — passing it into
 * the returned markup is safe but re-escaping it would double-encode
 * entities.
 */
export interface ShapeRenderContext {
    w: number;
    h: number;
    label: string;
    borderWidth?: number;
}

/**
 * Produces the inner SVG for one shape. Output is concatenated into the
 * surrounding {@code <g transform="translate(...)">…</g>} that
 * {@link renderNodeSvg} emits, so the renderer should draw at local
 * origin {@code (0, 0)}.
 */
export type ShapeRenderer = (ctx: ShapeRenderContext) => string;

/** Default size used when a shape's registration didn't supply one. */
const DEFAULT_SIZE = { w: 120, h: 48 };

const SHAPE_SIZES = new Map<string, { w: number; h: number }>();
const SHAPE_RENDERERS = new Map<string, ShapeRenderer>();

/**
 * Registers (or replaces) a shape handler. Call this from consumer code
 * before any diagrams are rendered to add domain-specific primitives.
 *
 * <p>The built-in primitives ({@code rect}, {@code rounded-rect},
 * {@code circle}, {@code diamond}, {@code bar}) are installed
 * automatically the first time {@link install} runs.
 *
 * @param name  shape identifier matching what the server emits in
 *              {@code UiDiagramNode.shape}
 * @param size  default size (width × height in diagram coordinates)
 * @param renderer  function that produces the SVG fragment for this shape
 */
export function registerShape(name: string,
                              size: { w: number; h: number },
                              renderer: ShapeRenderer): void {
    SHAPE_SIZES.set(name, size);
    SHAPE_RENDERERS.set(name, renderer);
}

function sizeOf(shape: string): { w: number; h: number } {
    return SHAPE_SIZES.get(shape) ?? DEFAULT_SIZE;
}

/**
 * Effective size of a node, considering per-node {@code width}/{@code height}
 * overrides. Used for containers (where the size depends on the children,
 * not on the shape's default) and any consumer wanting a custom-sized box.
 */
function nodeSize(n: UiDiagramNodeWire): { w: number; h: number } {
    const def = sizeOf(n.shape);
    return {
        w: n.width  ?? def.w,
        h: n.height ?? def.h,
    };
}

/**
 * Installs the geometric primitives this module ships with. Idempotent —
 * called from {@link install} but safe to invoke explicitly too.
 */
function installDefaultShapes(): void {
    const tBase = `text-anchor="middle" dominant-baseline="middle"`;
    const labelAt = (cx: number, cy: number, label: string) =>
        `<text x="${cx}" y="${cy}" ${tBase} class="sui-diagram-label">${label}</text>`;
    const strokeAttr = (bw?: number) => bw ? ` stroke-width="${bw}"` : "";

    /**
     * Renders a (possibly multi-line) label anchored to the top edge of a
     * rect-style shape. First line is the name in bold; subsequent lines
     * (anything after a "\n" in the supplied label) are rendered smaller
     * and muted — that's where shape-builders put the step-type detail
     * (HTTP method, code language, etc.). Lines are emitted as <tspan>s
     * with explicit dy so SVG actually breaks them; a plain <text> ignores
     * "\n" regardless of white-space rules.
     */
    const headerLabel = (w: number, label: string) => {
        if (!label) return "";
        const lines = label.split("\n");
        const head = `<tspan x="${w / 2}" dy="0" class="sui-diagram-label--header-line">${lines[0]}</tspan>`;
        const rest = lines.slice(1).map(line =>
            `<tspan x="${w / 2}" dy="1.2em" class="sui-diagram-label--detail">${line}</tspan>`
        ).join("");
        // y=18 sits the first line on the same top-band the
        // container-with-header shape uses, so steps and containers line up.
        return `<text x="${w / 2}" y="18" text-anchor="middle" `
             + `dominant-baseline="middle" class="sui-diagram-label">${head}${rest}</text>`;
    };

    registerShape("rounded-rect", { w: 160, h: 56 }, ({ w, h, label, borderWidth }) =>
        `<rect x="0" y="0" width="${w}" height="${h}" rx="8" ry="8" `
        + `class="sui-diagram-shape sui-diagram-shape--rounded-rect"${strokeAttr(borderWidth)}/>`
        + headerLabel(w, label));

    registerShape("rect", { w: 160, h: 56 }, ({ w, h, label, borderWidth }) =>
        `<rect x="0" y="0" width="${w}" height="${h}" `
        + `class="sui-diagram-shape sui-diagram-shape--rect"${strokeAttr(borderWidth)}/>`
        + headerLabel(w, label));

    // Circle: inscribed in the bounding box; label sits below so it can grow
    // beyond the circle's diameter without spilling onto the curve.
    registerShape("circle", { w: 48, h: 48 }, ({ w, h, label, borderWidth }) => {
        const cx = w / 2, cy = h / 2;
        const r = Math.min(w, h) / 2 - 2;
        const labelHtml = label
            ? `<text x="${cx}" y="${h + 14}" ${tBase} class="sui-diagram-label">${label}</text>`
            : "";
        return `<circle cx="${cx}" cy="${cy}" r="${r}" `
             + `class="sui-diagram-shape sui-diagram-shape--circle"${strokeAttr(borderWidth)}/>`
             + labelHtml;
    });

    // Diamond defaults to a square bounding box so the four sides are equal
    // (BPMN gateway look). Callers that want a wider rhombus can override via
    // the per-node width/height.
    registerShape("diamond", { w: 80, h: 80 }, ({ w, h, label, borderWidth }) => {
        const cx = w / 2, cy = h / 2;
        const path = `M ${cx},0 L ${w},${cy} L ${cx},${h} L 0,${cy} Z`;
        return `<path d="${path}" `
             + `class="sui-diagram-shape sui-diagram-shape--diamond"${strokeAttr(borderWidth)}/>`
             + labelAt(cx, cy, label);
    });

    // Bar: a thin rectangle, used for fork/join markers. Label sits below.
    registerShape("bar", { w: 160, h: 12 }, ({ w, h, label, borderWidth }) => {
        const labelHtml = label
            ? `<text x="${w / 2}" y="${h + 14}" ${tBase} class="sui-diagram-label">${label}</text>`
            : "";
        return `<rect x="0" y="0" width="${w}" height="${h}" rx="3" ry="3" `
             + `class="sui-diagram-shape sui-diagram-shape--bar"${strokeAttr(borderWidth)}/>`
             + labelHtml;
    });

    // Container with the label pinned to the top edge rather than the centre
    // — useful when the box wraps child nodes (the centre is where the
    // children render, so a centred label would overlap them). Used by the
    // workflow-diagram module for if-branch and foreach-loop containers.
    // Uses the same headerLabel as plain rects so the header band lines up
    // across step types.
    registerShape("container-with-header", { w: 200, h: 120 }, ({ w, h, label, borderWidth }) =>
        `<rect x="0" y="0" width="${w}" height="${h}" rx="8" ry="8" `
        + `class="sui-diagram-shape sui-diagram-shape--container-with-header"${strokeAttr(borderWidth)}/>`
        + headerLabel(w, label));
}

let defaultShapesInstalled = false;
function ensureDefaultShapesInstalled(): void {
    if (!defaultShapesInstalled) {
        installDefaultShapes();
        defaultShapesInstalled = true;
    }
}

/**
 * Registers the {@code diagram} node handler on the supplied renderer. The
 * handler returns a {@code <sui-diagram>} custom element with the graph
 * payload on a {@code data-graph} attribute; the element renders itself in
 * its {@code connectedCallback}. This mirrors the pattern used by jsonviewer
 * (offload rendering to a self-contained custom element) so the synchronous
 * renderer contract isn't violated.
 *
 * <p>Call this once per page after creating your {@code SuiRenderer}.
 */
export function install(renderer: SuiRenderer): void {
    ensureDefaultShapesInstalled();
    ensureCustomElementDefined();
    renderer.register<UiDiagramWire>("diagram", renderDiagram);
}

function renderDiagram(node: UiDiagramWire): string {
    // JSON.stringify + HTML escape — the element parses the payload back from
    // the attribute on connect. Same trick UiChart uses for its placeholder.
    const payload = escapeHtml(JSON.stringify(node));
    const cls = node.cssClass ? `sui-diagram ${escapeHtml(node.cssClass)}` : "sui-diagram";
    const id = escapeHtml(node.id);
    return `<sui-diagram id="${id}" class="${cls}" data-graph='${payload}'></sui-diagram>`;
}

// ── Custom element ──────────────────────────────────────────────────────────

/**
 * Events the element emits as {@code CustomEvent} on itself (bubbling).
 *
 * <p>The host page (or another extension) listens for these and translates
 * them into REST calls. Keeping all server I/O outside the element means the
 * same component works in a static demo (just log the events) or a Spring
 * Boot harness (post them) without code changes.
 */
export interface SuiDiagramEventMap {
    /** Node was clicked. {@code detail.stepRef} is null for synthetic nodes. */
    "sui-diagram-node-selected": { nodeId: string; stepRef: string | null };
    /** Node was dragged to a new position. Fires on mouse-up, not during the drag. */
    "sui-diagram-node-moved":    { nodeId: string; stepRef: string; x: number; y: number };
    /** User clicked the plus-button on a flow edge between two consecutive steps. */
    "sui-diagram-insert-after":  { afterStepRef: string | null };
    /**
     * User clicked the plus-button inside a container (top or bottom).
     * {@code containerPath} is the server-supplied identifier of the nested
     * children list to insert into; {@code position} is "first" or "last".
     */
    "sui-diagram-insert-into":   { containerPath: string; position: "first" | "last" };
    /** User pressed Delete on the selected node. */
    "sui-diagram-delete":        { stepRef: string };
    /**
     * Waypoints on an edge changed (added / moved / removed). The host
     * persists this through the server's edge-waypoints endpoint. The edge
     * is identified by the source/target stepRefs rather than the wire id
     * so the binding stays stable across rebuilds.
     */
    "sui-diagram-edge-waypoints-changed": {
        sourceStepRef: string;
        targetStepRef: string;
        waypoints: Array<{ x: number; y: number }>;
    };
}

/**
 * Pure-SVG editor canvas for {@link UiDiagramWire}. Renders nodes + edges,
 * adds plus-buttons on FLOW edges between non-synthetic neighbours, and
 * supports drag-to-move on non-synthetic nodes. Property edits are out of
 * scope here — they're handled by the host page through a side panel that
 * subscribes to {@code sui-diagram-node-selected}.
 *
 * <p>Idempotent: every {@code render()} clears the previous SVG, so
 * re-setting {@code data-graph} (via a server-driven refresh) just
 * re-renders the canvas without leaks.
 */
class SuiDiagramElement extends HTMLElement {

    static get observedAttributes(): string[] {
        return ["data-graph"];
    }

    private graph: UiDiagramWire | null = null;
    private selectedNodeId: string | null = null;

    // Per-render index: every node by id, plus its absolute (diagram-global)
    // top-left so we don't recompute the ancestor chain on every edge or
    // mouse-move. Rebuilt each render(); see {@link rebuildIndex}.
    private nodesById: Map<string, UiDiagramNodeWire> = new Map();
    private absolutePositions: Map<string, { x: number; y: number }> = new Map();
    private parentOfId: Map<string, string | null> = new Map();
    // Edges keyed by owner node id (empty string = top-level). Built in
    // paint() and consumed by renderNodeSvg, which slots the matching
    // edges into each container's <g> so they ride along on drag.
    private edgesByOwner: Map<string, UiDiagramEdgeWire[]> = new Map();

    // Drag state. With nested rendering, moving a container is just moving
    // its <g transform>: every descendant is positioned relative to it, so
    // they ride along for free. We only need the dragged node's own start
    // position and SVG group plus an absolute-offset map of every moving
    // node (the container + its descendants) so we can re-clip edges that
    // cross the container boundary.
    private dragging: {
        node: UiDiagramNodeWire;
        groupEl: SVGGElement;
        startMouseX: number;
        startMouseY: number;
        startNodeX: number;
        startNodeY: number;
    } | null = null;

    // Waypoint-drag state — independent of node-drag so the two never
    // collide. Used both for moving an existing waypoint and for
    // promoting a midpoint-handle into a fresh waypoint on first drag.
    private waypointDragging: {
        edge: UiDiagramEdgeWire;
        waypointIndex: number;       // index into edge.waypoints[]
        createdOnDown: boolean;       // true if the waypoint was just created (will be removed on cancel if not moved)
        // Offset from the cursor (in SVG-units) to the waypoint centre, at
        // mousedown. The move handler does newWp = cursorSvg - offset, so the
        // waypoint sticks to wherever on it the user grabbed (not jumping
        // its centre under the cursor) regardless of viewBox changes mid-drag.
        offsetX: number;
        offsetY: number;
    } | null = null;

    // Camera state. The SVG itself fills its container (no width/height
    // attributes); panning and zooming purely move/scale the viewBox.
    // {@link view} is null until the first paint — that paint auto-fits to
    // the whole graph, after which we remember the user's pan/zoom across
    // subsequent re-renders so a save/refresh doesn't snap the camera back.
    private view: { x: number; y: number; w: number; h: number } | null = null;

    // Interaction mode. "select" is the default (click/drag nodes, edges,
    // waypoints). "pan" turns the canvas into a draggable surface — any
    // mousedown on empty space (or even on a node) pans the viewBox.
    private mode: "select" | "pan" = "select";

    // Active canvas pan, separate from node/waypoint drags so the two never
    // interfere. Set on mousedown when {@link mode} is "pan".
    private canvasPanning: {
        startMouseX: number;
        startMouseY: number;
        startVbX: number;
        startVbY: number;
    } | null = null;

    connectedCallback(): void {
        this.render();
        this.tabIndex = 0;  // make focusable so we can capture Delete key
        this.addEventListener("keydown", this.onKeyDown);
        // passive:false because we preventDefault on Ctrl-wheel to stop
        // the browser's page-zoom from also firing.
        this.addEventListener("wheel", this.onWheel, { passive: false });
        this.addEventListener("mousedown", this.onCanvasMouseDown);
    }

    disconnectedCallback(): void {
        this.removeEventListener("keydown", this.onKeyDown);
        this.removeEventListener("wheel", this.onWheel);
        this.removeEventListener("mousedown", this.onCanvasMouseDown);
        // Tidy up any in-flight drag listeners on window if the element gets removed mid-drag.
        if (this.dragging) {
            window.removeEventListener("mousemove", this.onMouseMove);
            window.removeEventListener("mouseup", this.onMouseUp);
            this.dragging = null;
        }
    }

    attributeChangedCallback(name: string): void {
        if (name === "data-graph" && this.isConnected) {
            this.render();
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    /**
     * Lifecycle entry-point: re-parses {@code data-graph} into
     * {@link #graph} and paints. Called from
     * {@link #connectedCallback} and {@link #attributeChangedCallback} —
     * any time the wire attribute has fresh data the SVG should reflect.
     */
    private render(): void {
        const raw = this.getAttribute("data-graph");
        if (!raw) {
            this.innerHTML = "";
            this.graph = null;
            this.nodesById.clear();
            this.absolutePositions.clear();
            this.parentOfId.clear();
            return;
        }
        try {
            this.graph = JSON.parse(raw) as UiDiagramWire;
        } catch (err) {
            console.error("sui-diagram: invalid data-graph JSON", err);
            this.innerHTML = `<pre class="sui-diagram-error">invalid diagram payload</pre>`;
            return;
        }
        this.paint();
    }

    /**
     * Re-draws the SVG from the current in-memory {@link #graph} without
     * touching the wire attribute. Used by local mutation paths (waypoint
     * drag, etc.) that mutate the model in place — re-parsing
     * {@code data-graph} here would discard those mutations on every paint.
     */
    private repaint(): void {
        if (!this.graph) return;
        this.paint();
    }

    /**
     * Shared paint pipeline used by both {@link #render} and {@link #repaint}.
     * Rebuilds the per-render index, lays out the SVG, and wires interactions.
     * Assumes {@link #graph} is the model to draw — neither callers nor this
     * method touch the wire attribute.
     */
    private paint(): void {
        if (!this.graph) return;

        const graph = this.graph;
        this.rebuildIndex();

        const width = graph.width ?? this.computeBoundsWidth();
        const height = graph.height ?? this.computeBoundsHeight();

        // First paint of a brand-new graph: auto-fit the camera. From then on
        // we preserve the user's pan/zoom across re-renders. Save/refresh
        // doesn't snap the camera back to "fit whole graph", which is the
        // whole point of having an infinite canvas.
        if (!this.view) {
            const margin = 40;
            this.view = {
                x: -margin, y: -margin,
                w: width + margin * 2, h: height + margin * 2,
            };
        }

        // Edges are partitioned by their ownerNodeId so each one renders
        // inside the SVG transform of the container that holds both
        // endpoints. The top-level group only gets edges with owner=null;
        // container-owned ones are slotted into the matching container <g>
        // via renderNodeSvg below. This way, when a container is dragged,
        // SVG transform inheritance moves the edge AND its waypoints along
        // for free — no separate per-edge bookkeeping needed.
        this.edgesByOwner.clear();
        for (const e of graph.edges) {
            const owner = e.ownerNodeId ?? "";
            const bucket = this.edgesByOwner.get(owner) ?? [];
            bucket.push(e);
            this.edgesByOwner.set(owner, bucket);
        }
        const topLevelEdges = this.edgesByOwner.get("") ?? [];
        const edgesSvg = topLevelEdges.map(e => this.renderEdgeSvg(e)).join("");

        // Nodes render recursively: each top-level node emits a <g translate>,
        // its children render inside that <g> at their relative positions, etc.
        // SVG's transform inheritance gives us correct z-order for free
        // (parent draws first, then children on top).
        const nodesSvg = graph.nodes
            .map(n => this.renderNodeSvg(n, n.id === this.selectedNodeId))
            .join("");

        // SVG has no width/height attributes — CSS gives it 100% of the
        // <sui-diagram> wrapper, which itself fills the host's layout slot.
        // The viewBox is the camera; pan/zoom mutate it directly.
        const vb = this.view;
        this.innerHTML =
            `<svg xmlns="http://www.w3.org/2000/svg" class="sui-diagram-svg" `
            + `viewBox="${vb.x} ${vb.y} ${vb.w} ${vb.h}" `
            + `preserveAspectRatio="xMidYMid meet">`
            + `<defs>`
            +   `<marker id="sui-diagram-arrow" viewBox="0 0 10 10" refX="9" refY="5" `
            +     `markerWidth="8" markerHeight="8" orient="auto-start-reverse">`
            +     `<path d="M0,0 L10,5 L0,10 z" fill="currentColor"/>`
            +   `</marker>`
            + `</defs>`
            + `<g class="sui-diagram-edges">${edgesSvg}</g>`
            + `<g class="sui-diagram-nodes">${nodesSvg}</g>`
            + `</svg>`;

        this.wireInteractions();
    }

    /**
     * Writes {@link #view} back to the live SVG without doing a full repaint.
     * Cheap — touches a single attribute — so we can call it on every
     * mousemove during a pan or on every wheel-tick during a zoom.
     */
    private applyViewBox(): void {
        if (!this.view) return;
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;
        const v = this.view;
        svgEl.setAttribute("viewBox", `${v.x} ${v.y} ${v.w} ${v.h}`);
    }

    // ── Pan / zoom ──────────────────────────────────────────────────────────

    /**
     * Switches between "select" mode (the default — drag nodes, click to
     * select, click plus/delete buttons) and "pan" mode (drag anywhere on
     * the canvas to move the camera). Called from the host toolbar.
     */
    setMode(mode: "select" | "pan"): void {
        this.mode = mode;
        this.classList.toggle("sui-diagram--mode-pan", mode === "pan");
    }

    /**
     * Wheel = zoom toward (or away from) the cursor. Zooming "toward" the
     * cursor means the SVG point under the cursor stays put while the
     * surrounding area scales — the standard diagrams.io/Figma/Miro feel.
     */
    private onWheel = (ev: WheelEvent): void => {
        if (!this.view) return;
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;
        ev.preventDefault();   // stop the browser from also page-zooming
        const v = this.view;

        // Convert cursor pixel → SVG-unit point inside the current viewBox.
        const rect = svgEl.getBoundingClientRect();
        const px = (ev.clientX - rect.left) / rect.width;
        const py = (ev.clientY - rect.top)  / rect.height;
        const cx = v.x + px * v.w;
        const cy = v.y + py * v.h;

        // 1.0015^deltaY — exponential so the speed feels linear in screen
        // terms regardless of how big the current viewBox is. deltaY > 0
        // means scroll down = zoom out (browser default direction).
        const scale = Math.pow(1.0015, ev.deltaY);
        const minW = 50, maxW = 100000;
        let newW = v.w * scale;
        let newH = v.h * scale;
        if (newW < minW || newH < minW) { newW = v.w; newH = v.h; }
        if (newW > maxW || newH > maxW) { newW = v.w; newH = v.h; }

        // Keep (cx, cy) at the same screen-fraction position post-zoom.
        v.x = cx - px * newW;
        v.y = cy - py * newH;
        v.w = newW;
        v.h = newH;
        this.applyViewBox();
    };

    /**
     * Mousedown on the canvas: in pan mode, starts a camera drag regardless
     * of what's underneath. In select mode this no-ops — the existing
     * node/edge/waypoint mousedown handlers do their thing.
     */
    private onCanvasMouseDown = (ev: MouseEvent): void => {
        if (this.mode !== "pan") return;
        if (!this.view) return;
        // Left-button only — right-click should still open the browser menu.
        if (ev.button !== 0) return;
        ev.preventDefault();
        ev.stopPropagation();
        this.canvasPanning = {
            startMouseX: ev.clientX,
            startMouseY: ev.clientY,
            startVbX: this.view.x,
            startVbY: this.view.y,
        };
        window.addEventListener("mousemove", this.onCanvasPanMove);
        window.addEventListener("mouseup",   this.onCanvasPanUp);
    };

    private onCanvasPanMove = (ev: MouseEvent): void => {
        if (!this.canvasPanning || !this.view) return;
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;
        const rect = svgEl.getBoundingClientRect();
        // Convert pixel delta to viewBox-unit delta. Moving the mouse right
        // should drag the canvas right → the viewBox origin moves left.
        const scaleX = this.view.w / rect.width;
        const scaleY = this.view.h / rect.height;
        const dx = (ev.clientX - this.canvasPanning.startMouseX) * scaleX;
        const dy = (ev.clientY - this.canvasPanning.startMouseY) * scaleY;
        this.view.x = this.canvasPanning.startVbX - dx;
        this.view.y = this.canvasPanning.startVbY - dy;
        this.applyViewBox();
    };

    private onCanvasPanUp = (_ev: MouseEvent): void => {
        this.canvasPanning = null;
        window.removeEventListener("mousemove", this.onCanvasPanMove);
        window.removeEventListener("mouseup",   this.onCanvasPanUp);
    };

    /**
     * Rebuilds the by-id and absolute-position indices by walking the node
     * tree once. Called from {@link render} and whenever the model changes
     * (e.g. after a drag commits a new container position). Putting this in
     * one place keeps the renderer and the interaction handlers in sync.
     */
    private rebuildIndex(): void {
        this.nodesById.clear();
        this.absolutePositions.clear();
        this.parentOfId.clear();
        if (!this.graph) return;
        const walk = (n: UiDiagramNodeWire,
                      parentId: string | null,
                      parentAbsX: number, parentAbsY: number) => {
            const px = (n.position?.x ?? 0) + parentAbsX;
            const py = (n.position?.y ?? 0) + parentAbsY;
            this.nodesById.set(n.id, n);
            this.absolutePositions.set(n.id, { x: px, y: py });
            this.parentOfId.set(n.id, parentId);
            if (n.children) {
                for (const c of n.children) walk(c, n.id, px, py);
            }
        };
        for (const n of this.graph.nodes) walk(n, null, 0, 0);
    }

    private computeBoundsWidth(): number {
        let max = 0;
        for (const [id, pos] of this.absolutePositions) {
            const n = this.nodesById.get(id);
            if (!n) continue;
            const { w } = nodeSize(n);
            max = Math.max(max, pos.x + w);
        }
        return max + 40;
    }

    private computeBoundsHeight(): number {
        let max = 0;
        for (const [id, pos] of this.absolutePositions) {
            const n = this.nodesById.get(id);
            if (!n) continue;
            const { h } = nodeSize(n);
            max = Math.max(max, pos.y + h);
        }
        return max + 40;
    }

    // ── Interaction wiring ──────────────────────────────────────────────────

    // ── SVG renderers (recursive on the node tree) ──────────────────────────

    /**
     * Renders one node as an SVG {@code <g transform>}, with its shape body
     * first and any nested children inside. SVG inherits transforms down the
     * tree, so a child at relative {@code (20, 40)} inside a container at
     * absolute {@code (200, 100)} naturally draws at absolute {@code (220, 140)}.
     */
    private renderNodeSvg(n: UiDiagramNodeWire, selected: boolean): string {
        const pos = n.position ?? { x: 0, y: 0 };
        const { w, h } = nodeSize(n);
        const label = escapeHtml(n.label ?? "");
        const stepRef = n.stepRef ? ` data-step-ref="${escapeHtml(n.stepRef)}"` : "";
        const synthetic = n.synthetic ? " data-synthetic=\"true\"" : "";
        const id = escapeHtml(n.id);

        const klassParts = ["sui-diagram-node", `sui-diagram-node--${escapeHtml(n.shape)}`];
        if (n.shapeClass) klassParts.push(escapeHtml(n.shapeClass));
        if (selected)     klassParts.push("sui-diagram-node--selected");
        const klass = klassParts.join(" ");

        // Capability flags from the wire model decide what the editor
        // exposes on this node. Missing flag defaults to true so a
        // simply-built node behaves like a normal step.
        const canDrag         = n.canDrag         ?? true;
        const canInsertAfter  = n.canInsertAfter  ?? true;
        const canDelete       = n.canDelete       ?? true;
        const cursor = canDrag ? ` style="cursor: move"` : "";
        const body = renderShape(n, w, h, label);
        const childSvg = (n.children ?? [])
            .map(c => this.renderNodeSvg(c, c.id === this.selectedNodeId))
            .join("");

        // If this node is a container that can hold steps (the server
        // tagged it with a containerPath in data), expose the "insert at
        // start of children" plus inside the frame.
        const containerPath = n.data?.["containerPath"];
        const containerPlus = containerPath
            ? this.renderContainerPlusButtons(n.id, containerPath, w, h)
            : "";

        // Insert-after plus + delete-× — both gated purely on the server's
        // capability flags, no more stepRef-prefix or shape sniffing here.
        const afterPlus = (canInsertAfter && n.stepRef)
            ? this.renderInsertAfterPlus(n.stepRef, w, h)
            : "";
        const deleteBtn = (canDelete && n.stepRef)
            ? this.renderDeleteButton(n.stepRef, w)
            : "";

        // Edges this node owns (i.e. n is the LCA of their endpoints) get
        // rendered inside its <g> transform so they ride along when the
        // container is dragged. Endpoints and waypoints are computed in the
        // owner's local frame, not absolute. Rendered AFTER children so
        // edges sit visually on top of (or alongside) the nested nodes.
        const ownedEdges = this.edgesByOwner.get(id) ?? [];
        const edgeSvg = ownedEdges.map(e => this.renderEdgeSvg(e)).join("");

        return `<g class="${klass}" data-node-id="${id}"${stepRef}${synthetic}${cursor} `
             + `transform="translate(${pos.x},${pos.y})">${body}${childSvg}${edgeSvg}${containerPlus}${afterPlus}${deleteBtn}</g>`;
    }

    /**
     * Renders a plus-button immediately below a node, used to insert a new
     * step after the one being hovered. Coordinates are local to the
     * containing {@code <g transform>}, so the button rides along when the
     * step is dragged. Hidden by default — CSS reveals it on node hover.
     */
    private renderInsertAfterPlus(stepRef: string, w: number, h: number): string {
        const cx = w / 2;
        // Sit ON the bottom edge, not below it. Keeping the plus inside the
        // shape's bounding box means the node's :hover stays stable as the
        // cursor moves from shape to plus — no gap to fall through.
        const cy = h;
        const ref = escapeHtml(stepRef);
        return `<g class="sui-diagram-plus sui-diagram-plus--insert-after" `
             + `data-after-step-ref="${ref}" `
             + `style="cursor: pointer" transform="translate(${cx},${cy})">`
             + `<circle r="9" class="sui-diagram-plus-bg"/>`
             + `<text x="0" y="0" text-anchor="middle" dominant-baseline="central" `
             +   `class="sui-diagram-plus-label">+</text>`
             + `</g>`;
    }

    /**
     * Renders a small delete-button (×) at the top-right corner of a node.
     * Same hide-until-hover pattern as the plus, same in-shape positioning
     * (anchored on the shape's top edge) so :hover stays stable. Click
     * emits {@code sui-diagram-delete}; the host page is responsible for
     * confirming and issuing the actual DELETE request.
     */
    private renderDeleteButton(stepRef: string, w: number): string {
        const cx = w;       // right edge
        const cy = 0;       // top edge
        const ref = escapeHtml(stepRef);
        return `<g class="sui-diagram-delete" `
             + `data-delete-step-ref="${ref}" `
             + `style="cursor: pointer" transform="translate(${cx},${cy})">`
             + `<circle r="9" class="sui-diagram-delete-bg"/>`
             + `<text x="0" y="0" text-anchor="middle" dominant-baseline="central" `
             +   `class="sui-diagram-delete-label">×</text>`
             + `</g>`;
    }

    /**
     * Renders a single plus-button inside a container — just below the
     * header band — for inserting a step at the start of its children list.
     * The matching "append" gesture for the end of the list is the regular
     * insert-after plus that the last child step already shows on hover,
     * so a second container-level plus at the bottom would just be noise.
     * Coordinates are local to the container's own transform, so it rides
     * along when the container is dragged.
     */
    private renderContainerPlusButtons(nodeId: string, containerPath: string,
                                       w: number, _h: number): string {
        const cx = w / 2;
        // Sit clear below the header band — the label itself sits at y=18,
        // so anything at y<=18 would either overlap it or get its hover
        // swallowed by the text. y=40 lines up with the container's first-
        // child row (TOP_PAD on the server, 20 label-gutter + 20 padding).
        const topY = 40;
        const pathAttr = escapeHtml(containerPath);
        const owner = escapeHtml(nodeId);
        return `<g class="sui-diagram-plus sui-diagram-plus--container-first" `
             + `data-container-path="${pathAttr}" data-container-position="first" `
             + `data-owner-node-id="${owner}" `
             + `style="cursor: pointer" transform="translate(${cx},${topY})">`
             + `<circle r="9" class="sui-diagram-plus-bg"/>`
             + `<text x="0" y="0" text-anchor="middle" dominant-baseline="central" `
             +   `class="sui-diagram-plus-label">+</text></g>`;
    }

    /**
     * Renders one edge as an SVG polyline + optional label. Edge endpoints
     * are computed in <em>absolute</em> diagram coordinates (looked up in
     * {@link #absolutePositions}) so a cross-container edge draws correctly
     * regardless of how deeply each endpoint is nested.
     *
     * <p>If the edge carries waypoints, the polyline threads through them
     * between the clipped source and target points; otherwise it's a single
     * straight segment (same visual as the previous <line>-based renderer).
     */
    private renderEdgeSvg(e: UiDiagramEdgeWire): string {
        const path = this.computeEdgePath(e);
        if (!path) {
            console.warn("sui-diagram: edge references unknown node", e);
            return "";
        }
        const kindClass = e.kind === "structural"
            ? "sui-diagram-edge--structural" : "sui-diagram-edge--flow";
        const id = escapeHtml(e.id);
        const labelPt = pathMidpoint(path);
        const label = e.label
            ? `<text x="${labelPt.x}" y="${labelPt.y - 4}" `
              + `text-anchor="middle" class="sui-diagram-edge-label">${escapeHtml(e.label)}</text>`
            : "";
        const pointsAttr = path.map(p => `${p.x},${p.y}`).join(" ");

        // A thick, invisible polyline over the visible one. Two jobs:
        //  (a) Generous hit-area for hover (the visible line is just 1.5px).
        //  (b) Drag-target for "drag the line to bend it" — mousedown anywhere
        //      on this polyline inserts a new waypoint at the cursor and
        //      immediately starts dragging it, diagrams.io-style.
        // pointer-events="stroke" is essential: SVG's default is "visiblePainted",
        // which means a transparent stroke wouldn't intercept clicks. With
        // "stroke" the invisible-but-thick line catches the cursor anywhere
        // along its length regardless of paint.
        const hitArea = `<polyline points="${pointsAttr}" `
            + `class="sui-diagram-edge-hit" fill="none" stroke="transparent" `
            + `stroke-width="14" pointer-events="stroke" `
            + `data-edge-id="${id}"/>`;

        // Waypoint markers — one filled circle per waypoint. Drag to move,
        // shift-click to remove.
        const wpMarkers = (e.waypoints ?? []).map((wp, i) =>
            `<circle cx="${wp.x}" cy="${wp.y}" r="5" `
            + `class="sui-diagram-edge-waypoint" `
            + `data-edge-id="${id}" data-waypoint-index="${i}" `
            + `style="cursor: move"/>`).join("");

        return `<g class="sui-diagram-edge ${kindClass}" data-edge-id="${id}">`
             + `<polyline points="${pointsAttr}" `
             +   `class="sui-diagram-edge-line" fill="none" `
             +   `marker-end="url(#sui-diagram-arrow)"/>`
             + hitArea
             + label
             + wpMarkers
             + `</g>`;
    }

    /**
     * Returns the full point sequence the edge polyline should follow, in
     * the edge owner's local coordinate system: clipped-source, waypoints…,
     * clipped-target. Null if either endpoint can't be resolved.
     *
     * <p>Source and target are projected from their own absolute positions
     * into the owner's local frame by subtracting the owner's absolute
     * top-left. Waypoints are already in that local frame (that's the
     * whole point of the owner-frame model). The {@code overrides} map
     * carries live absolute positions during a drag — same projection
     * applies, plus the owner itself might be in the override map (the
     * dragged node IS the owner) so we look that up too.
     */
    private computeEdgePath(e: UiDiagramEdgeWire,
                            overrides?: Map<string, { x: number; y: number }>)
            : Array<{ x: number; y: number }> | null {
        const src = this.nodesById.get(e.source);
        const dst = this.nodesById.get(e.target);
        if (!src || !dst) return null;
        const srcAbs = overrides?.get(src.id) ?? this.absolutePositions.get(src.id);
        const dstAbs = overrides?.get(dst.id) ?? this.absolutePositions.get(dst.id);
        if (!srcAbs || !dstAbs) return null;

        // Owner's absolute origin — what we subtract to convert endpoint
        // absolutes into owner-local coords. ownerNodeId == null means the
        // diagram root, origin (0, 0).
        let originAbsX = 0, originAbsY = 0;
        const ownerId = e.ownerNodeId ?? null;
        if (ownerId) {
            const ownerAbs = overrides?.get(ownerId) ?? this.absolutePositions.get(ownerId);
            if (ownerAbs) { originAbsX = ownerAbs.x; originAbsY = ownerAbs.y; }
        }

        const srcCorner = { x: srcAbs.x - originAbsX, y: srcAbs.y - originAbsY };
        const dstCorner = { x: dstAbs.x - originAbsX, y: dstAbs.y - originAbsY };
        const srcSize = nodeSize(src);
        const dstSize = nodeSize(dst);
        const srcCentre = { x: srcCorner.x + srcSize.w / 2, y: srcCorner.y + srcSize.h / 2 };
        const dstCentre = { x: dstCorner.x + dstSize.w / 2, y: dstCorner.y + dstSize.h / 2 };

        const waypoints = e.waypoints ?? [];

        // When there are waypoints, the clip "looks toward" the first
        // waypoint on the source side and the last waypoint on the target
        // side — so the line meets each shape's boundary heading in the
        // right direction.
        const firstAim = waypoints.length > 0 ? waypoints[0]                 : dstCentre;
        const lastAim  = waypoints.length > 0 ? waypoints[waypoints.length-1] : srcCentre;

        const a = clipLineToRect(firstAim, srcCentre,
            srcCorner.x, srcCorner.y, srcSize.w, srcSize.h);
        const b = clipLineToRect(lastAim, dstCentre,
            dstCorner.x, dstCorner.y, dstSize.w, dstSize.h);
        return [a, ...waypoints, b];
    }

    /**
     * Attaches event handlers after each render. Using addEventListener on the
     * freshly-built children is simpler than delegated listeners because the
     * element subtree is small and we recreate it on every render anyway.
     */
    private wireInteractions(): void {
        const nodeGroups = this.querySelectorAll<SVGGElement>(".sui-diagram-node");
        nodeGroups.forEach(g => {
            g.addEventListener("mousedown", (ev) => this.onNodeMouseDown(ev, g));
            g.addEventListener("click",     (ev) => this.onNodeClick(ev, g));
        });

        const plusButtons = this.querySelectorAll<SVGGElement>(".sui-diagram-plus");
        plusButtons.forEach(btn => {
            btn.addEventListener("click", (ev) => this.onPlusClick(ev, btn));
        });

        // Delete-buttons (× at top-right of each step) — emit the same
        // sui-diagram-delete event the side-panel's Delete uses, so the host
        // gets a single code path for confirmation + server call.
        this.querySelectorAll<SVGGElement>(".sui-diagram-delete").forEach(btn => {
            // mousedown swallow keeps the click from kicking off node-drag.
            btn.addEventListener("mousedown", (ev) => ev.stopPropagation());
            btn.addEventListener("click", (ev) => {
                ev.stopPropagation();
                const stepRef = btn.dataset["deleteStepRef"];
                if (stepRef) this.emit("sui-diagram-delete", { stepRef });
            });
        });

        // Existing waypoint markers — drag to move, shift-click to delete.
        this.querySelectorAll<SVGCircleElement>(".sui-diagram-edge-waypoint").forEach(c => {
            c.addEventListener("mousedown", (ev) => this.onWaypointMouseDown(ev, c, false));
            c.addEventListener("click",     (ev) => this.onWaypointClick(ev, c));
        });

        // Edge hit areas — mousedown anywhere on the line inserts a fresh
        // waypoint at the cursor and starts dragging it.
        this.querySelectorAll<SVGPolylineElement>(".sui-diagram-edge-hit").forEach(p => {
            p.addEventListener("mousedown", (ev) => this.onEdgeHitMouseDown(ev, p));
        });
    }

    private onNodeClick(ev: MouseEvent, g: SVGGElement): void {
        if (this.dragging) return;  // suppress click that follows a drag
        ev.stopPropagation();
        const nodeId = g.dataset["nodeId"] ?? "";
        const node = this.nodesById.get(nodeId);
        if (!node || node.canSelect === false) return;
        const stepRef = g.dataset["stepRef"] ?? null;
        this.selectedNodeId = nodeId;
        this.refreshSelection();
        this.emit("sui-diagram-node-selected", { nodeId, stepRef });
    }

    private onNodeMouseDown(ev: MouseEvent, g: SVGGElement): void {
        // Pan mode owns every mousedown on the canvas — node-drag is off
        // until the user toggles back to select mode.
        if (this.mode === "pan") return;
        const nodeId = g.dataset["nodeId"] ?? "";
        const node = this.nodesById.get(nodeId);
        if (!node || !node.position) return;
        // Capability flag from the server decides who's draggable. Synthetic
        // scaffolding nodes (merge, branch frames, etc.) set canDrag=false;
        // start/end markers and real steps default to true.
        if (node.canDrag === false) return;
        ev.preventDefault();

        this.dragging = {
            node,
            groupEl: g,
            startMouseX: ev.clientX,
            startMouseY: ev.clientY,
            startNodeX: node.position.x,
            startNodeY: node.position.y,
        };
        window.addEventListener("mousemove", this.onMouseMove);
        window.addEventListener("mouseup",   this.onMouseUp);
    }

    /**
     * Bound to {@code this} via arrow-function field so addEventListener and
     * removeEventListener get the same reference.
     *
     * <p>With nested rendering, moving a node — container or leaf — is just
     * a matter of updating one {@code <g transform>}. SVG transform
     * inheritance moves every descendant along automatically. The only
     * non-trivial bit is the edges: each one needs its endpoints re-clipped
     * because the absolute position of any descendant has shifted.
     */
    private onMouseMove = (ev: MouseEvent): void => {
        if (!this.dragging || !this.graph) return;
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;

        const rect = svgEl.getBoundingClientRect();
        const scaleX = svgEl.viewBox.baseVal.width / rect.width;
        const scaleY = svgEl.viewBox.baseVal.height / rect.height;
        const dx = (ev.clientX - this.dragging.startMouseX) * scaleX;
        const dy = (ev.clientY - this.dragging.startMouseY) * scaleY;
        const newX = this.dragging.startNodeX + dx;
        const newY = this.dragging.startNodeY + dy;

        // 1) Move the dragged node. Descendants ride along automatically
        //    through SVG transform inheritance — no per-descendant work.
        this.dragging.groupEl.setAttribute("transform", `translate(${newX},${newY})`);

        // 2) Build an override map of absolute positions for every node
        //    whose visual position changed. That's the dragged node plus
        //    every descendant in the tree — same delta for all (the
        //    container's absolute position shifted by dx/dy, and each
        //    descendant's absolute = container.absolute + descendant.relative).
        const draggedAbs = this.absolutePositions.get(this.dragging.node.id);
        if (!draggedAbs) return;
        const newAbs = { x: draggedAbs.x + dx, y: draggedAbs.y + dy };
        const overrides = new Map<string, { x: number; y: number }>();
        overrides.set(this.dragging.node.id, newAbs);
        this.collectDescendantAbsolutes(this.dragging.node, newAbs.x, newAbs.y, overrides);

        // 2b) Grow the viewBox live so the dragged node stays visible while
        //     it's being moved past the current canvas extent. We use the
        //     override map (live absolutes) directly — no need to commit
        //     anything to the cache yet, that happens on mouse-up.
        this.expandViewBoxToFitAbsolutes(svgEl, this.dragging.node, overrides);

        // 3) Re-route every edge that touches any moving node.
        this.querySelectorAll<SVGGElement>(".sui-diagram-edge").forEach(g => {
            const edgeId = g.dataset["edgeId"];
            if (!edgeId) return;
            const edge = this.graph!.edges.find(e => e.id === edgeId);
            if (!edge) return;
            if (!overrides.has(edge.source) && !overrides.has(edge.target)) return;

            const path = this.computeEdgePath(edge, overrides);
            if (!path) return;

            const polyEl = g.querySelector<SVGPolylineElement>(".sui-diagram-edge-line");
            if (polyEl) {
                polyEl.setAttribute("points", path.map(p => `${p.x},${p.y}`).join(" "));
            }
            const labelEl = g.querySelector<SVGTextElement>(".sui-diagram-edge-label");
            if (labelEl) {
                const mid = pathMidpoint(path);
                labelEl.setAttribute("x", String(mid.x));
                labelEl.setAttribute("y", String(mid.y - 4));
            }
        });

        // 4) Same for plus-buttons sitting on those edges.
        this.querySelectorAll<SVGGElement>(".sui-diagram-plus").forEach(btn => {
            const edgeId = btn.dataset["edgeId"];
            if (!edgeId) return;
            const edge = this.graph!.edges.find(e => e.id === edgeId);
            if (!edge) return;
            if (!overrides.has(edge.source) && !overrides.has(edge.target)) return;
            const path = this.computeEdgePath(edge, overrides);
            if (!path) return;
            const mid = pathMidpoint(path);
            btn.setAttribute("transform", `translate(${mid.x},${mid.y})`);
        });
    };

    /**
     * Walks the dragged node's children, recording each descendant's new
     * absolute position into {@code overrides}. The parent's absolute
     * position is taken as given; each child sits at parentAbsolute + its
     * relative position.
     */
    private collectDescendantAbsolutes(parent: UiDiagramNodeWire,
                                       parentAbsX: number, parentAbsY: number,
                                       overrides: Map<string, { x: number; y: number }>): void {
        if (!parent.children) return;
        for (const c of parent.children) {
            const cx = (c.position?.x ?? 0) + parentAbsX;
            const cy = (c.position?.y ?? 0) + parentAbsY;
            overrides.set(c.id, { x: cx, y: cy });
            this.collectDescendantAbsolutes(c, cx, cy, overrides);
        }
    }

    /**
     * Shifts the cached absolute positions of {@code node} and every
     * descendant by {@code (dx, dy)}. Called after a drag commits so the
     * next interaction reads consistent absolutes without us having to
     * trigger a full re-render. Recurses through {@link UiDiagramNodeWire#children}.
     */
    private shiftAbsolutes(node: UiDiagramNodeWire, dx: number, dy: number): void {
        const cur = this.absolutePositions.get(node.id);
        if (cur) {
            this.absolutePositions.set(node.id, { x: cur.x + dx, y: cur.y + dy });
        }
        if (node.children) {
            for (const c of node.children) this.shiftAbsolutes(c, dx, dy);
        }
    }

    /**
     * Recomputes the {@code viewBox} to the minimal bounding rectangle that
     * covers the <em>whole</em> graph (plus a margin). Unlike the grow-only
     * {@link #expandViewBoxToFitAbsolutes} used during a live drag, this
     * both grows and <em>shrinks</em>, so dragging a node back into the
     * original area collapses the canvas to its tight extent.
     *
     * <p>Only safe to call at a moment when the user isn't actively dragging,
     * since a shrink during a drag would jitter the canvas. Called from
     * {@link #onMouseUp} after the new node position has been baked into
     * the cache.
     */
    private recomputeViewBoxToWholeGraph(svgEl: SVGSVGElement): void {
        // No-op under the infinite-canvas model. Previously this method
        // shrunk/expanded the viewBox to the graph's bounding box after every
        // node-drag so the canvas felt "tight". With user-driven pan/zoom
        // the camera belongs to the user — snapping it back on every drop
        // would steal their view. The {@link #expandViewBoxToFitAbsolutes}
        // grow-only path during a drag still kicks in if the user moves a
        // node off-screen.
        void svgEl;
    }

    /**
     * Grows the SVG {@code viewBox} so it covers {@code node} and all its
     * descendants. Grows both ways — if the node moved into negative
     * coordinates, the viewBox origin shifts left/up; if it moved past the
     * existing extent, width/height grow. {@code absoluteSource} resolves
     * each node id to its current absolute top-left — either the cached
     * {@link #absolutePositions} (post-drag) or a live overrides map (during
     * a drag).
     *
     * <p>Only grows in either direction; never shrinks. A clean shrink/reset
     * happens on the next full server render via {@code build()}'s extent
     * computation.
     */
    private expandViewBoxToFitAbsolutes(svgEl: SVGSVGElement,
                                        node: UiDiagramNodeWire,
                                        absoluteSource: Map<string, { x: number; y: number }>): void {
        if (!this.view) return;
        let minLeft = Number.POSITIVE_INFINITY, minTop = Number.POSITIVE_INFINITY;
        let maxRight = Number.NEGATIVE_INFINITY, maxBottom = Number.NEGATIVE_INFINITY;
        const visit = (n: UiDiagramNodeWire) => {
            const abs = absoluteSource.get(n.id);
            if (abs) {
                const sz = nodeSize(n);
                minLeft   = Math.min(minLeft,   abs.x);
                minTop    = Math.min(minTop,    abs.y);
                maxRight  = Math.max(maxRight,  abs.x + sz.w);
                maxBottom = Math.max(maxBottom, abs.y + sz.h);
            }
            if (n.children) for (const c of n.children) visit(c);
        };
        visit(node);
        if (!isFinite(maxRight)) return;  // no positioned node found, nothing to do

        const margin = 40;
        const v = this.view;
        const curLeft   = v.x,           curTop    = v.y;
        const curRight  = v.x + v.w,     curBottom = v.y + v.h;
        const newLeft   = Math.min(curLeft,   minLeft   - margin);
        const newTop    = Math.min(curTop,    minTop    - margin);
        const newRight  = Math.max(curRight,  maxRight  + margin);
        const newBottom = Math.max(curBottom, maxBottom + margin);
        if (newLeft === curLeft && newTop === curTop
            && newRight === curRight && newBottom === curBottom) return;

        v.x = newLeft; v.y = newTop;
        v.w = newRight - newLeft; v.h = newBottom - newTop;
        svgEl.setAttribute("viewBox", `${v.x} ${v.y} ${v.w} ${v.h}`);
    }

    /**
     * Grows the viewBox outward to keep {@code (x, y)} inside the canvas
     * with at least {@code margin} pixels of clearance. Used during a
     * live waypoint drag — the waypoint is a single point, so this is the
     * lightweight counterpart of the node-tree-aware
     * {@link #expandViewBoxToFitAbsolutes}. Only grows; shrinking back
     * happens on mouse-up via {@link #recomputeViewBoxToWholeGraph}.
     */
    private expandViewBoxToFitPoint(svgEl: SVGSVGElement,
                                    x: number, y: number,
                                    margin: number = 40): void {
        if (!this.view) return;
        const v = this.view;
        const curLeft   = v.x,        curTop    = v.y;
        const curRight  = v.x + v.w,  curBottom = v.y + v.h;
        const newLeft   = Math.min(curLeft,   x - margin);
        const newTop    = Math.min(curTop,    y - margin);
        const newRight  = Math.max(curRight,  x + margin);
        const newBottom = Math.max(curBottom, y + margin);
        if (newLeft === curLeft && newTop === curTop
            && newRight === curRight && newBottom === curBottom) return;
        v.x = newLeft; v.y = newTop;
        v.w = newRight - newLeft; v.h = newBottom - newTop;
        svgEl.setAttribute("viewBox", `${v.x} ${v.y} ${v.w} ${v.h}`);
    }

    private onMouseUp = (ev: MouseEvent): void => {
        if (!this.dragging) return;
        const d = this.dragging;

        // Compute the same delta we used in mousemove (client-pixel → SVG-units).
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        let dx = 0, dy = 0;
        if (svgEl) {
            const rect = svgEl.getBoundingClientRect();
            const scaleX = svgEl.viewBox.baseVal.width / rect.width;
            const scaleY = svgEl.viewBox.baseVal.height / rect.height;
            dx = (ev.clientX - d.startMouseX) * scaleX;
            dy = (ev.clientY - d.startMouseY) * scaleY;
        }

        // New relative position (what we write into node.position so subsequent
        // local re-renders are consistent with the persisted state).
        const finalRelX = d.startNodeX + dx;
        const finalRelY = d.startNodeY + dy;

        // New *absolute* position — what the server needs to compute the
        // parent-relative override. Absolute = start-absolute + delta, where
        // start-absolute is whatever rebuildIndex() recorded at render time.
        const startAbs = this.absolutePositions.get(d.node.id);
        const finalAbsX = (startAbs?.x ?? finalRelX) + dx;
        const finalAbsY = (startAbs?.y ?? finalRelY) + dy;

        if (d.node.position) {
            d.node.position.x = finalRelX;
            d.node.position.y = finalRelY;
        }

        // Keep the absolute-position index in sync with the new model. The
        // server's move-node endpoint replies 204 (no re-render), so the
        // next drag would otherwise read stale absolutes here and edges
        // would route from the dragged node's old visual position. Update
        // the dragged node and recursively all descendants — their relative
        // offsets are unchanged, but their absolutes shifted by the same dx/dy.
        const startAbsForUpdate = this.absolutePositions.get(d.node.id);
        if (startAbsForUpdate) {
            this.shiftAbsolutes(d.node, dx, dy);
        }

        // Recompute the SVG viewBox to the whole graph's minimal bounding
        // box. Unlike the live-grow during drag, this is allowed to *shrink*
        // — dragging a node back into the original area collapses the
        // canvas to its tight extent again. Safe to do at mouse-up because
        // no live drag is in flight.
        if (svgEl) {
            this.recomputeViewBoxToWholeGraph(svgEl);
        }

        // Re-fit every ancestor container to its (possibly moved) children.
        // Done at mouse-up rather than during the drag itself so the
        // container box doesn't jitter while the user is moving — a clean
        // single resize at the end is calmer and lets the user place a
        // child outside the original bounds without the container chasing
        // them frame-by-frame. A repaint() rebuilds the SVG using the new
        // model sizes; cheaper than threading width/height attribute
        // mutations through the live drag.
        const needsRepaint = this.refitAncestorContainers(d.node);

        // Note: waypoints used to need explicit shifting when a container
        // dragged its contained edges around (they were stored in absolute
        // coords). Now they live in the owner container's local frame, so
        // SVG transform inheritance does that for free — no per-waypoint
        // model mutation or persistence needed on a container drag.

        window.removeEventListener("mousemove", this.onMouseMove);
        window.removeEventListener("mouseup",   this.onMouseUp);

        const stepRef = d.node.stepRef;
        const movedAny = Math.abs(dx) > 1 || Math.abs(dy) > 1;
        this.dragging = null;

        if (needsRepaint) this.repaint();

        if (stepRef && movedAny) {
            this.emit("sui-diagram-node-moved",
                { nodeId: d.node.id, stepRef, x: finalAbsX, y: finalAbsY });
        }
    };

    // ── Container auto-fit ──────────────────────────────────────────────────

    /**
     * Walks from {@code movedNode} up the parent chain and resizes every
     * ancestor container to tightly fit its children plus the standard
     * padding. Returns true when at least one container's size actually
     * changed, so the caller knows to repaint.
     *
     * <p>The padding constants (top 44 = label-gutter + padding, sides /
     * bottom 24) mirror BuildContext.java on the server — that way the
     * client-side fit lands on the same size the server will pick when
     * the workflow gets re-built (after save/refresh/insert).
     */
    private refitAncestorContainers(movedNode: UiDiagramNodeWire): boolean {
        const SIDE_PAD = 24;
        const TOP_PAD  = 44;     // = LABEL_GUTTER (20) + PADDING (24)
        let changed = false;
        let cursorId: string | null | undefined = this.parentOfId.get(movedNode.id);
        while (cursorId) {
            const container = this.nodesById.get(cursorId);
            if (!container) break;
            const children = container.children ?? [];
            if (children.length === 0) { cursorId = this.parentOfId.get(cursorId); continue; }
            let maxRight = 160, maxBottom = TOP_PAD + 40;  // sensible minimums
            for (const c of children) {
                if (!c.position) continue;
                const sz = nodeSize(c);
                maxRight  = Math.max(maxRight,  c.position.x + sz.w);
                maxBottom = Math.max(maxBottom, c.position.y + sz.h);
            }
            const newW = Math.round(maxRight  + SIDE_PAD);
            const newH = Math.round(maxBottom + SIDE_PAD);
            if (container.width !== newW || container.height !== newH) {
                container.width  = newW;
                container.height = newH;
                changed = true;
            }
            cursorId = this.parentOfId.get(cursorId);
        }
        return changed;
    }

    private onPlusClick(ev: MouseEvent, btn: SVGGElement): void {
        ev.stopPropagation();
        // Container plus-button: dataset carries containerPath + position.
        const containerPath = btn.dataset["containerPath"];
        if (containerPath) {
            const pos = btn.dataset["containerPosition"] === "first" ? "first" : "last";
            this.emit("sui-diagram-insert-into", { containerPath, position: pos });
            return;
        }
        // Insert-after plus-button: dataset carries the stepRef of the
        // node it sits beneath. The server treats the reserved __start__
        // ref as "insert at the beginning".
        const after = btn.dataset["afterStepRef"];
        if (after) this.emit("sui-diagram-insert-after", { afterStepRef: after });
    }

    // ── Waypoint editing ────────────────────────────────────────────────────

    /**
     * Looks up an edge by id and returns it; null when missing.
     */
    private findEdge(edgeId: string): UiDiagramEdgeWire | null {
        if (!this.graph) return null;
        return this.graph.edges.find(e => e.id === edgeId) ?? null;
    }

    /**
     * Returns the source/target stepRefs for an edge — these are the
     * stable identifiers we use to persist waypoints. Returns null if
     * either endpoint is not a real step (synthetic w/o stepRef).
     */
    private edgeStepRefs(e: UiDiagramEdgeWire): { source: string; target: string } | null {
        const src = this.nodesById.get(e.source);
        const dst = this.nodesById.get(e.target);
        if (!src || !dst) return null;
        const srcRef = src.stepRef;
        const dstRef = dst.stepRef;
        if (!srcRef || !dstRef) return null;
        return { source: srcRef, target: dstRef };
    }

    /**
     * Mouse-down on an existing waypoint handle. Starts a drag that
     * eventually emits a waypoints-changed event. {@code justCreated} flags
     * the case where the waypoint was *just* inserted from a midpoint
     * handle — we treat it the same as a normal drag.
     */
    private onWaypointMouseDown(ev: MouseEvent, handle: SVGCircleElement,
                                justCreated: boolean): void {
        if (this.mode === "pan") return;
        if (ev.shiftKey) return;  // shift+click handled in onWaypointClick
        const edgeId = handle.dataset["edgeId"];
        const idxStr = handle.dataset["waypointIndex"];
        if (!edgeId || idxStr == null) return;
        const edge = this.findEdge(edgeId);
        if (!edge || !edge.waypoints) return;
        const idx = parseInt(idxStr, 10);
        const wp = edge.waypoints[idx];
        if (!wp) return;
        ev.preventDefault();
        ev.stopPropagation();

        // Where the cursor sits inside the waypoint, in SVG-units. The
        // mousemove handler subtracts this from the live cursor-in-SVG so
        // the waypoint follows the cursor by the same offset it was grabbed
        // at — clicking the right edge of the dot keeps it on the right
        // edge instead of teleporting the centre under the cursor.
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;
        const cursorSvg = clientToSvg(ev.clientX, ev.clientY, svgEl);
        this.waypointDragging = {
            edge,
            waypointIndex: idx,
            createdOnDown: justCreated,
            offsetX: cursorSvg.x - wp.x,
            offsetY: cursorSvg.y - wp.y,
        };
        window.addEventListener("mousemove", this.onWaypointMouseMove);
        window.addEventListener("mouseup",   this.onWaypointMouseUp);
    }

    /**
     * Mouse-down on an edge's hit-area (the thick invisible polyline laid
     * on top of the visible line). Inserts a fresh waypoint at the cursor
     * and immediately starts the drag — no more plus-button conflict to
     * worry about because the insert-step plus now lives under the source
     * node, not at the edge midpoint.
     */
    private onEdgeHitMouseDown(ev: MouseEvent, hitEl: SVGPolylineElement): void {
        if (this.mode === "pan") return;
        const edgeId = hitEl.dataset["edgeId"];
        if (!edgeId) return;
        const edge = this.findEdge(edgeId);
        if (!edge) return;
        this.beginEdgeWaypointCreate(ev, edge);
    }

    /**
     * Inserts a fresh waypoint at {@code downEv}'s position and starts the
     * normal waypoint-drag flow.
     */
    private beginEdgeWaypointCreate(downEv: MouseEvent, edge: UiDiagramEdgeWire): void {
        downEv.preventDefault();
        downEv.stopPropagation();
        this.beginEdgeWaypointCreateFromPoint(downEv.clientX, downEv.clientY, edge, downEv);
    }

    private beginEdgeWaypointCreateFromPoint(downClientX: number, downClientY: number,
                                             edge: UiDiagramEdgeWire,
                                             currentEv: MouseEvent): void {
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;
        const pt = clientToSvg(downClientX, downClientY, svgEl);

        const path = this.computeEdgePath(edge);
        if (!path || path.length < 2) return;
        const segIdx = nearestSegmentIndex(path, pt);

        if (!edge.waypoints) edge.waypoints = [];
        edge.waypoints.splice(segIdx, 0, pt);
        this.repaint();

        // Cursor and waypoint share the same SVG-point here (we just spawned
        // the waypoint *at* the cursor), so the offset is zero by construction.
        this.waypointDragging = {
            edge,
            waypointIndex: segIdx,
            createdOnDown: true,
            offsetX: 0,
            offsetY: 0,
        };
        window.addEventListener("mousemove", this.onWaypointMouseMove);
        window.addEventListener("mouseup",   this.onWaypointMouseUp);
        // If we got here via the plus-vs-edge debounce, feed the current
        // mouse position straight into the drag so the waypoint snaps to
        // the cursor right away (instead of waiting for the next move).
        if (currentEv && currentEv.type !== "mousedown") {
            this.onWaypointMouseMove(currentEv);
        }
    }

    /**
     * Shift+click on a waypoint removes it. Non-shift click is consumed but
     * does nothing — the mouse-down path (drag) owns the interaction.
     */
    private onWaypointClick(ev: MouseEvent, handle: SVGCircleElement): void {
        ev.stopPropagation();
        if (!ev.shiftKey) return;
        const edgeId = handle.dataset["edgeId"];
        const idxStr = handle.dataset["waypointIndex"];
        if (!edgeId || idxStr == null) return;
        const edge = this.findEdge(edgeId);
        if (!edge || !edge.waypoints) return;
        const idx = parseInt(idxStr, 10);
        edge.waypoints.splice(idx, 1);
        this.repaint();
        this.emitWaypointsChanged(edge);
    }

    private onWaypointMouseMove = (ev: MouseEvent): void => {
        if (!this.waypointDragging) return;
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (!svgEl) return;

        // Cursor → live SVG-units, then subtract the grab-offset. Reading
        // the *current* viewBox+rect every tick means a viewBox grow this
        // frame (expandViewBoxToFitPoint below) doesn't desync the drag —
        // there's no stale scale factor anywhere in the math.
        const cursor = clientToSvg(ev.clientX, ev.clientY, svgEl);
        const newX = cursor.x - this.waypointDragging.offsetX;
        const newY = cursor.y - this.waypointDragging.offsetY;

        const wp = this.waypointDragging.edge.waypoints![this.waypointDragging.waypointIndex];
        wp.x = newX;
        wp.y = newY;
        // Grow the canvas as the user drags the waypoint past the edge —
        // without this the point could end up outside the viewBox and become
        // unreachable. Mirror of what node-drag does.
        this.expandViewBoxToFitPoint(svgEl, newX, newY);
        this.repaint();
    };

    private onWaypointMouseUp = (_ev: MouseEvent): void => {
        const d = this.waypointDragging;
        if (!d) return;
        window.removeEventListener("mousemove", this.onWaypointMouseMove);
        window.removeEventListener("mouseup",   this.onWaypointMouseUp);
        this.waypointDragging = null;
        // Final tight-fit pass like node-drag does — also lets the canvas
        // shrink back if the user dragged the waypoint into existing
        // empty space then back into the bounding box.
        const svgEl = this.querySelector<SVGSVGElement>("svg.sui-diagram-svg");
        if (svgEl) this.recomputeViewBoxToWholeGraph(svgEl);
        this.emitWaypointsChanged(d.edge);
    };

    private emitWaypointsChanged(edge: UiDiagramEdgeWire): void {
        const refs = this.edgeStepRefs(edge);
        if (!refs) return;  // edge has a synthetic endpoint with no stepRef — can't persist
        this.emit("sui-diagram-edge-waypoints-changed", {
            sourceStepRef: refs.source,
            targetStepRef: refs.target,
            waypoints: (edge.waypoints ?? []).map(wp => ({ x: wp.x, y: wp.y })),
        });
    }

    private onKeyDown = (ev: KeyboardEvent): void => {
        if ((ev.key === "Delete" || ev.key === "Backspace") && this.selectedNodeId) {
            // Use the by-id index so nested nodes (children of a container)
            // are found, not just top-level ones.
            const node = this.nodesById.get(this.selectedNodeId);
            if (node && !node.synthetic && node.stepRef) {
                ev.preventDefault();
                this.emit("sui-diagram-delete", { stepRef: node.stepRef });
            }
        }
    };

    private refreshSelection(): void {
        this.querySelectorAll(".sui-diagram-node").forEach(g => {
            g.classList.toggle("sui-diagram-node--selected",
                (g as HTMLElement).dataset["nodeId"] === this.selectedNodeId);
        });
    }

    private emit<K extends keyof SuiDiagramEventMap>(type: K, detail: SuiDiagramEventMap[K]): void {
        this.dispatchEvent(new CustomEvent(type, { detail, bubbles: true, composed: true }));
    }
}

function ensureCustomElementDefined(): void {
    if (typeof customElements !== "undefined" && !customElements.get("sui-diagram")) {
        customElements.define("sui-diagram", SuiDiagramElement);
    }
}

// ── Geometry helpers ────────────────────────────────────────────────────────

/**
 * Clips the line segment {@code from → to} so its end-point sits on the
 * boundary of the axis-aligned rectangle defined by {@code (cornerX, cornerY)}
 * and {@code (w, h)} — rather than at the rectangle's centre.
 *
 * <p>Used when drawing an edge into a node: we start with both endpoints at
 * the respective node centres, then push each endpoint outward to the box
 * boundary so the arrowhead lands on the shape's edge instead of being
 * buried inside it. For non-rectangular shapes (diamonds, circles) we still
 * clip against the bounding box — close enough visually and avoids
 * per-shape geometry.
 */
function clipLineToRect(from: { x: number; y: number },
                        to: { x: number; y: number },
                        cornerX: number, cornerY: number,
                        w: number, h: number): { x: number; y: number } {
    const cx = cornerX + w / 2;
    const cy = cornerY + h / 2;
    const dx = from.x - cx;
    const dy = from.y - cy;
    if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) return to;

    const halfW = w / 2;
    const halfH = h / 2;
    const tx = halfW / Math.abs(dx || 1e-9);
    const ty = halfH / Math.abs(dy || 1e-9);
    const t  = Math.min(tx, ty);
    return { x: cx + dx * t, y: cy + dy * t };
}

/**
 * Returns the midpoint of a polyline by arc length — used to anchor edge
 * labels. Walks the cumulative segment lengths until reaching half-total,
 * then linearly interpolates within that segment. For a 2-point path this
 * collapses to the geometric midpoint.
 */
/**
 * Returns the index of the segment in {@code path} whose closest point
 * to {@code pt} is nearest overall. Segments are numbered by their start
 * index: segment i is between path[i] and path[i+1]. Used when the user
 * clicks on an edge's hit-area — we need to know which segment to split
 * with the new waypoint.
 *
 * <p>Distance is point-to-segment, not point-to-line, so a click well past
 * a segment's end doesn't get attributed to that segment just because the
 * infinite line happens to pass close by.
 */
function nearestSegmentIndex(path: Array<{ x: number; y: number }>,
                             pt: { x: number; y: number }): number {
    let bestIdx = 0;
    let bestDist = Number.POSITIVE_INFINITY;
    for (let i = 0; i < path.length - 1; i++) {
        const d = pointSegmentDistance(pt, path[i], path[i + 1]);
        if (d < bestDist) {
            bestDist = d;
            bestIdx = i;
        }
    }
    return bestIdx;
}

function pointSegmentDistance(p: { x: number; y: number },
                              a: { x: number; y: number },
                              b: { x: number; y: number }): number {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-9) {
        // Degenerate segment — fall back to point-to-point distance.
        const ddx = p.x - a.x, ddy = p.y - a.y;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }
    // Project p onto the segment, clamp t to [0, 1] so we stay on it.
    let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq;
    t = Math.max(0, Math.min(1, t));
    const cx = a.x + t * dx;
    const cy = a.y + t * dy;
    const dxp = p.x - cx, dyp = p.y - cy;
    return Math.sqrt(dxp * dxp + dyp * dyp);
}

/**
 * Translates a client-space (browser pixel) coordinate into the SVG's own
 * coordinate system. Uses the SVG's live screen CTM (current transformation
 * matrix), which already accounts for the bounding-rect offset, viewBox
 * scaling, AND preserveAspectRatio letterboxing — the naive
 * `(clientX - rect.left) * vb.width / rect.width` form silently breaks when
 * the container's aspect ratio doesn't match the viewBox's, producing a
 * horizontal-or-vertical-only drift during drags.
 */
function clientToSvg(clientX: number, clientY: number,
                     svgEl: SVGSVGElement): { x: number; y: number } {
    const ctm = svgEl.getScreenCTM();
    if (!ctm) {
        // Fallback: SVG isn't laid out yet. Naive math is fine for the
        // degenerate "no viewBox transform" case.
        const rect = svgEl.getBoundingClientRect();
        const vb = svgEl.viewBox.baseVal;
        return {
            x: vb.x + (clientX - rect.left) * (vb.width  / rect.width),
            y: vb.y + (clientY - rect.top)  * (vb.height / rect.height),
        };
    }
    const pt = svgEl.createSVGPoint();
    pt.x = clientX;
    pt.y = clientY;
    const local = pt.matrixTransform(ctm.inverse());
    return { x: local.x, y: local.y };
}

function pathMidpoint(points: Array<{ x: number; y: number }>): { x: number; y: number } {
    if (points.length === 0) return { x: 0, y: 0 };
    if (points.length === 1) return points[0];
    let total = 0;
    const segLens: number[] = [];
    for (let i = 0; i < points.length - 1; i++) {
        const dx = points[i + 1].x - points[i].x;
        const dy = points[i + 1].y - points[i].y;
        const len = Math.sqrt(dx * dx + dy * dy);
        segLens.push(len);
        total += len;
    }
    let remaining = total / 2;
    for (let i = 0; i < segLens.length; i++) {
        if (remaining <= segLens[i] || i === segLens.length - 1) {
            const t = segLens[i] === 0 ? 0 : remaining / segLens[i];
            return {
                x: points[i].x + (points[i + 1].x - points[i].x) * t,
                y: points[i].y + (points[i + 1].y - points[i].y) * t,
            };
        }
        remaining -= segLens[i];
    }
    return points[points.length - 1];
}

/**
 * Dispatches to the registered renderer for {@code n.shape} and adds any
 * cross-shape decorations the node carries on the wire (today just
 * {@link UiDiagramNodeWire.marker} — a corner badge for things like the
 * {@code +} on a collapsed subprocess).
 *
 * <p>Unknown shape names fall back to a visible "unknown" rectangle so a
 * misconfigured server is easy to spot at a glance rather than vanishing
 * silently.
 */
function renderShape(n: UiDiagramNodeWire, w: number, h: number, label: string): string {
    const renderer = SHAPE_RENDERERS.get(n.shape);
    const body = renderer
        ? renderer({ w, h, label, borderWidth: n.borderWidth })
        : `<rect x="0" y="0" width="${w}" height="${h}" `
            + `class="sui-diagram-shape sui-diagram-shape--unknown"/>`
            + `<text x="${w / 2}" y="${h / 2}" text-anchor="middle" dominant-baseline="middle" `
            + `class="sui-diagram-label">${label}</text>`;

    // Marker — single-character badge in the bottom-right corner. Drawn
    // after the shape so it overlays the outline.
    const marker = n.marker
        ? `<text x="${w - 10}" y="${h - 6}" class="sui-diagram-marker">${escapeHtml(n.marker)}</text>`
        : "";
    return body + marker;
}
