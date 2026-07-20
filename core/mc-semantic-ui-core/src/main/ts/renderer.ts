import type {
    UiNode, UiField, UiAction, UiLink, UiListItem, UiTrigger, Pagination,
    UiForm, UiDetail, UiTable, UiTableColumn, UiTableRow,
    UiList, UiTree, UiTreeNode, UiMenu, UiMenuItem, UiMenuButton, UiSection, UiSectionEntry, UiStack, UiAppShell, UiHeader,
    UiText, UiIcon, UiSpinner, UiProgress, UiUpload, UiFieldGroup, UiDialog, UiPatch, UiPatchOperation,
} from "./model.js";

// Per-type render functions live under {@code ./renderers/}. They're
// imported here only to be registered as default handlers — the dispatcher
// itself never calls them directly.
import { renderForm }         from "./renderers/form.js";
import { renderDetail }       from "./renderers/detail.js";
import { renderList }         from "./renderers/list.js";
import { renderTree, renderTreeNode } from "./renderers/tree.js";
import { renderMenu, renderMenuItem } from "./renderers/menu.js";
import { renderMenuButton } from "./renderers/menu-button.js";
import { renderSection }      from "./renderers/section.js";
import { renderSectionEntry } from "./renderers/section-entry.js";
import { renderStack }        from "./renderers/stack.js";
import { renderTable }        from "./renderers/table.js";
import { renderColumn }       from "./renderers/column.js";
import { renderRow }          from "./renderers/row.js";
import { renderHeader }       from "./renderers/header.js";
import { renderText }         from "./renderers/text.js";
import { renderLink }         from "./renderers/link.js";
import { renderAction }       from "./renderers/action.js";
import { renderField }        from "./renderers/field.js";
import { renderFieldGroup }   from "./renderers/fieldgroup.js";
import { renderDialog }       from "./renderers/dialog.js";
import { renderUpload }       from "./renderers/upload.js";
import { renderIconNode }     from "./renderers/icon.js";
import { renderSpinner }      from "./renderers/spinner.js";
import { renderProgress }     from "./renderers/progress.js";
// Icon rendering is behind a swappable resolver — re-exported so apps can
// point at a different sprite / inline SVG / icon font.
export {
    renderIcon, setIconResolver, setIconSpriteUrl, spriteIconResolver,
    type IconResolver, type IconOpts,
} from "./renderers/icon.js";
// Menu state machine — re-exported so apps can restore a user's persisted
// collapse choice after mount (restoreMenuState) and drive it programmatically.
export {
    restoreMenuState, applyMenuState, cycleMenuState, menuStateOf,
    MENU_STATES, type MenuState,
} from "./renderers/menu.js";
// Priority-plus overflow for any single-row container marked
// data-sui-overflow="menu" (tab bars, header extras, your own toolbars).
// The event bus wires it on every mount, so apps rarely call it directly.
export { wireOverflow } from "./renderers/overflow.js";
// Older, container-specific names — thin aliases over wireOverflow.
export { wireTabOverflow } from "./renderers/tabs.js";
export { wireHeaderOverflow } from "./renderers/header-overflow.js";
// Menu-button popovers — apps call it once after mount to activate the
// click-positioned dropdown / context menus (UiMenuButton).
export { wireMenuButtons } from "./renderers/menu-button.js";
// Default item-handler for the UiList rendering — set on the SuiRenderer
// at construction time. List items have no type discriminator so they
// can't go through the dispatcher; they get their own handler slot.
import { defaultRenderItem } from "./renderers/shared.js";
import { renderAppShell } from "./renderers/app-shell.js";

/**
 * A handler renders one node of a specific {@link UiNode#type} to an HTML
 * string. It receives the owning {@link SuiRenderer} so it can recurse via
 * {@code renderer.render(child)} — never call sibling handlers directly,
 * otherwise application overrides won't be honoured throughout the tree.
 *
 * <p>The constraint is intentionally loose ({@code type: string}) rather than
 * {@code extends UiNode}: extensions register their own node shapes that
 * don't appear in the core union, but the dispatcher only needs the
 * {@code type} discriminator to route.
 */
export type NodeHandler<N extends { type: string } = UiNode> =
    (node: N, renderer: SuiRenderer) => string;

/**
 * Optional hook for declaring markup at a finer grain than a full node.
 * Used today for {@link UiListItem} (children of {@code UiList}); kept
 * separate from {@link NodeHandler} because items have no {@code type}
 * discriminator. Future "leaf hooks" (table-cells, form-fields) would
 * follow the same shape.
 */
export type ItemHandler = (item: UiListItem, renderer: SuiRenderer) => string;

/**
 * Pluggable renderer for the semantic-ui node tree.
 *
 * Designed for instance use, not as a module-scope singleton — multiple
 * renderers can coexist on the same page (e.g. main app + embedded widget,
 * each with its own custom node types).
 *
 * Built-in node types are wired by {@link installDefaultHandlers}, which the
 * default-exported {@link createDefaultRenderer} calls for you. Application
 * code registers custom or overriding handlers via {@link SuiRenderer#register}.
 *
 * Handlers should recurse through the owning renderer instance
 * ({@code renderer.render(child)}) rather than reaching for the default
 * exports, so overrides apply to the whole subtree.
 */
/**
 * Renders the loading overlay shown by the renderer's default loading
 * indicator. Apps that want a different look register their own via
 * {@link SuiRenderer#setLoadingIndicator}.
 */
export interface LoadingIndicator {
    show(root: HTMLElement): void;
    hide(root: HTMLElement): void;
}

export class SuiRenderer {
    private readonly handlers = new Map<string, NodeHandler<any>>();
    private itemHandler: ItemHandler = defaultRenderItem;
    private rootElement: HTMLElement | null = null;
    private loadingDepth = 0;
    private loadingIndicator: LoadingIndicator = defaultLoadingIndicator;
    private morpher: Morpher = innerHtmlMorpher;
    private morphPromise: Promise<Morpher> | null = null;

    /**
     * Optionally bind the renderer to a host element. Once mounted,
     * {@link SuiRenderer#mount} and {@link SuiRenderer#applyPatch} write
     * directly into it, which is the simplest way to wire the renderer into
     * a page that has a single content container. Callers that need finer
     * control (e.g. an app that owns its own patch dispatcher) can leave
     * the constructor argument empty and keep using the lower-level
     * {@link SuiRenderer#render} string API.
     *
     * <p>Constructing a renderer touches the network not at all: the
     * Idiomorph load starts at the first DOM write (see {@link #morph}).
     */
    constructor(rootElement?: HTMLElement | null) {
        if (rootElement) this.rootElement = rootElement;
    }

    /**
     * Every DOM write goes through here, which is also the only place that
     * can trigger the Idiomorph load.
     *
     * <p>The load used to start in the constructor. That made
     * {@code new SuiRenderer()} reach for a CDN even when the instance was
     * only ever going to be used for its {@link #render} string API — the
     * server-side-rendering case, where there is no DOM to morph and the
     * fetch is pure cost: an outbound request to a third-party host from a
     * backend process, one warning per instance in the log, and a DNS
     * failure per construction in a network-restricted environment. Binding
     * it to the first write means a renderer that only ever builds strings
     * stays entirely offline, while a renderer in a browser is unaffected:
     * its first write is a {@code mount} that happens microseconds later.
     */
    private morph(target: HTMLElement, newContent: string, mode: "innerHTML" | "outerHTML"): void {
        this.kickoffMorphLoad();
        this.morpher(target, newContent, mode);
    }

    /**
     * Loads Idiomorph from the CDN and installs it as the active morpher.
     * Called on the first DOM write, so the first paint is almost always the
     * innerHTML fallback; every subsequent swap uses Idiomorph.
     */
    private kickoffMorphLoad(): void {
        if (this.morphPromise) return;
        this.morphPromise = import(/* @vite-ignore */ IDIOMORPH_URL)
            .then(mod => {
                const lib = (mod.Idiomorph ?? mod.default ?? mod) as IdiomorphLib;
                if (typeof lib?.morph !== "function") {
                    throw new Error("Idiomorph: unexpected module shape");
                }
                this.morpher = idiomorphMorpher(lib);
                return this.morpher;
            })
            .catch(err => {
                console.warn("SuiRenderer: Idiomorph load failed; falling back to innerHTML morph", err);
                return this.morpher;
            });
    }

    /**
     * Replaces the morpher with a custom implementation. Useful for apps
     * that bundle Idiomorph themselves, want a different morph library, or
     * deliberately disable morphing (e.g. for SSR snapshot diffing).
     */
    setMorpher(morpher: Morpher): this {
        this.morpher = morpher;
        // Cancel the in-flight CDN load — the app has chosen explicitly.
        this.morphPromise = Promise.resolve(morpher);
        return this;
    }

    /**
     * Registers (or replaces) a handler for one node type. Returns
     * {@code this} for chaining. The node type {@code N} can be a core
     * {@link UiNode} subtype or any extension-defined shape that carries a
     * {@code type: string} discriminator.
     */
    register<N extends { type: string }>(type: N["type"], handler: NodeHandler<N>): this {
        this.handlers.set(type, handler as unknown as NodeHandler);
        return this;
    }

    /** Replaces the list-item handler. The default supports the full UiListItem shape. */
    registerItemHandler(handler: ItemHandler): this {
        this.itemHandler = handler;
        return this;
    }

    /** Returns true if a handler is registered for the given type. */
    has(type: string): boolean {
        return this.handlers.has(type);
    }

    /**
     * Renders a node tree to an HTML string. Unknown types fall back to a
     * {@code <pre>} dump with a {@code console.warn} — visible enough to
     * catch missing handlers in development without crashing the page.
     */
    render(node: { type: string } | null | undefined): string {
        if (node == null) return "";
        const handler = this.handlers.get(node.type);
        if (!handler) {
            console.warn("SuiRenderer: no handler for node type", node.type);
            return `<pre>${escapeHtml(JSON.stringify(node, null, 2))}</pre>`;
        }
        return handler(node, this);
    }

    /**
     * Binds the renderer to a host element (or rebinds it). Subsequent
     * {@link #mount} calls will replace the host's content with the rendered
     * tree. Returns {@code this} for chaining.
     */
    attach(rootElement: HTMLElement): this {
        this.rootElement = rootElement;
        return this;
    }

    /**
     * Returns the currently attached host element, or {@code null} when the
     * renderer is being used in pure-string mode (e.g. for patch dispatching
     * outside the host root).
     */
    root(): HTMLElement | null {
        return this.rootElement;
    }

    /**
     * Renders the node and merges the result into the attached host
     * element via the active morpher. Existing DOM nodes survive the swap
     * when they have matching {@code id}s, which preserves focus, scroll
     * position and CSS animation state on the unchanged subtree. Throws
     * when no host has been provided.
     */
    mount(node: { type: string } | null | undefined): this {
        if (!this.rootElement) {
            throw new Error("SuiRenderer.mount(): no host element attached");
        }
        this.morph(this.rootElement, this.render(node), "innerHTML");
        return this;
    }

    /**
     * Renders the node and replaces {@code element} itself (not just its
     * contents) via the active morpher. Use it when the re-render has to
     * refresh attributes on the element as well — a table whose
     * {@code data-node} model changed, for instance. Matching {@code id}s
     * still survive the swap, so focus and scroll are preserved.
     */
    replaceElement(element: HTMLElement, node: { type: string } | null | undefined): void {
        this.morph(element, this.render(node), "outerHTML");
    }

    /**
     * Renders the node and merges it into the given element via the active
     * morpher. Used by the patch dispatcher to replace sub-trees identified
     * by {@code id} without touching the surrounding DOM.
     */
    renderInto(element: HTMLElement, node: { type: string } | null | undefined): void {
        this.morph(element, this.render(node), "innerHTML");
    }

    /** Renders one list item. Exposed so list handlers can delegate. */
    renderItem(item: UiListItem): string {
        return this.itemHandler(item, this);
    }

    /**
     * Applies a server-issued patch. Each operation targets an element by
     * {@code id} and either replaces it, appends to it, or clears it.
     *
     * <ul>
     *   <li><b>REPLACE</b> morphs the target's outer HTML so focus,
     *       selection and CSS-animation state on the unchanged subtree
     *       survive the swap (via Idiomorph; falls back to plain outerHTML
     *       replacement while the library is still loading).</li>
     *   <li><b>APPEND</b> appends the rendered node to the target. When the
     *       node is a {@link UiList}, items are appended directly into the
     *       target's {@code <ul>} so the list header isn't duplicated; the
     *       sentinel {@code [data-id="empty"]} placeholder, if present, is
     *       removed first. If the user was scrolled to the bottom of the
     *       container, the scroll position is updated to follow the new
     *       tail; otherwise it is left alone (so a user reading older
     *       messages isn't yanked away).</li>
     *   <li><b>CLEAR</b> empties the target via the morpher.</li>
     *   <li><b>REMOVE</b> removes the target element itself from the DOM.
     *       When the target lives inside a list item, the wrapping {@code
     *       <li>} is dropped too, so transient placeholders disappear
     *       without leaving an empty row.</li>
     * </ul>
     *
     * <p>Read-position stability for prepended content is additionally
     * supported by the browser's native {@code overflow-anchor} (default in
     * {@code sui.css}).
     */
    applyPatch(patch: UiPatch | null | undefined): this {
        if (!patch || !patch.patches) return this;
        for (const op of patch.patches) {
            this.applyPatchOp(op);
        }
        return this;
    }

    private applyPatchOp(op: UiPatchOperation): void {
        const target = document.getElementById(op.targetId);
        if (!target) return;
        // Row/column patches inside a table are model updates, not DOM
        // morphs: the table re-renders from its embedded model so header,
        // cells and selection state stay consistent (a lone <tr>/<th> swap
        // couldn't re-render a column's cells or apply cellTemplates).
        if (this.applyTablePatch(op, target)) return;
        switch (op.op) {
            case "REPLACE": {
                if (!op.node) return;
                // A slot container (data-sui-slot) is part of a parent node's
                // own layout — the app shell's content area, for instance.
                // Replacing it outright would delete the container and its
                // layout classes along with the old content, so REPLACE on a
                // slot fills it instead. Everything else is a normal
                // outerHTML morph.
                const isSlot = target.hasAttribute("data-sui-slot");
                // Both APPEND and REPLACE can grow a chat-style scroll
                // container: APPEND adds a new item, REPLACE swaps a
                // streaming token-by-token message and the message gets
                // taller. Sample the scroller around either op so the
                // tail-chase fires for both.
                this.withTailChase(target, () => {
                    this.morph(target, this.render(op.node!), isSlot ? "innerHTML" : "outerHTML");
                });
                break;
            }
            case "APPEND": {
                if (!op.node) return;
                // For appends we deliberately don't morph: we want to add
                // new content, not reconcile against existing siblings.
                this.withTailChase(target, () => {
                    const type = (op.node as { type?: string }).type;
                    if (type === "list") {
                        this.appendListItems(target, op.node as { items?: UiListItem[] });
                    } else {
                        // Tree rows are <li>s that belong inside the tree's
                        // <ul> (root list or a node's children list), not at
                        // the end of the targeted container itself.
                        const host = type === "tree-node"
                            ? this.treeAppendHost(target) ?? target
                            : target;
                        const tmp = document.createElement("div");
                        tmp.innerHTML = this.render(op.node!);
                        while (tmp.firstChild) host.appendChild(tmp.firstChild);
                    }
                });
                break;
            }
            case "CLEAR":
                this.morph(target, "", "innerHTML");
                break;
            case "REMOVE": {
                // Drop the target element entirely. If the target sits
                // inside a list item (<li>), drop the wrapping <li> so we
                // don't leave behind an empty list row.
                const li = target.closest("li");
                (li ?? target).remove();
                break;
            }
        }
    }

    /**
     * Runs {@code mutate} and, if the surrounding scroll container was
     * already at the bottom before the mutation, scrolls it back to the
     * bottom afterwards. The container is found by walking up from
     * {@code target} until a vertically-scrollable ancestor is found, then
     * looking inside {@code target} if none up the tree qualifies (chat
     * pattern: list-div with inner scrolling {@code <ul>}).
     *
     * <p>The tail-chase scroll is deferred to the next animation frame so
     * the browser has finished its layout pass after {@code mutate} —
     * otherwise {@code scrollHeight} can still be stale and we end up
     * scrolling to the pre-mutation bottom, which leaves the appended
     * content hidden below the fold. A second {@code requestAnimationFrame}
     * pin covers the common case where the appended subtree contains
     * Markdown that paints in a follow-up frame (e.g. when the markdown
     * extension is still resolving its CDN import on first use).
     */
    private withTailChase(target: HTMLElement, mutate: () => void): void {
        const scroller = ancestorScroller(target) ?? findScroller(target);
        const wasAtBottom = scroller != null && isAtBottom(scroller);
        mutate();
        if (wasAtBottom && scroller) {
            requestAnimationFrame(() => {
                scroller.scrollTop = scroller.scrollHeight;
                requestAnimationFrame(() => {
                    scroller.scrollTop = scroller.scrollHeight;
                });
            });
        }
    }

    /**
     * Handles patch ops that address a table's rows or columns. Tables
     * render with their full model embedded as {@code data-node} (see
     * renderTable); a matching patch edits that model and re-renders the
     * whole table through the morpher, which keeps thead/tbody/selection
     * consistent and preserves focus/scroll.
     *
     * <p>Handled cases — returns {@code true} when consumed:
     * <ul>
     *   <li>{@code REPLACE} a {@code row}/{@code column} node whose target
     *       id matches a model row/column;</li>
     *   <li>{@code REMOVE} where the target id matches a model row/column;</li>
     *   <li>{@code APPEND} a {@code row} node targeting the table itself
     *       (appends to {@code rows}; an existing id is replaced instead so
     *       repeated appends stay idempotent).</li>
     * </ul>
     * Anything else (e.g. patching a cell-template subtree, whose suffixed
     * ids never match model entries) falls back to the generic DOM path.
     */
    private applyTablePatch(op: UiPatchOperation, target: HTMLElement): boolean {
        const wrapper = target.closest<HTMLElement>('[data-sui="table"][data-node]');
        if (!wrapper) return false;
        let model: { rows?: Array<{ id?: string }>; columns?: Array<{ id?: string }> };
        try { model = JSON.parse(wrapper.getAttribute("data-node")!); }
        catch { return false; }
        const rows = model.rows ?? (model.rows = []);
        const cols = model.columns ?? (model.columns = []);
        const type = (op.node as { type?: string } | undefined)?.type;

        let changed = false;
        if (op.op === "APPEND" && target === wrapper && type === "row") {
            const idx = rows.findIndex(x => x.id != null && x.id === (op.node as { id?: string }).id);
            if (idx >= 0) rows[idx] = op.node as never; else rows.push(op.node as never);
            changed = true;
        } else if (target !== wrapper && op.op === "REPLACE" && (type === "row" || type === "column")) {
            const list = type === "row" ? rows : cols;
            const idx = list.findIndex(x => x.id === op.targetId);
            if (idx < 0) return false;
            list[idx] = op.node as never;
            changed = true;
        } else if (target !== wrapper && op.op === "REMOVE") {
            const ri = rows.findIndex(x => x.id === op.targetId);
            const ci = ri < 0 ? cols.findIndex(x => x.id === op.targetId) : -1;
            if (ri < 0 && ci < 0) return false;
            if (ri >= 0) rows.splice(ri, 1); else cols.splice(ci, 1);
            changed = true;
        }
        if (!changed) return false;
        this.withTailChase(wrapper, () => {
            this.morph(wrapper, this.render(model as never), "outerHTML");
        });
        return true;
    }

    /**
     * Resolves where an APPENDed {@code tree-node} <li> should land within
     * {@code target}:
     * <ul>
     *   <li>target is the tree container → its root {@code .sui-tree-list};</li>
     *   <li>target is an expandable tree row → its {@code .sui-tree-children}
     *       list (created on the fly when the row has a body but no children
     *       yet, e.g. content-only nodes);</li>
     *   <li>anything else → {@code null}, caller falls back to the target
     *       itself. A collapsed leaf can't grow children this way — REPLACE
     *       the row instead.</li>
     * </ul>
     */
    private treeAppendHost(target: HTMLElement): HTMLElement | null {
        const rootList = target.querySelector<HTMLElement>(":scope > ul.sui-tree-list");
        if (rootList) return rootList;
        const body = target.querySelector<HTMLElement>(":scope > details > .sui-tree-body");
        if (!body) return null;
        let children = body.querySelector<HTMLElement>(":scope > ul.sui-tree-children");
        if (!children) {
            children = document.createElement("ul");
            children.className = "sui-tree-children";
            children.setAttribute("role", "group");
            body.appendChild(children);
        }
        return children;
    }

    private appendListItems(target: HTMLElement, node: { items?: UiListItem[] }): void {
        const ul = target.querySelector("ul");
        if (!ul) return;
        // Conventional placeholder used by empty-state list rendering:
        // <li data-id="empty">…</li>. Drop it on the first real item.
        const placeholder = ul.querySelector('[data-id="empty"]');
        if (placeholder) placeholder.remove();
        for (const item of (node.items ?? [])) {
            const tmp = document.createElement("ul");
            tmp.innerHTML = this.renderItem(item);
            const first = tmp.firstElementChild;
            if (first) ul.appendChild(first);
        }
    }

    // ── Loading indicator ─────────────────────────────────────────────────

    /**
     * Replaces the loading indicator implementation. The default draws a
     * subtle overlay on top of the root element with a progress bar; apps
     * with their own loading aesthetic (spinner in the header, branded
     * shimmer, …) replace it here. {@link #showLoading} and
     * {@link #hideLoading} use the new implementation immediately.
     */
    setLoadingIndicator(indicator: LoadingIndicator): this {
        this.loadingIndicator = indicator;
        return this;
    }

    /**
     * Shows the loading indicator. Reference-counted: concurrent dispatches
     * (e.g. an SSE stream over a navigation) increment the counter, only
     * the matching number of {@link #hideLoading} calls hides it. Apps
     * usually don't call this directly — the EventBus does, around every
     * behaviour dispatch.
     */
    showLoading(): void {
        if (!this.rootElement) return;
        this.loadingDepth++;
        if (this.loadingDepth === 1) this.loadingIndicator.show(this.rootElement);
    }

    /** Counterpart to {@link #showLoading}. Safe to call when not loading. */
    hideLoading(): void {
        if (!this.rootElement) return;
        if (this.loadingDepth === 0) return;
        this.loadingDepth--;
        if (this.loadingDepth === 0) this.loadingIndicator.hide(this.rootElement);
    }
}

/**
 * Default loading indicator: lazily attaches a {@code .sui-loading-overlay}
 * element to the root and toggles a {@code .sui-loading} class on the root
 * to drive the CSS. The overlay itself is a single absolutely-positioned
 * {@code <div>} with a progress bar pseudo-element; the {@code position:
 * relative} requirement on the root is set inline so consumers don't have
 * to remember it in their CSS.
 */
const defaultLoadingIndicator: LoadingIndicator = {
    show(root: HTMLElement) {
        ensureRootIsPositioned(root);
        let overlay = root.querySelector<HTMLElement>(":scope > .sui-loading-overlay");
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.className = "sui-loading-overlay";
            overlay.innerHTML = '<div class="sui-loading-bar"></div>';
            root.appendChild(overlay);
        }
        root.classList.add("sui-loading");
    },
    hide(root: HTMLElement) {
        root.classList.remove("sui-loading");
    },
};

function ensureRootIsPositioned(root: HTMLElement): void {
    const cs = getComputedStyle(root);
    if (cs.position === "static") root.style.position = "relative";
}

// ── HTML escaping ───────────────────────────────────────────────────────────

const HTML_ESCAPE_MAP: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
};

/**
 * Escapes user-supplied data for safe interpolation into HTML element bodies
 * and double-quoted attribute values. The replacement set also covers
 * single quotes, which lets the same helper guard {@code data-*} attributes
 * we emit with single-quoted JSON payloads.
 */
export function escapeHtml(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE_MAP[ch]!);
}

/**
 * Encodes a {@link UiTrigger} as a single-quoted attribute payload. The
 * returned string contains JSON whose single quotes are HTML-escaped to
 * {@code &#39;}, so it can be embedded inside {@code data-trigger='…'}
 * without breaking the DOM parser.
 */
export function encodeTrigger(trigger: UiTrigger): string {
    return JSON.stringify(trigger).replace(/'/g, "&#39;");
}

// ── Default handlers ────────────────────────────────────────────────────────

/**
 * Wires the standard semantic-ui node types into a renderer. Application code
 * calls this once after construction, then registers its own overrides.
 *
 * <p>Each render function lives in its own file under {@code ./renderers/}.
 * That keeps this module's concern small (registry + dispatcher + utilities)
 * and matches the SSR side, where every node type owns a {@code .hbs}
 * template file under {@code templates/sui/}.
 */
export function installDefaultHandlers(renderer: SuiRenderer): SuiRenderer {
    return renderer
        .register<UiList>("list",                renderList)
        .register<UiTree>("tree",                renderTree)
        .register<UiTreeNode>("tree-node",       renderTreeNode)
        .register<UiMenu>("menu",                renderMenu)
        .register<UiMenuItem>("menu-item",       renderMenuItem)
        .register<UiMenuButton>("menu-button",   renderMenuButton)
        .register<UiForm>("form",                renderForm)
        .register<UiDetail>("detail",            renderDetail)
        .register<UiSection>("section",          renderSection)
        .register<UiSectionEntry>("section-entry", renderSectionEntry)
        .register<UiStack>("stack",              renderStack)
        .register<UiAppShell>("app-shell",       renderAppShell)
        .register<UiTable>("table",              renderTable)
        .register<UiTableColumn>("column",       renderColumn)
        .register<UiTableRow>("row",             renderRow)
        .register<UiHeader>("header",            renderHeader)
        .register<UiText>("text",                renderText)
        // Top-level handlers for nodes that were historically only emitted
        // as sub-elements by their containers (UiForm.fields, UiDetail.links,
        // …). They're addressable as standalone nodes now so cellTemplate
        // can drop a single link / action / field straight into a table
        // cell. Container-internal callers still call the helpers in
        // {@code renderers/shared.ts} which preserves the original markup.
        .register<UiLink>("link",                renderLink)
        .register<UiAction>("action",            renderAction)
        .register<UiField>("field",              renderField)
        .register<UiFieldGroup>("fieldgroup",    renderFieldGroup)
        .register<UiDialog>("dialog",            renderDialog)
        .register<UiUpload>("upload",            renderUpload)
        .register<UiIcon>("icon",                renderIconNode)
        .register<UiSpinner>("spinner",          renderSpinner)
        .register<UiProgress>("progress",        renderProgress);
}

/** Convenience: a fresh renderer pre-loaded with the default handlers. */
export function createDefaultRenderer(): SuiRenderer {
    return installDefaultHandlers(new SuiRenderer());
}

// ── Morpher ─────────────────────────────────────────────────────────────────
//
// The renderer delegates every DOM swap (mount + REPLACE/CLEAR patch ops) to
// a Morpher function so it can be swapped at runtime. The default loads
// Idiomorph from a CDN lazily; until that resolves, the fallback below uses
// plain innerHTML / outerHTML replacement (functionally identical, just
// without focus / scroll / animation preservation).

/**
 * Merges {@code newContent} (a rendered HTML string) into {@code target}.
 * {@code mode} chooses whether the target itself is replaced
 * ({@code "outerHTML"}, used by REPLACE patches) or only its children
 * ({@code "innerHTML"}, used by mount and CLEAR).
 */
export type Morpher = (target: HTMLElement, newContent: string, mode: "innerHTML" | "outerHTML") => void;

/** Fallback morpher: plain string assignment. Used before Idiomorph loads. */
const innerHtmlMorpher: Morpher = (target, newContent, mode) => {
    if (mode === "outerHTML") target.outerHTML = newContent;
    else                      target.innerHTML = newContent;
};

/** Shape we expect from the Idiomorph ESM module. */
interface IdiomorphLib {
    morph(
        target: Node,
        newContent: string | Node,
        opts?: {
            morphStyle?: "outerHTML" | "innerHTML";
            ignoreActiveValue?: boolean;
            callbacks?: {
                beforeAttributeUpdated?: (
                    attributeName: string,
                    node: Element,
                    mutationType: "update" | "remove",
                ) => boolean | void;
            };
        },
    ): void;
}

const IDIOMORPH_URL = "https://cdn.jsdelivr.net/npm/idiomorph@0.7.4/+esm";

/**
 * Builds a Morpher that delegates to a resolved Idiomorph instance.
 *
 * <p>Two pieces of user-owned state are protected from server re-renders:
 * the value of the field the user is currently editing ({@code ignoreActiveValue}),
 * and the open/closed state of any {@code <details data-sui-client-collapse>}
 * (via {@code beforeAttributeUpdated}). The latter lets live-updating cards —
 * tool calls, sub-agent activity — keep whatever the user manually expanded or
 * collapsed, instead of snapping back to the server's idea of {@code open} on
 * every streaming patch.
 */
function idiomorphMorpher(lib: IdiomorphLib): Morpher {
    return (target, newContent, mode) => {
        lib.morph(target, newContent, {
            morphStyle: mode,
            // Don't clobber what the user is currently typing — the
            // server's view of the form value is, by definition, stale
            // while the user is still editing.
            ignoreActiveValue: true,
            callbacks: {
                beforeAttributeUpdated: (attributeName, node) => {
                    // Leave the `open` attribute alone on client-controlled
                    // <details>: its expand/collapse is owned by the user, not
                    // the server. Returning false tells Idiomorph to skip it.
                    if (attributeName === "open"
                        && node.hasAttribute("data-sui-client-collapse")) {
                        return false;
                    }
                    return undefined;
                },
            },
        });
    };
}

/**
 * Heuristic: "is the user looking at the bottom of this container right
 * now?". Used by the APPEND patch op to decide whether to chase the tail
 * (chat-message arrived, user was already at the bottom) or hold position
 * (user scrolled up to read older messages, leave them where they are).
 */
function isAtBottom(el: HTMLElement, thresholdPx = 40): boolean {
    return el.scrollHeight - el.scrollTop - el.clientHeight <= thresholdPx;
}

/**
 * Returns the element that actually scrolls vertically for the given
 * append target. If the target itself has overflow-y, that's the answer;
 * otherwise we look for the first descendant that does. Used so a UiList
 * whose inner {@code <ul>} is the scroll container still gets
 * tail-chased correctly when items are appended into the {@code <ul>}.
 *
 * <p>Note we check {@code overflow-y} (the CSS rule) rather than
 * {@code scrollHeight > clientHeight} (the dynamic state). The latter
 * would return {@code null} for an empty container that's about to
 * receive its first item — exactly the case we need to handle for the
 * very first chat message.
 */
function findScroller(el: HTMLElement): HTMLElement | null {
    if (canScrollVertically(el)) return el;
    const candidates = Array.from(el.querySelectorAll<HTMLElement>("*"));
    for (const c of candidates) {
        if (canScrollVertically(c)) return c;
    }
    return null;
}

/**
 * Walks up the DOM from {@code el} until a vertically-scrollable element
 * is found. Used by the REPLACE patch op: the patch target itself (a chat
 * message, say) is never scrollable, but its scrolling ancestor
 * (the message list's {@code <ul>}) is the one we need to chase.
 */
function ancestorScroller(el: HTMLElement): HTMLElement | null {
    let cur: HTMLElement | null = el.parentElement;
    while (cur && cur !== document.body) {
        if (canScrollVertically(cur)) return cur;
        cur = cur.parentElement;
    }
    return null;
}

function canScrollVertically(el: HTMLElement): boolean {
    const overflowY = getComputedStyle(el).overflowY;
    return overflowY === "auto" || overflowY === "scroll" || overflowY === "overlay";
}
