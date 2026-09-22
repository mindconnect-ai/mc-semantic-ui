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

/** One row of a list. */
export interface SnapshotItem {
    id: string;
    label?: string;
    description?: string;
    /** True when the row itself is clickable — {@code perform} takes its id. */
    clickable?: boolean;
    fields?: SnapshotField[];
    actions?: SnapshotAction[];
    children?: SnapshotNode[];
    truncated?: boolean;
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
    items?: SnapshotItem[];
    fields?: SnapshotField[];
    actions?: SnapshotAction[];
    children?: SnapshotNode[];
    /** True when this node's content was cut — by {@code depth} or {@code maxChars}. */
    truncated?: boolean;
}

/** What {@code bus.snapshot()} returns. */
export interface Snapshot {
    /** Which bus answered — see {@code SuiEventBus#id}. */
    busId: string;
    mode: SnapshotMode;
    /** The outline (or the full model); {@code null} when there was nothing to show. */
    node: SnapshotNode | Record<string, unknown> | null;
    /** True when anything was left out to stay inside {@code maxChars} or {@code depth}. */
    truncated?: boolean;
    /** Why {@link #node} is null: no page rendered yet, or no such root. */
    reason?: "no-tree" | "unknown-root";
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

/** Node types that are buttons rather than content. */
const ACTION_TYPES = new Set(["action", "action-menu", "menu-item"]);

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
}

/**
 * Finds a node by id in a tree. The walk is generic — every object with a
 * {@code type} is a node, wherever it hangs — so a plugin's own node shape is
 * found like a built-in one.
 */
export function findNode(tree: unknown, id: string): Record<string, unknown> | null {
    if (tree == null || typeof tree !== "object") return null;
    if (Array.isArray(tree)) {
        for (const entry of tree) {
            const hit = findNode(entry, id);
            if (hit) return hit;
        }
        return null;
    }
    const record = tree as Record<string, unknown>;
    if (record.id === id && typeof record.type === "string") return record;
    for (const value of Object.values(record)) {
        if (value != null && typeof value === "object") {
            const hit = findNode(value, id);
            if (hit) return hit;
        }
    }
    return null;
}

/**
 * Builds the snapshot of {@code tree} (or of the subtree named by
 * {@code options.root}).
 */
export function buildSnapshot(
    tree: UiNode | Record<string, unknown> | null | undefined,
    options: SnapshotOptions,
    dom: SnapshotDom,
    busId: string,
): Snapshot {
    const mode: SnapshotMode = options.mode ?? "outline";
    if (!tree) return { busId, mode, node: null, reason: "no-tree" };
    const start = options.root ? findNode(tree, options.root) : (tree as Record<string, unknown>);
    if (!start) return { busId, mode, node: null, reason: "unknown-root" };
    const maxChars = options.maxChars ?? DEFAULT_MAX_CHARS;
    const depth = options.depth ?? DEFAULT_DEPTH;
    const build = (limit: number): Snapshot => {
        const budget = new Budget(limit);
        const ctx: Ctx = { dom, budget, mode };
        const node = mode === "full" ? fullNode(start, depth, ctx) : outlineNode(start, depth, ctx);
        const shot: Snapshot = { busId, mode, node };
        if (budget.cut) shot.truncated = true;
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
    if (sizeOf(result) > maxChars) return { busId, mode, node: null, truncated: true };
    return result;
}

/** How long the answer is on the wire. */
function sizeOf(shot: Snapshot): number {
    try { return JSON.stringify(shot)?.length ?? 0; }
    catch { return 0; }
}

// ── Outline ─────────────────────────────────────────────────────────────────

/**
 * The outline of one node: its own words, then its children sorted into what
 * can be read ({@code children}, {@code items}), what can be filled in
 * ({@code fields}) and what can be pressed ({@code actions}).
 *
 * <p>A node with no id carries no handle for {@code perform} and nothing an
 * agent can point at, so it only appears when it has something to say — a
 * text, a title, or children that do.
 */
function outlineNode(node: Record<string, unknown>, depth: number, ctx: Ctx): SnapshotNode {
    const out: SnapshotNode = { type: String(node.type) };
    if (typeof node.id === "string" && node.id) out.id = node.id;
    copyText(node, out, ctx);
    // Charged before the children are walked, so each node is counted once.
    if (!ctx.budget.take(out)) { out.truncated = true; return out; }
    if (depth <= 0) {
        if (hasChildren(node)) { out.truncated = true; ctx.budget.cut = true; }
        return out;
    }
    fill(node, out, depth, ctx);
    return out;
}

/** The node's own words plus the state the DOM (not the model) decides. */
function copyText(node: Record<string, unknown>, out: SnapshotNode, ctx: Ctx): void {
    if (typeof node.title === "string") out.title = node.title;
    if (typeof node.label === "string") out.label = node.label;
    if (typeof node.text === "string") out.text = node.text;
    // A drawer is opened and minimized in the browser without the server
    // hearing about it, so its model says nothing useful: read the element.
    if (node.type === "drawer" && typeof node.id === "string") {
        const el = ctx.dom.byId(node.id);
        const state = el?.getAttribute?.("data-state");
        if (state) out.state = state;
    }
}

/** Whether a node has anything below it that a deeper walk would show. */
function hasChildren(node: Record<string, unknown>): boolean {
    for (const [key, value] of Object.entries(node)) {
        if (key === "trailing") continue;
        if (isNode(value)) return true;
        if (Array.isArray(value) && value.some(v => isNode(v) || isItem(v))) return true;
    }
    return false;
}

/** A list row: no type discriminator, but an id and a label. */
function isItem(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value)
        && typeof (value as { type?: unknown }).type !== "string"
        && typeof (value as { id?: unknown }).id === "string";
}

/**
 * Sorts a node's children into the outline's four buckets. Shared by nodes and
 * by list rows, which carry the same kinds of children.
 */
function fill(
    node: Record<string, unknown>,
    out: { items?: SnapshotItem[]; fields?: SnapshotField[]; actions?: SnapshotAction[]; children?: SnapshotNode[]; truncated?: boolean },
    depth: number,
    ctx: Ctx,
): void {
    for (const [key, value] of Object.entries(node)) {
        // A field's trailing action is reported on the field itself.
        if (key === "trailing") continue;
        for (const child of Array.isArray(value) ? value : [value]) {
            if (isNode(child)) {
                addNode(child, out, depth, ctx);
            } else if (isItem(child) && (key === "items" || key === "rows")) {
                (out.items ??= []).push(outlineItem(child, depth - 1, ctx));
            } else {
                continue;
            }
            if (ctx.budget.cut) { out.truncated = true; return; }
        }
    }
}

/** Puts one child node into the bucket its type belongs in. */
function addNode(
    child: Record<string, unknown> & { type: string },
    out: { fields?: SnapshotField[]; actions?: SnapshotAction[]; children?: SnapshotNode[]; items?: SnapshotItem[] },
    depth: number,
    ctx: Ctx,
): void {
    if (child.type === "field") {
        const field = outlineField(child, ctx);
        if (!ctx.budget.take(field)) return;
        (out.fields ??= []).push(field);
        return;
    }
    if (ACTION_TYPES.has(child.type)) {
        // A menu button is a button *and* a container of entries: the entries
        // are what can be pressed, so they are what the outline lists.
        const entries = Array.isArray(child.items) ? child.items.filter(isNode) : [];
        const action = outlineAction(child, ctx);
        if (!ctx.budget.take(action)) return;
        (out.actions ??= []).push(action);
        for (const entry of entries) {
            addNode(entry as Record<string, unknown> & { type: string }, out, depth, ctx);
            if (ctx.budget.cut) return;
        }
        return;
    }
    const sub = outlineNode(child, depth - 1, ctx);
    // A node with neither an id nor words of its own is pure layout: its
    // children stand in for it, so the outline does not grow a level for it.
    if (!sub.id && !sub.title && !sub.label && !sub.text && !sub.truncated) {
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

/** One list row, walked like a small node. */
function outlineItem(item: Record<string, unknown>, depth: number, ctx: Ctx): SnapshotItem {
    const out: SnapshotItem = { id: String(item.id) };
    if (typeof item.label === "string") out.label = item.label;
    if (typeof item.description === "string") out.description = item.description;
    if (item.onClick) out.clickable = true;
    if (!ctx.budget.take(out)) { out.truncated = true; return out; }
    if (depth <= 0) {
        if (hasChildren(item)) { out.truncated = true; ctx.budget.cut = true; }
        return out;
    }
    fill(item, out, depth, ctx);
    return out;
}

/** A field with its live value — or with the value withheld. */
function outlineField(field: Record<string, unknown>, ctx: Ctx): SnapshotField {
    const id = String(field.id ?? "");
    const out: SnapshotField = { id };
    if (typeof field.label === "string") out.label = field.label;
    if (typeof field.fieldType === "string") out.type = field.fieldType;
    if (field.required === true) out.required = true;
    if (typeof field.validationError === "string") out.error = field.validationError;
    if (Array.isArray(field.options)) out.options = field.options as SnapshotField["options"];
    if (isSecretField(field as { id?: string; label?: string; fieldType?: string })) {
        out.omitted = true;
    } else {
        out.value = liveValue(id, field.value, ctx);
    }
    if (isNode(field.trailing)) out.action = outlineAction(field.trailing, ctx);
    return out;
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

/** A button, with {@code enabled} taken from the element rather than the model. */
function outlineAction(action: Record<string, unknown>, ctx: Ctx): SnapshotAction {
    const id = String(action.id ?? "");
    const out: SnapshotAction = { id };
    if (typeof action.label === "string") out.label = action.label;
    if (typeof action.style === "string") out.style = action.style;
    if (typeof action.confirm === "string") out.confirm = action.confirm;
    if (typeof action.disabledReason === "string") out.disabledReason = action.disabledReason;
    out.enabled = isEnabled(id, action, ctx);
    return out;
}

/** Disabled on screen beats enabled in the model — the screen is what is true. */
function isEnabled(id: string, action: Record<string, unknown>, ctx: Ctx): boolean {
    const el = id ? ctx.dom.byId(id) : null;
    if (el?.hasAttribute?.("disabled")) return false;
    if (el?.getAttribute?.("aria-disabled") === "true") return false;
    if (el?.classList?.contains?.("is-loading")) return false;
    return action.enabled !== false && action.loading !== true;
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
        if (secret && key === "value") { out.omitted = true; continue; }
        if (node.type === "field" && key === "value") {
            out.value = liveValue(String(node.id ?? ""), value, ctx);
            continue;
        }
        if (isNode(value)) {
            if (depth <= 0) { out.truncated = true; ctx.budget.cut = true; continue; }
            const child = fullNode(value, depth - 1, ctx);
            if (!ctx.budget.take(child)) { out.truncated = true; break; }
            out[key] = child;
            continue;
        }
        if (Array.isArray(value) && value.some(v => isNode(v) || isItem(v))) {
            if (depth <= 0) { out.truncated = true; ctx.budget.cut = true; continue; }
            const children: unknown[] = [];
            for (const entry of value) {
                const child = (entry != null && typeof entry === "object")
                    ? fullNode(entry as Record<string, unknown>, depth - 1, ctx)
                    : entry;
                if (!ctx.budget.take(child)) { out.truncated = true; break; }
                children.push(child);
            }
            out[key] = children;
            continue;
        }
        if (!ctx.budget.take(value)) { out.truncated = true; break; }
        out[key] = value;
    }
    return out;
}
