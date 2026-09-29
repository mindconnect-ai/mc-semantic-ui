import { positionPopover } from "./menu-button.js";
import { renderIcon } from "./icon.js";
import { withCsrf } from "../csrf.js";
import { escapeHtml } from "../renderer.js";

/**
 * Suggestions while typing — the browser half of {@code UiField.suggest}.
 *
 * <p>A field that takes suggestions renders its typing control with
 * {@code data-sui-suggest="<url>"} (and {@code -separator}, {@code -min},
 * {@code -debounce}, {@code -mode} when the model sets them). Nothing here is
 * per element: {@link wireSuggest} installs a few listeners on the document
 * once, so a field drawn later, a field in a dialog and a field the morpher
 * replaced all work the same.
 *
 * <p>As the user types, the token under the caret ({@link tokenAt}) is sent
 * as {@code GET <url>?q=<token>} once typing pauses; the latest question wins
 * ({@link SuggestSession}). The answer — a JSON array of
 * {@code {value, label?, detail?}} — becomes a listbox under the field with
 * combobox semantics. Picking one writes it into the field:
 *
 * <ul>
 *   <li><b>text</b> (TEXT, TEXTAREA): over the token ({@link replaceToken}),
 *       with the separator and a space after it when the field has one;</li>
 *   <li><b>one</b> (SELECT): the label into the text input, the value into the
 *       hidden input the form submits;</li>
 *   <li><b>many</b> (MULTISELECT): a chip with its own hidden input.</li>
 * </ul>
 *
 * <p>Then the field's ordinary {@code input} and {@code change} fire, so an
 * {@code onChange} trigger, {@code submitOnChange} and every harvest of the
 * form see the new value. The request is a plain fetch: the field is not
 * marked busy and the loading indicator stays away.
 */

/** One suggestion, as the server answers it. */
export interface Suggestion {
    value: string;
    label: string;
    detail?: string;
}

/** How many suggestions the list shows at most. */
export const MAX_SUGGESTIONS = 8;

/** The token under the caret: where it starts and ends in the value, and its text, trimmed. */
export interface Token {
    start: number;
    end: number;
    text: string;
}

/**
 * The token the caret stands in. Without a separator the whole value is one
 * token; with one, it is the stretch between the separator before the caret
 * and the one after it (or the ends of the value).
 */
export function tokenAt(value: string, caret: number, separator?: string | null): Token {
    if (!separator) return { start: 0, end: value.length, text: value.trim() };
    const at = Math.max(0, Math.min(caret, value.length));
    const before = at === 0 ? -1 : value.lastIndexOf(separator, at - 1);
    const start = before < 0 ? 0 : before + separator.length;
    const next = value.indexOf(separator, at);
    const end = next < 0 ? value.length : next;
    return { start, end, text: value.slice(start, end).trim() };
}

/**
 * The value with the token under the caret replaced by {@code replacement},
 * and where the caret goes: at the end of what was inserted. Without a
 * separator the replacement is the whole value. With one, a token that ends
 * the value is followed by the separator and a space, so the next entry can be
 * typed; a token in the middle keeps the separator that already follows it. A
 * separator that is itself whitespace (a newline: one entry per line) gets no
 * space after it.
 */
export function replaceToken(value: string, caret: number, separator: string | null | undefined,
                             replacement: string): { value: string; caret: number } {
    if (!separator) return { value: replacement, caret: replacement.length };
    const t = tokenAt(value, caret, separator);
    const gap = /^\s+$/.test(separator) ? "" : " ";
    let before = value.slice(0, t.start);
    if (before !== "" && !/\s$/.test(before)) before += gap;
    const after = value.slice(t.end);
    if (after.startsWith(separator)) {
        return { value: before + replacement + after, caret: before.length + replacement.length };
    }
    const inserted = replacement + separator + gap;
    return { value: before + inserted, caret: before.length + inserted.length };
}

/** The request url: {@code q} appended with {@code ?} or {@code &}, before any fragment. */
export function suggestUrl(url: string, q: string): string {
    const hashAt = url.indexOf("#");
    const base = hashAt < 0 ? url : url.slice(0, hashAt);
    const hash = hashAt < 0 ? "" : url.slice(hashAt);
    const join = !base.includes("?") ? "?" : /[?&]$/.test(base) ? "" : "&";
    return `${base}${join}q=${encodeURIComponent(q)}${hash}`;
}

/**
 * What the server answered, as suggestions: entries without a value are
 * dropped, the label defaults to the value, and no more than
 * {@link MAX_SUGGESTIONS} are kept. Anything that is not an array is none.
 */
export function parseSuggestions(json: unknown): Suggestion[] {
    if (!Array.isArray(json)) return [];
    const out: Suggestion[] = [];
    for (const entry of json) {
        if (entry == null || typeof entry !== "object") continue;
        const e = entry as { value?: unknown; label?: unknown; detail?: unknown };
        if (typeof e.value !== "string" && typeof e.value !== "number") continue;
        const value = String(e.value);
        const s: Suggestion = { value, label: typeof e.label === "string" && e.label !== "" ? e.label : value };
        if (typeof e.detail === "string" && e.detail !== "") s.detail = e.detail;
        out.push(s);
        if (out.length === MAX_SUGGESTIONS) break;
    }
    return out;
}

/** What {@link SuggestSession} needs from the page — injectable so the timing can be tested. */
export interface SuggestSessionOptions {
    url: string;
    /** Characters the token needs before a request goes out. */
    minChars: number;
    /** Pause after the last keystroke before it does. */
    debounceMs: number;
    fetch: (input: string, init: RequestInit) => Promise<Response>;
    /** The suggestions for {@code token} — empty when there are none, the token is too short or the request failed. */
    onResults: (items: Suggestion[], token: string) => void;
    setTimer?: (fn: () => void, ms: number) => unknown;
    clearTimer?: (handle: unknown) => void;
}

/**
 * The questions one field asks. {@link update} after every keystroke restarts
 * the pause; when it runs out, the current token is asked for — unless it is
 * shorter than the minimum, which answers "none" without a request. A new
 * question aborts the one still out, and an answer that arrives after a newer
 * question was asked (or after {@link cancel}) is dropped: the latest wins.
 */
export class SuggestSession {
    private timer: unknown = null;
    private seq = 0;
    private controller: AbortController | null = null;

    constructor(readonly options: SuggestSessionOptions) { }

    update(token: string): void {
        this.stopTimer();
        const set = this.options.setTimer ?? ((fn, ms) => setTimeout(fn, ms));
        this.timer = set(() => { this.timer = null; void this.ask(token); }, this.options.debounceMs);
    }

    cancel(): void {
        this.stopTimer();
        this.seq++;
        this.controller?.abort();
        this.controller = null;
    }

    private stopTimer(): void {
        if (this.timer == null) return;
        (this.options.clearTimer ?? ((h) => clearTimeout(h as ReturnType<typeof setTimeout>)))(this.timer);
        this.timer = null;
    }

    private async ask(token: string): Promise<void> {
        const mine = ++this.seq;
        this.controller?.abort();
        this.controller = null;
        if (token.length < this.options.minChars) {
            this.options.onResults([], token);
            return;
        }
        const controller = typeof AbortController === "function" ? new AbortController() : null;
        this.controller = controller;
        let items: Suggestion[] = [];
        try {
            const res = await this.options.fetch(suggestUrl(this.options.url, token), {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" },
                ...(controller ? { signal: controller.signal } : {}),
            });
            if (res.ok) items = parseSuggestions(await res.json());
        } catch {
            // Aborted by a newer question, or the network said no: nothing to offer.
            items = [];
        }
        if (mine !== this.seq) return;
        if (this.controller === controller) this.controller = null;
        this.options.onResults(items, token);
    }
}

/** What a key does to an open list of {@code count} entries with {@code active} highlighted. */
export interface ListKeyResult {
    /** The key was the list's: the browser must not act on it as well. */
    handled: boolean;
    active: number;
    action?: "accept" | "close";
}

/**
 * The listbox's keyboard: arrows move (and wrap), Enter and Tab take the
 * highlighted entry, Escape closes; everything else is the field's — Shift+Tab
 * included, which goes back to the field before, as it always does.
 */
export function listKey(key: string, count: number, active: number, shift = false): ListKeyResult {
    if (count <= 0) return { handled: false, active: -1 };
    if (key === "Tab" && shift) return { handled: false, active };
    switch (key) {
        case "ArrowDown": return { handled: true, active: active < 0 ? 0 : (active + 1) % count };
        case "ArrowUp": return { handled: true, active: active <= 0 ? count - 1 : active - 1 };
        case "Enter":
        case "Tab": return active >= 0 ? { handled: true, active, action: "accept" } : { handled: false, active };
        case "Escape": return { handled: true, active, action: "close" };
        default: return { handled: false, active };
    }
}

/**
 * One picked value of a MULTISELECT with suggestions: its label, a button to
 * take it out again, and the hidden input the form submits it with. The same
 * markup the renderers and suggest-many.hbs write.
 */
export function renderChip(name: string, value: string, label: string): string {
    const v = escapeHtml(value), l = escapeHtml(label);
    return `<span class="sui-chip" data-value="${v}"><span class="sui-chip-label">${l}</span>`
        + `<button type="button" class="sui-chip-remove" data-sui-chip-remove aria-label="Remove ${l}">${renderIcon("x")}</button>`
        + `<input type="hidden" name="${escapeHtml(name)}" value="${v}" data-sui-type="MULTISELECT"></span>`;
}

// ── Wiring ──────────────────────────────────────────────────────────────────

type Control = HTMLInputElement | HTMLTextAreaElement;
type Mode = "text" | "one" | "many";

interface FieldState {
    session: SuggestSession;
    url: string;
    items: Suggestion[];
    active: number;
    list: HTMLElement | null;
}

const states = new WeakMap<Control, FieldState>();
/**
 * The fetch of the bus a field belongs to, by the element it hangs under — the
 * bus's root, or the dialog host its dialogs live in — so a suggestion goes out
 * with the same CSRF, credentials and fetcher as the bus's own requests. Asked
 * for at request time, so a later {@code setFetcher} counts; weakly held, so a
 * bus that is gone takes its root with it.
 */
const fetchers = new WeakMap<object, () => typeof fetch>();
let defaultFetch: typeof fetch | null = null;
let open: { control: Control; state: FieldState } | null = null;
let wired = false;
/** Set while this module sends input/change itself, so its own events do not ask again. */
let emitting = false;

/**
 * Turns on suggestions for every field on the page that asks for them. Called
 * by the event bus with its root (after each render) and its dialog host, and
 * how to get its fetch; the listeners go on the document once, so calling it
 * again only records the fetch and tidies a list whose field has gone.
 */
export function wireSuggest(root: ParentNode = document, fetcher?: () => typeof fetch): void {
    if (fetcher) fetchers.set(root, fetcher);
    if (open && !open.control.isConnected) close();
    if (wired || typeof document === "undefined" || typeof document.addEventListener !== "function") return;
    wired = true;
    document.addEventListener("input", onInput, true);
    document.addEventListener("keydown", onKeydown, true);
    document.addEventListener("focusout", onFocusOut, true);
    document.addEventListener("mousedown", onMouseDown, true);
    document.addEventListener("click", onClick, true);
    document.addEventListener("change", onChange, true);
    if (typeof window !== "undefined" && typeof window.addEventListener === "function") {
        window.addEventListener("resize", () => close());
        window.addEventListener("scroll", () => { if (open?.state.list) place(open.control, open.state.list); }, true);
    }
}

function isSuggestControl(el: unknown): el is Control {
    return !!el && typeof (el as Element).getAttribute === "function"
        && (el as Element).getAttribute("data-sui-suggest") != null;
}

function modeOf(control: Control): Mode {
    const m = control.getAttribute("data-sui-suggest-mode");
    return m === "one" || m === "many" ? m : "text";
}

function stateOf(control: Control): FieldState {
    const url = control.getAttribute("data-sui-suggest") ?? "";
    const known = states.get(control);
    if (known && known.url === url) return known;
    known?.session.cancel();
    const number = (attr: string, fallback: number): number => {
        const n = Number(control.getAttribute(attr));
        return control.getAttribute(attr) != null && Number.isFinite(n) && n >= 0 ? n : fallback;
    };
    const state: FieldState = {
        url, items: [], active: -1, list: null,
        session: new SuggestSession({
            url,
            minChars: number("data-sui-suggest-min", 1),
            debounceMs: number("data-sui-suggest-debounce", 200),
            fetch: (input, init) => fetcherFor(control)(input, init),
            onResults: (items) => showResults(control, state, items),
        }),
    };
    states.set(control, state);
    return state;
}

function fetcherFor(control: Control): typeof fetch {
    // A bus's root and its dialog host are elements, so the walk is by element.
    for (let node: Element | null = control; node; node = node.parentElement) {
        const provider = fetchers.get(node);
        if (provider) return provider();
    }
    return defaultFetch ??= withCsrf((input, init) => fetch(input, init));
}

/** The token a control asks about: under the caret for text, the whole typed text otherwise. */
function currentToken(control: Control): string {
    if (modeOf(control) !== "text") return control.value.trim();
    const caret = typeof control.selectionStart === "number" ? control.selectionStart : control.value.length;
    return tokenAt(control.value, caret, control.getAttribute("data-sui-suggest-separator")).text;
}

function onInput(e: Event): void {
    const control = e.target;
    if (!isSuggestControl(control) || emitting || e.isTrusted === false) return;
    stateOf(control).session.update(currentToken(control));
}

function showResults(control: Control, state: FieldState, items: Suggestion[]): void {
    // Typed away, tabbed away, or drawn again in the meantime: nothing to show.
    if (!control.isConnected || (typeof document !== "undefined" && document.activeElement !== control)) return;
    // A chip field does not offer what it already holds.
    if (modeOf(control) === "many") {
        const chips = control.closest<HTMLElement>("[data-sui-chips]");
        if (chips) items = items.filter(item => !hasChip(chips, item.value));
    }
    if (items.length === 0) { if (open?.control === control) close(); return; }
    if (open && open.control !== control) close();
    state.items = items;
    // Nothing is taken unless chosen where the field holds free text: a typed
    // address that is not among the suggestions must survive Enter and Tab.
    // A SELECT or a chip field holds only picks, so the first is offered.
    const preselect = modeOf(control) === "text" ? -1 : 0;
    const list = state.list ?? document.createElement("ul");
    state.list = list;
    const listId = `${control.id || "sui-suggest"}__suggest`;
    list.id = listId;
    list.className = "sui-suggest-list";
    list.setAttribute("role", "listbox");
    list.innerHTML = "";
    items.forEach((item, i) => {
        const li = document.createElement("li");
        li.id = `${listId}__${i}`;
        li.className = "sui-suggest-option";
        li.setAttribute("role", "option");
        li.setAttribute("data-index", String(i));
        const label = document.createElement("span");
        label.className = "sui-suggest-label";
        label.textContent = item.label;
        li.appendChild(label);
        if (item.detail) {
            const detail = document.createElement("span");
            detail.className = "sui-suggest-detail";
            detail.textContent = item.detail;
            li.appendChild(detail);
        }
        list.appendChild(li);
    });
    if (!list.isConnected) document.body.appendChild(list);
    control.setAttribute("aria-controls", listId);
    control.setAttribute("aria-expanded", "true");
    open = { control, state };
    highlight(control, state, preselect);
    place(control, list);
}

function place(control: Control, list: HTMLElement): void {
    const anchor = (modeOf(control) === "many" ? control.closest<HTMLElement>("[data-sui-chips]") : null) ?? control;
    list.style.minWidth = `${Math.round(anchor.getBoundingClientRect().width)}px`;
    positionPopover(anchor, list, true);
}

function highlight(control: Control, state: FieldState, index: number): void {
    state.active = index;
    const options = state.list ? Array.from(state.list.children) as HTMLElement[] : [];
    options.forEach((o, i) => o.setAttribute("aria-selected", String(i === index)));
    const current = options[index];
    if (current) {
        control.setAttribute("aria-activedescendant", current.id);
        current.scrollIntoView?.({ block: "nearest" });
    } else {
        control.removeAttribute("aria-activedescendant");
    }
}

/** Closes the open list, if any. */
function close(): void {
    if (!open) return;
    const { control, state } = open;
    open = null;
    state.session.cancel();
    state.list?.remove();
    state.list = null;
    state.items = [];
    state.active = -1;
    control.setAttribute("aria-expanded", "false");
    control.removeAttribute("aria-activedescendant");
}

function onKeydown(e: KeyboardEvent): void {
    const control = e.target;
    if (!isSuggestControl(control)) return;
    // A chip field: Backspace in an empty search takes the last chip back out.
    if (e.key === "Backspace" && modeOf(control) === "many" && control.value === ""
        && !(open?.control === control)) {
        const chips = control.closest("[data-sui-chips]")?.querySelectorAll(".sui-chip");
        const last = chips && chips.length > 0 ? chips[chips.length - 1] : null;
        if (last) { e.preventDefault(); last.remove(); emit(control, "change"); }
        return;
    }
    // A SELECT's or chip field's search box is never the form's Enter: what is
    // typed there is not a value, even before the suggestions have arrived.
    const searchOnly = modeOf(control) !== "text" && e.key === "Enter";
    if (open?.control !== control) { if (searchOnly) e.preventDefault(); return; }
    const state = open.state;
    const r = listKey(e.key, state.items.length, state.active, e.shiftKey);
    if (!r.handled) { if (searchOnly) e.preventDefault(); return; }
    // The list's key and nobody else's: not the form's Enter, not the dialog's Escape.
    e.preventDefault();
    e.stopPropagation();
    if (r.action === "accept") acceptSuggestion(control, state.items[r.active]);
    else if (r.action === "close") close();
    else highlight(control, state, r.active);
}

function onFocusOut(e: FocusEvent): void {
    const control = e.target;
    if (!isSuggestControl(control)) return;
    // After the focus has moved: a click on an entry keeps it (mousedown is
    // prevented), anything else takes it away.
    setTimeout(() => { if (document.activeElement !== control) leaveSuggest(control); }, 0);
}

/**
 * What leaving a field does: the list closes, a question still out is
 * dropped, and a SELECT settles — typed text that was not picked is undone,
 * and a box emptied by the user clears the pick (and says so, like any
 * change). Exported for tests.
 */
export function leaveSuggest(control: Control): void {
    if (open?.control === control) close();
    else states.get(control)?.session.cancel();
    if (modeOf(control) !== "one") return;
    const hidden = pickInputOf(control);
    if (control.value.trim() === "" && hidden && hidden.value !== "") {
        hidden.value = "";
        control.value = "";
        control.setAttribute("data-sui-suggest-label", "");
        emit(control, "input");
        emit(control, "change");
        return;
    }
    control.value = control.getAttribute("data-sui-suggest-label") ?? "";
}

function onMouseDown(e: MouseEvent): void {
    if (!open?.state.list) return;
    const option = (e.target as Element | null)?.closest?.<HTMLElement>(".sui-suggest-option");
    if (!option || !open.state.list.contains(option)) return;
    e.preventDefault();
    const item = open.state.items[Number(option.getAttribute("data-index"))];
    if (item) acceptSuggestion(open.control, item);
}

function onClick(e: MouseEvent): void {
    const remove = (e.target as Element | null)?.closest?.<HTMLElement>("[data-sui-chip-remove]");
    if (!remove) return;
    const chip = remove.closest(".sui-chip");
    const control = remove.closest("[data-sui-chips]")?.querySelector<Control>("[data-sui-suggest]") ?? null;
    e.preventDefault();
    chip?.remove();
    if (control) { emit(control, "change"); control.focus?.(); }
}

/**
 * A SELECT's or MULTISELECT's search box says nothing on its own: what the
 * user typed there is not the field's value. Its native change is kept from
 * the page; the one sent after a pick is not.
 */
function onChange(e: Event): void {
    const control = e.target;
    if (!isSuggestControl(control) || emitting || e.isTrusted === false || modeOf(control) === "text") return;
    e.stopImmediatePropagation();
}

/** Sends an input or change from a control, as typing would. */
function emit(control: Element, type: "input" | "change"): void {
    emitting = true;
    try { control.dispatchEvent(new Event(type, { bubbles: true })); }
    finally { emitting = false; }
}

/**
 * Takes a suggestion into the field — over the token for text, as the pick of
 * a SELECT, as a new chip of a MULTISELECT — closes the list, and sends the
 * field's input and change. Exported for tests.
 */
export function acceptSuggestion(control: Control, item: Suggestion | undefined): void {
    if (!item) return;
    if (open?.control === control) close();
    else states.get(control)?.session.cancel();
    const mode = modeOf(control);
    if (mode === "text") {
        const caret = typeof control.selectionStart === "number" ? control.selectionStart : control.value.length;
        const next = replaceToken(control.value, caret, control.getAttribute("data-sui-suggest-separator"), item.value);
        control.value = next.value;
        control.setSelectionRange?.(next.caret, next.caret);
        emit(control, "input");
        emit(control, "change");
        return;
    }
    if (mode === "one") {
        const hidden = pickInputOf(control);
        if (hidden) hidden.value = item.value;
        control.value = item.label;
        control.setAttribute("data-sui-suggest-label", item.label);
        emit(control, "input");
        emit(control, "change");
        return;
    }
    const chips = control.closest<HTMLElement>("[data-sui-chips]");
    if (!chips) return;
    control.value = "";
    if (!hasChip(chips, item.value)) {
        chips.insertBefore(chipElement(chips.getAttribute("data-sui-chips") ?? "", item.value, item.label), control);
        emit(control, "change");
    }
}

/** The hidden input a SELECT with suggestions submits — the one right after its search box. */
function pickInputOf(control: Control): HTMLInputElement | null {
    const next = control.nextElementSibling as HTMLInputElement | null;
    return next && next.tagName === "INPUT" && next.getAttribute("type") === "hidden" ? next : null;
}

function hasChip(chips: HTMLElement, value: string): boolean {
    return Array.from(chips.querySelectorAll(".sui-chip")).some(c => c.getAttribute("data-value") === value);
}

/** The element {@link renderChip} describes, built with the DOM rather than parsed. */
function chipElement(name: string, value: string, label: string): HTMLElement {
    const chip = document.createElement("span");
    chip.className = "sui-chip";
    chip.setAttribute("data-value", value);
    const text = document.createElement("span");
    text.className = "sui-chip-label";
    text.textContent = label;
    const button = document.createElement("button");
    button.setAttribute("type", "button");
    button.className = "sui-chip-remove";
    button.setAttribute("data-sui-chip-remove", "");
    button.setAttribute("aria-label", `Remove ${label}`);
    button.innerHTML = renderIcon("x");
    const input = document.createElement("input");
    input.setAttribute("type", "hidden");
    input.setAttribute("name", name);
    input.setAttribute("value", value);
    input.setAttribute("data-sui-type", "MULTISELECT");
    chip.appendChild(text);
    chip.appendChild(button);
    chip.appendChild(input);
    return chip;
}

/**
 * Sets what a SELECT or MULTISELECT with suggestions holds, from outside —
 * {@code SuiEventBus#perform} filling it in. The value (or each of the values)
 * is kept with its label where the field already shows it, else as its own
 * label. Answers the search box when the field is one of these and something
 * moved, {@code null} when it is one and nothing did, and {@code undefined}
 * when it is not one of these at all.
 */
export function setSuggestPicks(field: Element, value: unknown): Control | null | undefined {
    const control = (isSuggestControl(field) ? field : field.querySelector?.<Control>("[data-sui-suggest-mode]")) ?? null;
    if (!control || modeOf(control) === "text") return undefined;
    const values = (Array.isArray(value) ? value : value == null || value === "" ? [] : [value]).map(String);
    if (modeOf(control) === "one") {
        const hidden = pickInputOf(control);
        const wanted = values[0] ?? "";
        if (!hidden || hidden.value === wanted) return null;
        hidden.value = wanted;
        control.value = wanted;
        control.setAttribute("data-sui-suggest-label", wanted);
        return control;
    }
    const chips = control.closest<HTMLElement>("[data-sui-chips]");
    if (!chips) return null;
    const present = Array.from(chips.querySelectorAll<HTMLElement>(".sui-chip"));
    const before = present.map(c => c.getAttribute("data-value")).join("\u0000");
    const labels = new Map(present.map(c => [c.getAttribute("data-value") ?? "",
        c.querySelector(".sui-chip-label")?.textContent || c.getAttribute("data-value") || ""]));
    for (const c of present) c.remove();
    for (const v of values) {
        if (hasChip(chips, v)) continue;
        chips.insertBefore(chipElement(chips.getAttribute("data-sui-chips") ?? "", v, labels.get(v) || v), control);
    }
    return values.join("\u0000") === before ? null : control;
}

/**
 * The picks the suggestion fields under {@code root} show, with their labels,
 * by field id — what only the page knows once a pick came from the server.
 * The renderer adds them to a field's {@code options} when it redraws the
 * field from its model with the user's input kept (a MERGE), so a pick still
 * reads "Anna Muster" and not the id it stands for.
 */
export function suggestPicksUnder(root: Element): Map<string, Array<{ value: string; label: string }>> {
    const out = new Map<string, Array<{ value: string; label: string }>>();
    if (typeof root?.querySelectorAll !== "function") return out;
    for (const list of Array.from(root.querySelectorAll<HTMLElement>("[data-sui-chips]"))) {
        const name = list.getAttribute("data-sui-chips");
        if (!name) continue;
        out.set(name, Array.from(list.querySelectorAll<HTMLElement>(".sui-chip")).map(chip => {
            const value = chip.getAttribute("data-value") ?? "";
            return { value, label: chip.querySelector(".sui-chip-label")?.textContent || value };
        }));
    }
    for (const control of Array.from(root.querySelectorAll<Control>('[data-sui-suggest-mode="one"]'))) {
        const hidden = pickInputOf(control);
        const name = hidden?.getAttribute("name");
        if (!hidden || !name || hidden.value === "") continue;
        out.set(name, [{ value: hidden.value, label: control.getAttribute("data-sui-suggest-label") || hidden.value }]);
    }
    return out;
}
