import type { SuiRenderer } from "@mindconnect-ai/mc-semantic-ui-core";

// The helpers below are inlined (not imported from the core bundle) so the
// compiled extension.js has NO runtime import of /sui/renderer.js. That keeps
// the bundle portable: it works from a CDN or under a path prefix, where an
// absolute /sui/ import would resolve against the wrong origin. The `import
// type` above is erased at compile time. Same trick as the chart extension.
const HTML_ESCAPE: Record<string, string> =
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
function esc(value: unknown): string {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, ch => HTML_ESCAPE[ch]!);
}

/** A trigger as the core defines it; only what the board needs to carry. */
interface Trigger {
    url?: string;
    method?: string;
    behavior?: string;
    payload?: string;
    handler?: string;
    [key: string]: unknown;
}

/** The node-level events every UiNode may carry — mirrors renderers/util.ts `evt()`. */
interface NodeEvents {
    onClick?: Trigger; onDblClick?: Trigger; onHover?: Trigger;
    onLeave?: Trigger; onChange?: Trigger; onInput?: Trigger;
}

interface NodeBase extends NodeEvents {
    id: string;
    title?: string;
    cssClass?: string;
    display?: "HIDDEN" | "BLANK" | string;
}

/** Wire shape of `kanban` — mirrors UiKanban.java. */
export interface UiKanbanWire extends NodeBase {
    type: "kanban";
    lanes?: UiKanbanLaneWire[];
    onMove?: Trigger;
    readOnly?: boolean;
}

/** Wire shape of `kanban-lane` — mirrors UiKanbanLane.java. */
export interface UiKanbanLaneWire extends NodeBase {
    type: "kanban-lane";
    cards?: UiKanbanCardWire[];
    limit?: number;
    color?: string;
    locked?: boolean;
}

/** Wire shape of `kanban-card` — mirrors UiKanbanCard.java. */
export interface UiKanbanCardWire extends NodeBase {
    type: "kanban-card";
    description?: string;
    badge?: string;
    tags?: string[];
    color?: string;
    locked?: boolean;
}

/** One card's journey, as the board reports it. */
export interface KanbanMove {
    /** The card's id. */
    card: string;
    /** The lane it left. */
    from: string;
    /** The lane it landed in — the same as `from` when it only changed place. */
    to: string;
    /** Its new position in that lane, zero-based. */
    index: number;
}

/** What the board needs of an event bus: `SuiEventBus` fits, so does a stand-in. */
export interface KanbanBus {
    dispatch(trigger: Trigger, sourceElement?: HTMLElement): Promise<void>;
}

// ── Markup ──────────────────────────────────────────────────────────────────
// The three renderers below and the three Handlebars templates under
// templates/sui/ produce the same bytes; the parity test holds them to it.

/** `cls()` from the core: base class + own classes, `display` as a marker. */
function cls(base: string, node: NodeBase): string {
    const marker = node.display === "HIDDEN" ? "sui-hidden"
        : node.display === "BLANK" ? "sui-blank" : null;
    const own = (node.cssClass ?? "")
        .split(/\s+/)
        .filter(token => token && token !== "sui-hidden" && token !== "sui-blank")
        .join(" ");
    const parts = [base];
    if (own) parts.push(esc(own));
    if (marker) parts.push(marker);
    return parts.join(" ");
}

/** `encodeTrigger()` from the core: JSON in a single-quoted attribute. */
function encodeTrigger(trigger: Trigger): string {
    return JSON.stringify(trigger).replace(/'/g, "&#39;");
}

/** `evt()` from the core: the node's triggers as `data-sui-on-*` attributes. */
function evt(node: NodeEvents): string {
    const all: Array<[string, Trigger | undefined]> = [
        ["click", node.onClick], ["dblclick", node.onDblClick],
        ["hover", node.onHover], ["leave", node.onLeave],
        ["change", node.onChange], ["input", node.onInput],
    ];
    return all.filter(([, t]) => !!t)
        .map(([name, t]) => ` data-sui-on-${name}='${encodeTrigger(t!)}'`).join("");
}

function accent(color: string | undefined): string {
    return color ? ` style="--sui-kanban-accent:${esc(color)}"` : "";
}

export function renderKanban(node: UiKanbanWire, r: SuiRenderer): string {
    const lanes = (node.lanes ?? []).map(l => r.render(l as never)).join("");
    const readOnly = node.readOnly ? ` data-readonly=""` : "";
    const move = node.onMove ? ` data-move-trigger='${encodeTrigger(node.onMove)}'` : "";
    return `<sui-kanban class="${cls("sui-kanban", node)}"${evt(node)} id="${esc(node.id)}" data-sui="kanban"${readOnly}${move}>`
        + `<div class="sui-kanban-lanes">${lanes}</div></sui-kanban>`;
}

export function renderKanbanLane(node: UiKanbanLaneWire, r: SuiRenderer): string {
    const cards = node.cards ?? [];
    const id = esc(node.id);
    const limit = node.limit ? ` data-limit="${node.limit}"` : "";
    const locked = node.locked ? ` data-locked=""` : "";
    const count = node.limit ? `${cards.length}/${node.limit}` : `${cards.length}`;
    return `<section class="${cls("sui-kanban-lane", node)}"${evt(node)} id="${id}" data-sui="kanban-lane" data-id="${id}"${limit}${locked}${accent(node.color)}>`
        + `<header class="sui-kanban-lane-head"><h3 class="sui-kanban-lane-title">${esc(node.title)}</h3>`
        + `<span class="sui-kanban-lane-count">${count}</span></header>`
        + `<ul class="sui-kanban-cards" role="list">${cards.map(c => r.render(c as never)).join("")}</ul></section>`;
}

export function renderKanbanCard(node: UiKanbanCardWire): string {
    const id = esc(node.id);
    const draggable = node.locked ? "" : ` draggable="true"`;
    const badge = node.badge ? `<span class="sui-kanban-card-badge">${esc(node.badge)}</span>` : "";
    const desc = node.description ? `<p class="sui-kanban-card-desc">${esc(node.description)}</p>` : "";
    const tags = node.tags && node.tags.length > 0
        ? `<ul class="sui-kanban-card-tags">${node.tags.map(t => `<li class="sui-kanban-card-tag">${esc(t)}</li>`).join("")}</ul>`
        : "";
    return `<li class="${cls("sui-kanban-card", node)}"${evt(node)} id="${id}" data-sui="kanban-card" data-id="${id}"${draggable}${accent(node.color)}>`
        + `<div class="sui-kanban-card-head"><span class="sui-kanban-card-title">${esc(node.title)}</span>${badge}</div>${desc}${tags}</li>`;
}

// ── The move trigger ────────────────────────────────────────────────────────

/**
 * The trigger to fire for a move: the board's `onMove` with `{card}`, `{from}`,
 * `{to}` and `{index}` filled in wherever its URL carries them — the same
 * render-time substitution a table applies to `{id}`, done at drop time.
 */
export function moveTrigger(template: Trigger, move: KanbanMove): Trigger {
    const url = (template.url ?? "")
        .replace(/\{card\}/g, encodeURIComponent(move.card))
        .replace(/\{from\}/g, encodeURIComponent(move.from))
        .replace(/\{to\}/g, encodeURIComponent(move.to))
        .replace(/\{index\}/g, String(move.index));
    return { ...template, url };
}

// ── Drag and drop ───────────────────────────────────────────────────────────

let bus: KanbanBus | null = null;

/**
 * Registers the three node types on a renderer and, in a browser, defines the
 * `<sui-kanban>` element that makes a board's cards draggable.
 *
 * <p>Pass the app's event bus so a drop fires the board's `onMove` trigger
 * through it (busy state, error handling and the response's patch all come
 * for free). Without one the board still emits a `sui-kanban-move` event on
 * every drop, for an app that wants to handle the move itself.
 *
 * <p>Scoped to the renderer you pass in; the custom element is defined once
 * per page.
 */
export function install(renderer: SuiRenderer, options: { bus?: KanbanBus } = {}): void {
    renderer.register<UiKanbanWire>("kanban", renderKanban);
    renderer.register<UiKanbanLaneWire>("kanban-lane", renderKanbanLane);
    renderer.register<UiKanbanCardWire>("kanban-card", renderKanbanCard);
    if (options.bus) bus = options.bus;
    defineElement();
}

const LANE = ".sui-kanban-lane";
const CARD = ".sui-kanban-card";
const CARDS = ".sui-kanban-cards";

/** The drag in progress: the card by id (a re-render may swap the element) and where it started. */
interface DragState {
    card: string;
    from: string;
    index: number;
    /** Where to put the card back when the drag ends without a drop. */
    parent: HTMLElement;
    next: Element | null;
}

/**
 * Defines `<sui-kanban>` — once per page, and only where there is a DOM to
 * define it in. The class lives inside so the module imports cleanly on a
 * server that renders the same markup with no `HTMLElement` in sight.
 */
function defineElement(): void {
    if (typeof HTMLElement === "undefined" || typeof customElements === "undefined") return;
    if (customElements.get("sui-kanban")) return;

    /**
     * A board that lets its cards be dragged. Listeners sit on the board itself,
     * so cards and lanes that a patch re-renders need no re-wiring; and a board
     * the morpher replaces wholesale is a new element, wired by its own
     * `connectedCallback`.
     *
     * <p>While a card is dragged it moves live: over another card it takes that
     * card's place (above or below it, by the pointer's half), over a lane's
     * empty end it goes last. A drop leaves it there and reports the move; a drag
     * that ends anywhere else puts it back. A locked lane, or one at its limit,
     * does not take the pointer at all.
     */
    class SuiKanbanElement extends HTMLElement {
        private drag: DragState | null = null;
        private wired = false;

        connectedCallback(): void {
            if (this.wired) return;
            this.wired = true;
            this.addEventListener("dragstart", e => this.onDragStart(e));
            this.addEventListener("dragover", e => this.onDragOver(e));
            this.addEventListener("drop", e => this.onDrop(e));
            this.addEventListener("dragend", () => this.onDragEnd());
        }

        private get readOnly(): boolean { return this.hasAttribute("data-readonly"); }

        private cardOf(target: EventTarget | null): HTMLElement | null {
            const el = target instanceof Element ? target.closest<HTMLElement>(CARD) : null;
            return el && this.contains(el) ? el : null;
        }

        private laneOf(target: EventTarget | null): HTMLElement | null {
            const el = target instanceof Element ? target.closest<HTMLElement>(LANE) : null;
            return el && this.contains(el) ? el : null;
        }

        private dragged(): HTMLElement | null {
            return this.drag ? this.querySelector<HTMLElement>(`${CARD}[data-id="${CSS.escape(this.drag.card)}"]`) : null;
        }

        private onDragStart(e: DragEvent): void {
            const card = this.cardOf(e.target);
            if (!card || this.readOnly || card.getAttribute("draggable") !== "true") return;
            const lane = this.laneOf(card);
            if (!lane) return;
            const list = card.parentElement as HTMLElement;
            this.drag = {
                card: card.dataset.id ?? card.id,
                from: lane.dataset.id ?? lane.id,
                index: Array.prototype.indexOf.call(list.children, card),
                parent: list,
                next: card.nextElementSibling,
            };
            card.classList.add("is-dragging");
            if (e.dataTransfer) {
                e.dataTransfer.effectAllowed = "move";
                e.dataTransfer.setData("text/plain", this.drag.card);
            }
        }

        /** Whether a lane takes the dragged card: not locked, and not full unless the card is already in it. */
        private accepts(lane: HTMLElement, card: HTMLElement): boolean {
            if (lane.hasAttribute("data-locked")) return false;
            const limit = Number(lane.dataset.limit ?? 0);
            if (!limit || lane.contains(card)) return true;
            return lane.querySelectorAll(CARD).length < limit;
        }

        private onDragOver(e: DragEvent): void {
            const card = this.dragged();
            const lane = this.laneOf(e.target);
            if (!card || !lane) return;
            this.markOver(this.accepts(lane, card) ? lane : null);
            if (!this.accepts(lane, card)) return;
            e.preventDefault();
            if (e.dataTransfer) e.dataTransfer.dropEffect = "move";
            const list = lane.querySelector<HTMLElement>(CARDS);
            if (!list) return;
            const over = this.cardOf(e.target);
            if (over && over !== card) {
                const box = over.getBoundingClientRect();
                const above = e.clientY < box.top + box.height / 2;
                list.insertBefore(card, above ? over : over.nextElementSibling);
            } else if (!over && !list.contains(card)) {
                list.appendChild(card);
            }
        }

        private markOver(lane: HTMLElement | null): void {
            this.querySelectorAll(`${LANE}.is-drop-target`).forEach(l => {
                if (l !== lane) l.classList.remove("is-drop-target");
            });
            lane?.classList.add("is-drop-target");
        }

        private onDrop(e: DragEvent): void {
            const card = this.dragged();
            const lane = this.laneOf(e.target);
            const drag = this.drag;
            if (!card || !lane || !drag || !this.accepts(lane, card)) return;
            e.preventDefault();
            this.drag = null;
            this.settle(card);
            const list = card.parentElement as HTMLElement;
            const move: KanbanMove = {
                card: drag.card,
                from: drag.from,
                to: lane.dataset.id ?? lane.id,
                index: Array.prototype.indexOf.call(list.children, card),
            };
            if (move.to === move.from && move.index === drag.index) return;
            this.recount();
            this.dispatchEvent(new CustomEvent<KanbanMove>("sui-kanban-move", { detail: move, bubbles: true, composed: true }));
            const raw = this.getAttribute("data-move-trigger");
            if (!raw || !bus) return;
            let template: Trigger;
            try { template = JSON.parse(raw) as Trigger; }
            catch (err) { console.error("sui-kanban: bad data-move-trigger JSON", err, raw); return; }
            // The lane, not the card, carries the busy state: `.is-loading`
            // blocks pointer events, and a card nobody can pick up again while
            // the server answers reads as stuck.
            void bus.dispatch(moveTrigger(template, move), lane);
        }

        /** A drag that ended without a drop — cancelled, or let go outside — goes back where it started. */
        private onDragEnd(): void {
            const card = this.dragged();
            const drag = this.drag;
            this.drag = null;
            if (!card || !drag) return;
            this.settle(card);
            if (drag.parent.isConnected) drag.parent.insertBefore(card, drag.next?.isConnected ? drag.next : null);
        }

        private settle(card: HTMLElement): void {
            card.classList.remove("is-dragging");
            this.markOver(null);
        }

        /** Refreshes every lane's count after a move, so the numbers do not wait for the server. */
        private recount(): void {
            this.querySelectorAll<HTMLElement>(LANE).forEach(lane => {
                const n = lane.querySelectorAll(CARD).length;
                const limit = lane.dataset.limit;
                const count = lane.querySelector(".sui-kanban-lane-count");
                if (count) count.textContent = limit ? `${n}/${limit}` : `${n}`;
            });
        }
    }

    customElements.define("sui-kanban", SuiKanbanElement);
}
