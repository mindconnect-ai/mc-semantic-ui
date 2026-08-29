import type { UiNode, UiListItem, UiTrigger, UiPatch } from "./model.js";
export { renderIcon, setIconResolver, setIconSpriteUrl, spriteIconResolver, type IconResolver, type IconOpts, } from "./renderers/icon.js";
export { restoreMenuState, applyMenuState, cycleMenuState, menuStateOf, MENU_STATES, type MenuState, } from "./renderers/menu.js";
export { wireOverflow } from "./renderers/overflow.js";
export { wireTabOverflow } from "./renderers/tabs.js";
export { wireHeaderOverflow } from "./renderers/header-overflow.js";
export { wireMenuButtons } from "./renderers/menu-button.js";
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
export type NodeHandler<N extends {
    type: string;
} = UiNode> = (node: N, renderer: SuiRenderer) => string;
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
export declare class SuiRenderer {
    private readonly handlers;
    private itemHandler;
    private rootElement;
    /**
     * The model each rendered id came from, for {@link #applyPatch}'s MERGE.
     *
     * <p>Only what this renderer has drawn is in here. A page delivered as
     * server-rendered HTML was never drawn by the client, so a merge against
     * one of its nodes has nothing to merge into until something re-renders
     * that subtree — navigating, or any other patch. That is a deliberate
     * limit: the alternative is the server writing every node's model into the
     * HTML, which doubles the size of every page to serve the rare merge.
     */
    private readonly models;
    private loadingDepth;
    private loadingIndicator;
    private morpher;
    private morphPromise;
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
    constructor(rootElement?: HTMLElement | null);
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
    private morph;
    /**
     * Loads Idiomorph from the CDN and installs it as the active morpher.
     * Called on the first DOM write, so the first paint is almost always the
     * innerHTML fallback; every subsequent swap uses Idiomorph.
     */
    private kickoffMorphLoad;
    /**
     * Replaces the morpher with a custom implementation. Useful for apps
     * that bundle Idiomorph themselves, want a different morph library, or
     * deliberately disable morphing (e.g. for SSR snapshot diffing).
     */
    setMorpher(morpher: Morpher): this;
    /**
     * Registers (or replaces) a handler for one node type. Returns
     * {@code this} for chaining. The node type {@code N} can be a core
     * {@link UiNode} subtype or any extension-defined shape that carries a
     * {@code type: string} discriminator.
     */
    register<N extends {
        type: string;
    }>(type: N["type"], handler: NodeHandler<N>): this;
    /** Replaces the list-item handler. The default supports the full UiListItem shape. */
    registerItemHandler(handler: ItemHandler): this;
    /** Returns true if a handler is registered for the given type. */
    has(type: string): boolean;
    /**
     * Renders a node tree to an HTML string. Unknown types fall back to a
     * {@code <pre>} dump with a {@code console.warn} — visible enough to
     * catch missing handlers in development without crashing the page.
     */
    render(node: {
        type: string;
    } | null | undefined): string;
    /**
     * Binds the renderer to a host element (or rebinds it). Subsequent
     * {@link #mount} calls will replace the host's content with the rendered
     * tree. Returns {@code this} for chaining.
     */
    attach(rootElement: HTMLElement): this;
    /**
     * Returns the currently attached host element, or {@code null} when the
     * renderer is being used in pure-string mode (e.g. for patch dispatching
     * outside the host root).
     */
    root(): HTMLElement | null;
    /**
     * Renders the node and merges the result into the attached host
     * element via the active morpher. Existing DOM nodes survive the swap
     * when they have matching {@code id}s, which preserves focus, scroll
     * position and CSS animation state on the unchanged subtree. Throws
     * when no host has been provided.
     */
    mount(node: {
        type: string;
    } | null | undefined): this;
    /**
     * Renders the node and replaces {@code element} itself (not just its
     * contents) via the active morpher. Use it when the re-render has to
     * refresh attributes on the element as well — a table whose
     * {@code data-node} model changed, for instance. Matching {@code id}s
     * still survive the swap, so focus and scroll are preserved.
     */
    replaceElement(element: HTMLElement, node: {
        type: string;
    } | null | undefined): void;
    /**
     * Renders the node and merges it into the given element via the active
     * morpher. Used by the patch dispatcher to replace sub-trees identified
     * by {@code id} without touching the surrounding DOM.
     */
    renderInto(element: HTMLElement, node: {
        type: string;
    } | null | undefined): void;
    /**
     * Seeds the model index from a tree this renderer did not draw.
     *
     * <p>A hybrid page arrives as finished HTML with its model parked in a
     * `<script type="application/json">`; walking it here is what lets a MERGE
     * work on the very first patch, before anything has been re-rendered.
     *
     * <p>Additive: it fills in what it walks and disturbs nothing else, so
     * calling it on an already-live page is harmless.
     */
    seedModels(node: {
        type: string;
    } | null | undefined): this;
    /** Walks a tree, remembering every node that has an id. */
    private indexModel;
    /** Renders one list item. Exposed so list handlers can delegate. */
    renderItem(item: UiListItem): string;
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
    applyPatch(patch: UiPatch | null | undefined): this;
    private applyPatchOp;
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
    /**
     * Whether APPEND animates its new elements in and REMOVE animates its
     * target out. On by default; set to {@code false} for a renderer driving
     * a screen where motion is wrong (a test harness, a print view, a
     * high-frequency feed).
     *
     * <p>Only these two operations animate, and that is deliberate. REPLACE
     * and MERGE are the streaming path — a chat turn REPLACEs the same node
     * once per token — so an enter animation there would restart several
     * times a second. APPEND and REMOVE are the only operations that put an
     * element on the page or take one off, which is exactly when an
     * animation has something to say.
     */
    animatePatches: boolean;
    /** Longest an enter/leave may take before it is cleaned up regardless. */
    private static readonly ANIMATION_TIMEOUT_MS;
    /**
     * Whether a patch should animate at all.
     *
     * <p>The hidden-document check is not an optimisation. Browsers do not
     * run {@code requestAnimationFrame} in a background tab, and both
     * animations below need a frame between setting up and starting — so in
     * a tab nobody is looking at they would freeze halfway and be cleaned up
     * by their timeout. Skipping outright gets the same result immediately,
     * and the user sees the finished state when they come back, which is
     * what they would have seen anyway.
     */
    private shouldAnimate;
    /**
     * Shared by every animation here: is motion wanted, and would it run?
     *
     * <p>Asked defensively, because this is the one thing in the renderer that
     * has to know about the environment it is in — and the string API is
     * documented to run on a Node backend with no DOM at all. Anything that is
     * not a browser answers "no", which is the right answer there anyway:
     * there is no frame to paint. So a missing `window`, a `document` that
     * throws on access, a `matchMedia` that is not a function — all of them
     * mean the same thing, and none of them is worth an exception escaping
     * into a patch.
     */
    private motionAllowed;
    /**
     * Whether a whole-view swap should go through the View Transition API.
     * On where the browser supports it; set to {@code false} to opt out.
     *
     * <p>Used for navigation only — {@link #mount} and a REPLACE that fills a
     * slot, which is what an app shell's content area is. Not for the other
     * operations: APPEND and REMOVE bring their own animation, and REPLACE on
     * anything else is the streaming path, where a full-page snapshot per
     * token would be a disaster.
     */
    viewTransitions: boolean;
    private shouldViewTransition;
    /**
     * Runs a whole-view swap inside a view transition where one is available,
     * and plainly where it is not.
     *
     * <p>The callback is deliberately allowed to run later than the call: the
     * API captures the old frame first and only then applies the change.
     * Nothing here depends on the DOM having changed by the time this
     * returns — the event bus re-runs its enhancers from a MutationObserver,
     * so they fire whenever the mutation actually lands.
     */
    private withViewTransition;
    /**
     * Marks freshly appended elements so the stylesheet can animate them in,
     * and takes the class back off once it has played.
     *
     * <p>The animation `sui.css` gives them moves opacity and transform only.
     * That is not a stylistic choice: APPEND runs inside
     * {@link #withTailChase}, which decides whether to follow the tail by
     * measuring the scroll container. An entering element that animated its
     * height would be measured mid-flight, and a chat would scroll to the
     * wrong place on every message.
     */
    private animateEnter;
    /**
     * Plays the element out and removes it when the transition ends.
     *
     * <p>Height is animated here, unlike on enter: REMOVE is not inside a
     * tail-chase, and a row that fades but keeps its space until it vanishes
     * reads as a bug. The height has to start from a concrete pixel value
     * for a transition to have anything to interpolate, so it is frozen
     * first and only collapsed in the next frame — a class that both
     * declared the transition and changed the value in one recalculation
     * would jump instead.
     */
    /** Longest of the element's declared transitions (delay included), in ms. */
    private transitionMillis;
    /**
     * Removes an element, letting it play out first: it takes the
     * {@code .sui-leave} class, and is dropped when the transition the
     * stylesheet declared for it has run. An element that is out of the
     * document flow — a dialog host, a toast — only fades; one in the flow
     * also collapses the space it held, so the rows below it close the gap
     * instead of jumping into it.
     *
     * <p>Public because REMOVE is not the only way something leaves the
     * page: the event bus closes a dialog on a backdrop click without ever
     * asking the server, and that should look the same as a close the server
     * ordered.
     *
     * <p>Falls back to a plain removal whenever an animation would not run
     * or would not be wanted — reduced motion, a hidden tab, a stylesheet
     * that declares no transition for the class.
     */
    removeAnimated(element: Element): void;
    private animateLeave;
    private withTailChase;
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
    /**
     * The target with {@code op.attributes} written over it.
     *
     * <p>A shallow merge on purpose: naming a field replaces it whole, so
     * {@code items} or {@code fields} can be swapped without the server
     * describing how to reconcile a list. Deep-merging arrays is a much bigger
     * promise than this operation is making.
     *
     * @return {@code null} when the target was never rendered here, which is
     *         also logged — a merge that quietly does nothing is worse than
     *         one that says why
     */
    private mergeModel;
    private applyTablePatch;
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
    private treeAppendHost;
    private appendListItems;
    /**
     * Replaces the loading indicator implementation. The default draws a
     * subtle overlay on top of the root element with a progress bar; apps
     * with their own loading aesthetic (spinner in the header, branded
     * shimmer, …) replace it here. {@link #showLoading} and
     * {@link #hideLoading} use the new implementation immediately.
     */
    setLoadingIndicator(indicator: LoadingIndicator): this;
    /**
     * Shows the loading indicator. Reference-counted: concurrent dispatches
     * (e.g. an SSE stream over a navigation) increment the counter, only
     * the matching number of {@link #hideLoading} calls hides it. Apps
     * usually don't call this directly — the EventBus does, around every
     * behaviour dispatch.
     */
    showLoading(): void;
    /** Counterpart to {@link #showLoading}. Safe to call when not loading. */
    hideLoading(): void;
}
/**
 * Escapes user-supplied data for safe interpolation into HTML element bodies
 * and double-quoted attribute values. The replacement set also covers
 * single quotes, which lets the same helper guard {@code data-*} attributes
 * we emit with single-quoted JSON payloads.
 */
export declare function escapeHtml(value: unknown): string;
/**
 * Encodes a {@link UiTrigger} as a single-quoted attribute payload. The
 * returned string contains JSON whose single quotes are HTML-escaped to
 * {@code &#39;}, so it can be embedded inside {@code data-trigger='…'}
 * without breaking the DOM parser.
 */
export declare function encodeTrigger(trigger: UiTrigger): string;
/**
 * Wires the standard semantic-ui node types into a renderer. Application code
 * calls this once after construction, then registers its own overrides.
 *
 * <p>Each render function lives in its own file under {@code ./renderers/}.
 * That keeps this module's concern small (registry + dispatcher + utilities)
 * and matches the SSR side, where every node type owns a {@code .hbs}
 * template file under {@code templates/sui/}.
 */
export declare function installDefaultHandlers(renderer: SuiRenderer): SuiRenderer;
/** Convenience: a fresh renderer pre-loaded with the default handlers. */
export declare function createDefaultRenderer(): SuiRenderer;
/**
 * Merges {@code newContent} (a rendered HTML string) into {@code target}.
 * {@code mode} chooses whether the target itself is replaced
 * ({@code "outerHTML"}, used by REPLACE patches) or only its children
 * ({@code "innerHTML"}, used by mount and CLEAR).
 */
export type Morpher = (target: HTMLElement, newContent: string, mode: "innerHTML" | "outerHTML") => void;
