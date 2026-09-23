import type { UiNode } from "./model.js";

/**
 * What is on the screen right now, as data.
 *
 * <p>The renderer keeps a copy of the tree it drew (see
 * {@code SuiRenderer#tree}); this module turns a subtree of that copy into
 * something small enough to hand to a reader that is not looking at the
 * screen — a test, a support session, an agent. Two shapes:
 *
 * <ul>
 *   <li>{@code "outline"} (the default) — only what someone acting on the
 *       screen needs: what it is called, what can be read, what can be typed
 *       into and what can be pressed. Everything else the node carries
 *       (styling, triggers, layout hints) is left out.</li>
 *   <li>{@code "full"} — the node as it was rendered, for debugging: every
 *       field of the model, with the same omissions for secrets.</li>
 * </ul>
 *
 * <p>Values are read from the DOM in both shapes, not from the model: a field
 * the user has typed into reads back what it says now, not what the server
 * last sent. That is the whole point — the snapshot describes the screen, and
 * the screen is the DOM.
 */

/** Which shape {@link buildSnapshot} produces. */
export type SnapshotMode = "outline" | "full";

export interface SnapshotOptions {
    /** Only this subtree. Omitted: the whole page. */
    root?: string;
    /** How many levels below the root are walked. Default {@link DEFAULT_DEPTH}. */
    depth?: number;
    /** Default {@code "outline"}. */
    mode?: SnapshotMode;
    /** Rough ceiling on the serialized result. Default {@link DEFAULT_MAX_CHARS}. */
    maxChars?: number;
}

/** A field as the snapshot reports it — label, kind, and the live value. */
export interface SnapshotField {
    id: string;
    label?: string;
    /** The semantic kind: {@code TEXT}, {@code SELECT}, {@code RICHTEXT} … */
    type?: string;
    /** What the control says right now. Absent when {@link #omitted}. */
    value?: unknown;
    /** True when the value is withheld — a password, a token, a file. */
    omitted?: boolean;
    options?: Array<{ value: string; label: string }>;
    required?: boolean;
    /** The server's complaint about the current value, when it made one. */
    error?: string;
    /** The action sitting on the control's row, when the field has one. */
    action?: SnapshotAction;
}

/** Anything that can be pressed: a button, a menu entry, a clickable row. */
export interface SnapshotAction {
    id: string;
    label?: string;
    style?: string;
    /** The question asked before this fires; {@code perform} must answer it. */
    confirm?: string;
    /** False when the control is disabled on screen right now. */
    enabled?: boolean;
    disabledReason?: string;
}

/** One row of a list, or of a table. */
export interface SnapshotItem {
    id: string;
    label?: string;
    description?: string;
    /** A table row's cells, by column id — what the row says on screen. */
    cells?: Record<string, unknown>;
    /** A table row's tick, when the table lets rows be picked. */
    selected?: boolean;
    /** True when the row itself is clickable — {@code perform} takes its id. */
    clickable?: boolean;
    fields?: SnapshotField[];
    actions?: SnapshotAction[];
    children?: SnapshotNode[];
    truncated?: boolean;
}

/** A table column, as the header shows it. */
export interface SnapshotColumn {
    id: string;
    label?: string;
}

/** Which page of a longer table (or list) is on screen. */
export interface SnapshotPagination {
    page: number;
    size: number;
    total: number;
}

/** One node of the outline. */
export interface SnapshotNode {
    id?: string;
    type: string;
    title?: string;
    label?: string;
    text?: string;
    /** A drawer's {@code open}/{@code minimized}/{@code closed}, read from the DOM. */
    state?: string;
    /** A table's columns, in the order the header shows them. */
    columns?: SnapshotColumn[];
    items?: SnapshotItem[];
    fields?: SnapshotField[];
    actions?: SnapshotAction[];
    children?: SnapshotNode[];
    /** Says the rows on screen are one page of more. */
    pagination?: SnapshotPagination;
    /** True when this node's content was cut — by {@code depth} or {@code maxChars}. */
    truncated?: boolean;
}

/** What {@code bus.snapshot()} returns. */
export interface Snapshot {
    /** Which bus answered — see {@code SuiEventBus#id}. */
    busId: string;
    mode: SnapshotMode;
    /**
     * The outline (or the full model); {@code null} when there was nothing to
     * show. A {@code root} that names a field or an action is described as
     * one, so the answer can be a {@link SnapshotField} or a
     * {@link SnapshotAction} instead of a node.
     */
    node: SnapshotNode | SnapshotField | SnapshotAction | Record<string, unknown> | null;
    /** True when anything was left out to stay inside {@code maxChars} or {@code depth}. */
    truncated?: boolean;
    /**
     * Why {@link #node} is null: nothing rendered yet, no such root, or a
     * {@code maxChars} too small to hold even the root node.
     */
    reason?: "no-tree" | "unknown-root" | "too-small";
}

/** The bits of the DOM the snapshot needs. Injected, so this module stays testable. */
export interface SnapshotDom {
    /** The element an id was rendered as, or null. */
    byId(id: string): HTMLElement | null;
    /**
     * The values the controls under (or at) an element carry right now, keyed
     * by field id — the event bus's own form harvest, so a snapshot value and
     * a submitted value are read the same way.
     */
    values(element: HTMLElement): Record<string, unknown>;
}

export const DEFAULT_DEPTH = 20;
export const DEFAULT_MAX_CHARS = 20_000;

/**
 * Field kinds whose value never leaves the browser. A password is a secret, a
 * file input's value is a path into someone's machine (and its content is not
 * text at all).
 */
const SECRET_FIELD_TYPES = new Set(["PASSWORD", "FILE"]);

/**
 * Names that make a value a secret whatever its kind — the hidden input
 * carrying a CSRF token is an ordinary {@code HIDDEN} field, and an agent has
 * no business reading it out of the page.
 */
const SECRET_NAME = /(pass(word|phrase)?|secret|token|csrf|xsrf|nonce|api[-_ ]?key|credential|authorization)/i;

/** Whether a value is a node: something with a type discriminator. */
function isNode(value: unknown): value is Record<string, unknown> & { type: string } {
    return value != null && typeof value === "object" && !Array.isArray(value)
        && typeof (value as { type?: unknown }).type === "string";
}

/** Whether a field's value is withheld from the snapshot. */
export function isSecretField(field: { id?: string; label?: string; fieldType?: string }): boolean {
    if (field.fieldType && SECRET_FIELD_TYPES.has(field.fieldType)) return true;
    return SECRET_NAME.test(field.id ?? "") || SECRET_NAME.test(field.label ?? "");
}

/**
 * Tracks what is left of the {@code maxChars} budget.
 *
 * <p>Every entry charges its own content when it is built, before its
 * children are walked — so a node is counted once, not once per level above
 * it. The first entry that does not fit ends the walk: what has been built
 * stays, nothing further is added, and everything from there up says
 * {@code truncated}. Whole entries, so the count is close rather than exact —
 * the result lands under {@code maxChars} with the punctuation of nesting as
 * the margin.
 */
class Budget {
    private left: number;
    /** True once anything was left out — surfaces as {@code truncated}. */
    cut = false;

    constructor(maxChars: number) {
        // The envelope ({busId, mode, node, truncated}) is not free either.
        this.left = Math.max(0, maxChars - 64);
    }

    /** Charges {@code value}'s own serialized size; false when it does not fit. */
    take(value: unknown): boolean {
        let size: number;
        try { size = JSON.stringify(value)?.length ?? 0; }
        catch { size = 0; }
        if (this.cut || size > this.left) { this.cut = true; return false; }
        this.left -= size;
        return true;
    }
}

interface Ctx {
    dom: SnapshotDom;
    budget: Budget;
    mode: SnapshotMode;
    /** What handlers are given; built once per pass, not per node. */
    api: OutlineContext;
    /** How a node type describes itself; absent means the general rules. */
    outlineFor?: OutlineLookup;
    /**
     * True once {@code depth} has cut something. Deliberately not the budget's
     * flag: a spent budget ends the whole walk, a depth limit ends one branch
     * and the siblings beside it still belong in the answer.
     */
    depthCut: boolean;
}

/**
 * Finds a node by id in a tree. The walk is generic — every object with a
 * {@code type} is a node, wherever it hangs — so a plugin's own node shape is
 * found like a built-in one.
 */
export function findNode(tree: unknown, id: string, seen: Set<unknown> = new Set()): Record<string, unknown> | null {
    // A node that points back at an ancestor is not a tree any more, and an
    // application is free to build one (a row keeping a reference to its
    // table). Without this the walk would recurse until the stack gave out.
    if (tree == null || typeof tree !== "object" || seen.has(tree)) return null;
    seen.add(tree);
    if (Array.isArray(tree)) {
        for (const entry of tree) {
            const hit = findNode(entry, id, seen);
            if (hit) return hit;
        }
        return null;
    }
    const record = tree as Record<string, unknown>;
    if (record.id === id && typeof record.type === "string") return record;
    for (const value of Object.values(record)) {
        if (value != null && typeof value === "object") {
            const hit = findNode(value, id, seen);
            if (hit) return hit;
        }
    }
    return null;
}

/** What the snapshot needs from its surroundings. */
export interface SnapshotEnv {
    /** How the screen is read back. */
    dom: SnapshotDom;
    /** Which bus is answering. */
    busId: string;
    /** How a node type describes itself — the renderer's outline registry. */
    outlineFor?: OutlineLookup;
    /**
     * Resolves {@code options.root} in one step (the renderer's id index).
     * Without it the tree is walked.
     */
    resolveRoot?: (id: string) => Record<string, unknown> | null | undefined;
}

/**
 * Builds the snapshot of {@code tree} (or of the subtree named by
 * {@code options.root}).
 */
export function buildSnapshot(
    tree: UiNode | Record<string, unknown> | null | undefined,
    options: SnapshotOptions,
    env: SnapshotEnv,
): Snapshot {
    const { dom, busId, outlineFor, resolveRoot } = env;
    const mode: SnapshotMode = options.mode ?? "outline";
    if (!tree) return { busId, mode, node: null, reason: "no-tree" };
    const start = options.root
        ? (resolveRoot?.(options.root) ?? findNode(tree, options.root))
        : (tree as Record<string, unknown>);
    if (!start) return { busId, mode, node: null, reason: "unknown-root" };
    const maxChars = options.maxChars ?? DEFAULT_MAX_CHARS;
    const depth = options.depth ?? DEFAULT_DEPTH;
    const build = (limit: number): Snapshot => {
        const budget = new Budget(limit);
        const ctx = { dom, budget, mode, outlineFor, depthCut: false } as Ctx;
        ctx.api = contextFor(ctx);
        const node = mode === "full"
            ? fullNode(start, depth, ctx)
            : outlineEntry(start, depth, ctx).value;
        const shot: Snapshot = { busId, mode, node };
        if (budget.cut || ctx.depthCut) shot.truncated = true;
        return shot;
    };
    // The budget counts entries, not the punctuation they end up nested in,
    // so a first pass can land a little over. Measure and go again with the
    // overshoot taken off, rather than handing back something larger than
    // was asked for: a caller that says 20 000 characters has a reason.
    let limit = maxChars;
    let result = build(limit);
    for (let attempt = 0; attempt < 3 && sizeOf(result) > maxChars; attempt++) {
        limit -= sizeOf(result) - maxChars;
        if (limit <= 0) break;
        result = build(limit);
    }
    // Still over after three tries: the ceiling is smaller than the smallest
    // answer there is. Say that, rather than let it read as an empty screen.
    if (sizeOf(result) > maxChars) return { busId, mode, node: null, truncated: true, reason: "too-small" };
    return result;
}

/** How long the answer is on the wire. */
function sizeOf(shot: Snapshot): number {
    try { return JSON.stringify(shot)?.length ?? 0; }
    catch { return 0; }
}

// ── What a node says about itself ───────────────────────────────────────────

/**
 * How one node type describes itself in a snapshot. Registered next to the
 * painter for that type ({@code SuiRenderer#registerOutline}), because what a
 * node shows and what it says about itself are the same knowledge — and an
 * extension's node is the only place that knows what its own fields mean.
 *
 * <p>A handler says <b>what</b> to report; the walk stays here. It never
 * recurses: it hands back the children it wants described
 * ({@link OutlineNodeSpec#children}, {@link OutlineNodeSpec#items}) and the
 * snapshot walks them under the caller's {@code depth} and {@code maxChars}.
 */
export type OutlineHandler<N = any> = (node: N, ctx: OutlineContext) => OutlineResult;

/** Finds the handler for a node type, if one is registered. */
export type OutlineLookup = (type: string) => OutlineHandler | undefined;

/** What a handler may ask about the screen, so it needs no DOM of its own. */
export interface OutlineContext {
    /**
     * What the control with this id says now; {@code modelValue} when it is
     * not on the screen (below a collapsed section, before the first paint).
     */
    value(id: string, modelValue?: unknown): unknown;
    /** An attribute of the element with this id — a drawer's {@code data-state}, say. */
    attr(id: string, name: string): string | null;
    /** The element itself, for a live reading nothing else expresses (a row's tick). */
    element(id: string): HTMLElement | null;
    /** False when the control with this id is disabled or busy on screen. */
    usable(id: string, enabledInModel?: boolean): boolean;
    /** Whether a field's value must be withheld — a password, a file, a token. */
    secret(field: { id?: string; label?: string; fieldType?: string }): boolean;
}

/** A node that is content: it has words, and children worth walking. */
export interface OutlineNodeSpec {
    kind?: "node";
    title?: string;
    label?: string;
    text?: string;
    /** A state the browser owns, not the server — a drawer's open/minimized. */
    state?: string;
    columns?: SnapshotColumn[];
    pagination?: SnapshotPagination;
    /** Rows: a list's items, a table's lines. */
    items?: OutlineItemSpec[];
    /** Child nodes to describe. Each lands in the bucket its own handler names. */
    children?: unknown[];
}

/** One row, as its container describes it. */
export interface OutlineItemSpec {
    id: string;
    label?: string;
    description?: string;
    cells?: Record<string, unknown>;
    selected?: boolean;
    clickable?: boolean;
    /** Nodes inside the row — its actions, its content. */
    children?: unknown[];
}

/** A node that is a control to fill in. It describes itself whole. */
export interface OutlineFieldSpec {
    kind: "field";
    field: SnapshotField;
}

/** A node that is something to press. */
export interface OutlineActionSpec {
    kind: "action";
    action: SnapshotAction;
    /** Entries of a menu: pressable in their own right, listed beside it. */
    entries?: unknown[];
}

export type OutlineResult = OutlineNodeSpec | OutlineFieldSpec | OutlineActionSpec;

/** A described node, and which bucket it belongs in. */
type Entry =
    | { kind: "node"; value: SnapshotNode; dropped?: boolean }
    | { kind: "field"; value: SnapshotField; dropped?: boolean }
    | { kind: "action"; value: SnapshotAction; entries?: unknown[]; dropped?: boolean };

/** The context handed to every handler. */
function contextFor(ctx: Ctx): OutlineContext {
    return {
        value: (id, modelValue) => liveValue(id, modelValue, ctx),
        attr: (id, name) => (id ? ctx.dom.byId(id)?.getAttribute?.(name) ?? null : null),
        element: (id) => (id ? ctx.dom.byId(id) : null),
        usable: (id, enabledInModel) => isUsable(id, enabledInModel !== false, ctx),
        secret: (field) => isSecretField(field),
    };
}

// ── The walk ────────────────────────────────────────────────────────────────

/**
 * The outline of one node: what it says about itself, then its children in
 * the bucket each of them names — what can be read ({@code children},
 * {@code items}), filled in ({@code fields}) or pressed ({@code actions}).
 */
function outlineEntry(node: Record<string, unknown>, depth: number, ctx: Ctx): Entry {
    const spec = describe(node, ctx);
    if (spec.kind === "field") {
        // What does not fit is not reported: the ceiling the caller named is
        // a ceiling, and the walk stops here anyway.
        const dropped = !ctx.budget.take(spec.field);
        return { kind: "field", value: spec.field, dropped };
    }
    if (spec.kind === "action") {
        const dropped = !ctx.budget.take(spec.action);
        return { kind: "action", value: spec.action, entries: spec.entries, dropped };
    }
    const out: SnapshotNode = { type: String(node.type) };
    if (typeof node.id === "string" && node.id) out.id = node.id;
    if (spec.title != null) out.title = spec.title;
    if (spec.label != null) out.label = spec.label;
    if (spec.text != null) out.text = spec.text;
    if (spec.state != null) out.state = spec.state;
    // Charged before the children are walked, so each node is counted once.
    if (!ctx.budget.take(out)) { out.truncated = true; return { kind: "node", value: out }; }
    const hasBelow = (spec.children?.length ?? 0) > 0 || (spec.items?.length ?? 0) > 0;
    if (depth <= 0) {
        if (hasBelow) { out.truncated = true; ctx.depthCut = true; }
        return { kind: "node", value: out };
    }
    if (spec.columns && spec.columns.length > 0) {
        if (ctx.budget.take(spec.columns)) out.columns = spec.columns;
        else out.truncated = true;
    }
    for (const item of spec.items ?? []) {
        (out.items ??= []).push(outlineItem(item, depth - 1, ctx));
        if (ctx.budget.cut) { out.truncated = true; return { kind: "node", value: out }; }
    }
    walkChildren(spec.children ?? [], out, depth, ctx);
    if (spec.pagination && !ctx.budget.cut) {
        if (ctx.budget.take(spec.pagination)) out.pagination = spec.pagination;
        else out.truncated = true;
    }
    return { kind: "node", value: out };
}

/** The described node, from its own handler or from the general rules. */
function describe(node: Record<string, unknown>, ctx: Ctx): OutlineResult {
    const handler = ctx.outlineFor?.(String(node.type));
    if (!handler) return genericSpec(node);
    let spec: unknown;
    try {
        spec = handler(node, ctx.api);
    } catch (err) {
        // A handler that throws must not take the whole screen's description
        // with it: the node falls back to what anyone can say about it.
        console.warn(`Snapshot: the outline handler for "${node.type}" failed`, err);
        return genericSpec(node);
    }
    if (!usableSpec(spec)) {
        // The commonest slip is an arrow body with no return. One extension
        // getting that wrong must cost its own node's detail, no more.
        console.warn(`Snapshot: the outline handler for "${node.type}" answered with nothing usable`, spec);
        return genericSpec(node);
    }
    return spec;
}

/** Whether a handler's answer can be believed: a spec, with the payload its kind promises. */
function usableSpec(spec: unknown): spec is OutlineResult {
    if (spec == null || typeof spec !== "object" || Array.isArray(spec)) return false;
    const kind = (spec as { kind?: unknown }).kind;
    if (kind === "field") return isPlainObject((spec as OutlineFieldSpec).field);
    if (kind === "action") return isPlainObject((spec as OutlineActionSpec).action);
    return kind === undefined || kind === "node";
}

function isPlainObject(value: unknown): boolean {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

/** Describes each child and puts it where its kind belongs. */
function walkChildren(children: unknown[], out: Buckets, depth: number, ctx: Ctx): void {
    for (const child of children) {
        if (!isNode(child)) continue;
        place(outlineEntry(child, depth - 1, ctx), out, depth, ctx);
        if (ctx.budget.cut) { out.truncated = true; return; }
    }
}

/** Where the outline puts things. */
interface Buckets {
    items?: SnapshotItem[];
    fields?: SnapshotField[];
    actions?: SnapshotAction[];
    children?: SnapshotNode[];
    truncated?: boolean;
}

/** One described child, in its bucket. */
function place(entry: Entry, out: Buckets, depth: number, ctx: Ctx): void {
    if (entry.dropped) { out.truncated = true; return; }
    if (entry.kind === "field") { (out.fields ??= []).push(entry.value); return; }
    if (entry.kind === "action") {
        (out.actions ??= []).push(entry.value);
        // A menu is a button and a list of entries: the entries are what can
        // be pressed, so they stand beside it rather than under it. A submenu
        // is one level further down and is counted as one — otherwise a menu
        // nested in a menu would be listed whatever depth was asked for, and
        // entries that name each other would never end.
        for (const item of entry.entries ?? []) {
            if (!isNode(item)) continue;
            if (depth <= 0) { out.truncated = true; ctx.depthCut = true; return; }
            place(outlineEntry(item, depth - 1, ctx), out, depth - 1, ctx);
            if (ctx.budget.cut) return;
        }
        return;
    }
    const sub = entry.value;
    // A node with neither an id nor words of its own is pure layout: its
    // children stand in for it, so the outline does not grow a level for it.
    // Unless it carries something that only makes sense beside its rows —
    // the columns they are keyed by, which page of a longer list this is, a
    // state the browser owns: that would be dropped with the level.
    const speaks = sub.id || sub.title || sub.label || sub.text
        || sub.columns || sub.pagination || sub.state || sub.truncated;
    if (!speaks) {
        for (const key of ["fields", "actions", "items", "children"] as const) {
            const values = sub[key];
            if (!values) continue;
            const bucket = (out as Record<string, unknown[]>)[key] ??= [];
            bucket.push(...values);
        }
        return;
    }
    (out.children ??= []).push(sub);
}

/** One row, and the nodes inside it. */
function outlineItem(spec: OutlineItemSpec, depth: number, ctx: Ctx): SnapshotItem {
    const out: SnapshotItem = { id: spec.id };
    if (spec.label != null) out.label = spec.label;
    if (spec.description != null) out.description = spec.description;
    if (spec.cells && Object.keys(spec.cells).length > 0) out.cells = spec.cells;
    if (spec.selected !== undefined) out.selected = spec.selected;
    if (spec.clickable) out.clickable = true;
    if (!ctx.budget.take(out)) { out.truncated = true; return out; }
    const children = spec.children ?? [];
    if (depth <= 0) {
        if (children.some(isNode)) { out.truncated = true; ctx.depthCut = true; }
        return out;
    }
    walkChildren(children, out, depth, ctx);
    return out;
}

// ── What anyone can say about a node ────────────────────────────────────────

/**
 * The description of a node whose type has registered none: its own words,
 * every child node below it, and the rows of anything that carries
 * {@code items}. It is what a plugin's node gets before its extension is
 * installed — a snapshot must not depend on that having happened.
 */
function genericSpec(node: Record<string, unknown>): OutlineNodeSpec {
    const spec: OutlineNodeSpec = {};
    if (typeof node.title === "string") spec.title = node.title;
    if (typeof node.label === "string") spec.label = node.label;
    if (typeof node.text === "string") spec.text = node.text;
    const children: unknown[] = [];
    for (const [key, value] of Object.entries(node)) {
        // A field's trailing action is reported on the field itself.
        if (key === "trailing") continue;
        for (const child of Array.isArray(value) ? value : [value]) {
            if (isNode(child)) children.push(child);
            else if (isItem(child) && (key === "items" || key === "rows")) {
                (spec.items ??= []).push(genericItem(child));
            }
        }
    }
    if (children.length > 0) spec.children = children;
    return spec;
}

/** A row nobody has described: its words, and whatever nodes hang in it. */
function genericItem(item: Record<string, unknown>): OutlineItemSpec {
    const spec: OutlineItemSpec = { id: String(item.id) };
    if (typeof item.label === "string") spec.label = item.label;
    if (typeof item.description === "string") spec.description = item.description;
    if (item.onClick) spec.clickable = true;
    const children: unknown[] = [];
    for (const value of Object.values(item)) {
        for (const child of Array.isArray(value) ? value : [value]) {
            if (isNode(child)) children.push(child);
        }
    }
    if (children.length > 0) spec.children = children;
    return spec;
}

/** A list row: no type discriminator, but an id. */
function isItem(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value)
        && typeof (value as { type?: unknown }).type !== "string"
        && typeof (value as { id?: unknown }).id === "string";
}

/**
 * What the control says now. The DOM is the authority; the model's value is
 * the fallback for a field this renderer has not put on the screen (a node
 * below a collapsed section, a snapshot taken before the first paint).
 */
function liveValue(id: string, modelValue: unknown, ctx: Ctx): unknown {
    const el = id ? ctx.dom.byId(id) : null;
    if (!el) return modelValue ?? null;
    const values = ctx.dom.values(el);
    return id in values ? values[id] : (modelValue ?? null);
}

/** Disabled or busy on screen beats enabled in the model — the screen is what is true. */
function isUsable(id: string, enabledInModel: boolean, ctx: Ctx): boolean {
    const el = id ? ctx.dom.byId(id) : null;
    if (el?.hasAttribute?.("disabled")) return false;
    if (el?.getAttribute?.("aria-disabled") === "true") return false;
    if (el?.classList?.contains?.("is-loading")) return false;
    return enabledInModel;
}

// ── Full ────────────────────────────────────────────────────────────────────

/**
 * The node as rendered, minus the secrets: every model field kept, child
 * nodes walked to {@code depth}, field values read live so a full snapshot
 * describes the same screen the outline does.
 */
function fullNode(node: Record<string, unknown>, depth: number, ctx: Ctx): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    const secret = node.type === "field"
        && isSecretField(node as { id?: string; label?: string; fieldType?: string });
    for (const [key, value] of Object.entries(node)) {
        // A spent budget ends the walk wherever it is standing.
        if (ctx.budget.cut) { out.truncated = true; break; }
        if (secret && key === "value") { out.omitted = true; continue; }
        if (node.type === "field" && key === "value") {
            const live = liveValue(String(node.id ?? ""), value, ctx);
            if (!ctx.budget.take([key, live])) { out.truncated = true; break; }
            out.value = live;
            continue;
        }
        if (isNode(value)) {
            if (depth <= 0) { out.truncated = true; ctx.depthCut = true; continue; }
            // No charge here: the child bills its own content as it is built.
            // Charging the finished subtree as well made every level pay again
            // for everything below it, so a model well inside maxChars came
            // back a fraction of its size.
            out[key] = fullNode(value, depth - 1, ctx);
            continue;
        }
        if (Array.isArray(value) && value.some(v => isNode(v) || isItem(v))) {
            if (depth <= 0) { out.truncated = true; ctx.depthCut = true; continue; }
            const children: unknown[] = [];
            for (const entry of value) {
                if (ctx.budget.cut) { out.truncated = true; break; }
                if (entry != null && typeof entry === "object") {
                    children.push(fullNode(entry as Record<string, unknown>, depth - 1, ctx));
                } else if (ctx.budget.take(entry)) {
                    children.push(entry);
                } else {
                    out.truncated = true;
                    break;
                }
            }
            out[key] = children;
            continue;
        }
        // The key is on the wire too, so it is charged with its value.
        if (!ctx.budget.take([key, value])) { out.truncated = true; break; }
        out[key] = value;
    }
    return out;
}
