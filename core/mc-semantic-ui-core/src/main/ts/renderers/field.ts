import type { UiField } from "../model.js";
import { escapeHtml, encodeTrigger } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { renderActions } from "./shared.js";
import { cls, evt } from "./util.js";
import { choicesInDisplayOrder } from "./choices.js";
import { renderRichTextToolbar, sanitizeRichText } from "./richtext.js";

/**
 * A time as `HH:mm`, rounded to the nearest multiple of `stepSeconds`
 * (`"09:07"` at 900 → `"09:00"`, `"09:08"` → `"09:15"`). Anything that is not
 * a time, or a step under a minute, comes back unchanged. The browser's own
 * picker offers only the stepped times; this is for what is typed in between.
 */
export function snapTimeValue(value: string, stepSeconds: number): string {
    const m = /^(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
    if (!m || !(stepSeconds >= 60)) return value;
    const step = Math.round(stepSeconds / 60);
    const minutes = Number(m[1]) * 60 + Number(m[2]);
    let snapped = Math.round(minutes / step) * step;
    if (snapped >= 1440) snapped -= step;   // never past the day: the last offered time instead
    const pad = (n: number): string => (n < 10 ? "0" : "") + n;
    return `${pad(Math.floor(snapped / 60))}:${pad(snapped % 60)}`;
}

/**
 * The times a TIME field offers, `HH:mm`, every `step` seconds from `min`
 * (or midnight) to `max` (or the end of the day) — the rows of the datalist
 * the input carries. Empty unless the step is whole minutes of five or more:
 * the browser's own picker lists every minute whatever the step says, and
 * only a datalist makes it show the stepped times instead. Twin of the
 * `timeOptions` SSR helper.
 */
export function timeOptions(f: { step?: string; min?: string; max?: string }): string[] {
    const step = Number(f.step);
    if (!(step >= 300) || step % 60 !== 0) return [];
    const minutesOf = (v: string | undefined, fallback: number): number => {
        const m = v ? /^(\d{2}):(\d{2})/.exec(v) : null;
        return m ? Number(m[1]) * 60 + Number(m[2]) : fallback;
    };
    const from = minutesOf(f.min, 0), to = minutesOf(f.max, 1439);
    const pad = (n: number): string => (n < 10 ? "0" : "") + n;
    const out: string[] = [];
    for (let m = from; m <= to; m += step / 60) out.push(`${pad(Math.floor(m / 60))}:${pad(m % 60)}`);
    return out;
}

export function renderField(f: UiField): string {
    // HIDDEN: no wrapper, no label — only the value, submitted with the form
    // whether or not the field is editable. The input carries the model id
    // itself, since there is no wrapper to hold it. Parity with field.hbs.
    if (f.fieldType === "HIDDEN") {
        const id = escapeHtml(f.id);
        return `<input type="hidden" id="${id}" name="${id}" value="${f.value != null ? escapeHtml(f.value) : ""}" data-sui-type="HIDDEN">`;
    }
    let input = f.editable
        ? renderInput(f)
        : f.fieldType === "RICHTEXT" && f.value != null && String(f.value) !== ""
            // Formatted text is shown as formatted text — reduced first to
            // what the editor itself produces, so a stored value that holds a
            // script or a handler never runs. Parity with RichTextSanitizer.java.
            ? `<div class="sui-richtext-view">${sanitizeRichText(String(f.value))}</div>`
            : `<span class="sui-value">${f.value != null ? escapeHtml(f.value) : "—"}</span>`;
    // Leading in-field icon (decorative): wrap the control so CSS can lay the
    // icon over the input's left padding. Only meaningful for editable
    // single-line controls — never for a group of radios or checkboxes, where
    // the glyph would sit on top of an option.
    if (f.icon && f.editable && !isExpandedChoice(f)) {
        input = `<div class="sui-input-icon">${renderIcon(f.icon)}${input}</div>`;
    }
    // Trailing action (e.g. a Browse… button) shares the control's row.
    if (f.trailing && f.editable) {
        input = `<div class="sui-field-row">${input}${renderActions([f.trailing])}</div>`;
    }
    // The wrapper carries the UiNode id so the editor's id-based selection
    // works symmetrically with every other node type. The inner control
    // takes a derived "<id>__input" so <label for> still hooks up and we
    // don't ship duplicate ids in the DOM. Form submission uses `name`,
    // which keeps the original UiField id — nothing on the server cares
    // about the control's DOM id.
    // cls() carries cssClass and the display state (sui-hidden / sui-blank),
    // so .hidden() and .blank() work on a field like on any other node.
    return `<div class="${cls("sui-field", f)} ${f.validationError ? "sui-field--error" : ""}"${evt(f, "change")} id="${escapeHtml(f.id)}" data-field="${escapeHtml(f.id)}">
        <label ${isExpandedChoice(f) ? `id="${escapeHtml(f.id)}__label"` : `for="${escapeHtml(f.id)}__input"`}>${escapeHtml(f.label)}${f.required ? ' <span class="sui-required">*</span>' : ""}</label>
        ${input}
        ${f.hint ? `<small class="sui-hint">${escapeHtml(f.hint)}</small>` : ""}
        ${f.validationError ? `<span class="sui-error">${escapeHtml(f.validationError)}</span>` : ""}
    </div>`;
}

function renderInput(f: UiField): string {
    // Control-element DOM id is suffixed to leave the canonical id on the
    // wrapper (see renderField). The `name=` attribute stays on the model
    // id so form submissions keep the same payload shape.
    const id = escapeHtml(f.id) + "__input";
    const name = escapeHtml(f.id);
    const valueAttr = f.value != null ? escapeHtml(f.value) : "";
    // submitOnChange: any value-change (typing in a text input, picking a
    // select option, toggling a checkbox) fires the surrounding form. The
    // EventBus reads the marker in its change handler so apps don't have
    // to add per-element listeners. Mirrors the Handlebars field.hbs path.
    const submitOnChange = f.submitOnChange ? ' data-submit-on-change="true"' : "";
    // Field-level onChange trigger: the control carries a data-change-trigger
    // (deliberately NOT data-trigger, which the click handler owns — a form
    // control must still toggle/commit natively on click). The EventBus's
    // change handler dispatches it (see SuiEventBus#handleChange). Lets one
    // field drive UI logic — a checkbox enabling another field, a select
    // filling a panel — with no form submit.
    const changeTrigger = f.onChange ? ` data-change-trigger='${encodeTrigger(f.onChange)}'` : "";
    // Every control gets both markers; they're independent (submitOnChange
    // submits the form, onChange dispatches a trigger — the bus prefers the
    // trigger when present).
    const changeAttrs = submitOnChange + changeTrigger;
    // Numeric/date inputs can carry min / max / step bounds. Plain string
    // attributes — yyyy-MM-dd for DATE, yyyy-MM-ddTHH:mm for DATETIME, a
    // bare number for the rest. No runtime validation: the browser enforces.
    const rangeAttrs = (f.min ? ` min="${escapeHtml(f.min)}"` : "")
        + (f.max  ? ` max="${escapeHtml(f.max)}"`   : "")
        + (f.step ? ` step="${escapeHtml(f.step)}"` : "");

    switch (f.fieldType) {
        case "TEXTAREA": {
            // submitOnEnter: chat-style commit (Enter = submit, Shift+Enter = newline).
            // The bus reads the data-attribute in its keydown handler — see
            // SuiEventBus#installRootListeners. We only emit the marker; the
            // wiring itself lives in the bus so apps don't have to add a
            // per-textarea listener of their own.
            const submitOnEnter = f.submitOnEnter ? ' data-submit-on-enter="true"' : "";
            return `<textarea id="${id}" name="${name}" rows="4"${submitOnEnter}${changeAttrs}>${valueAttr}</textarea>`;
        }
        case "RICHTEXT": {
            // An editable area carrying the HTML as-is, a toolbar above it,
            // and a hidden input that holds the same HTML for the form —
            // wired by wireRichText() (renderers/richtext.ts): the input
            // follows every edit, pastes are reduced to plain formatting, the
            // toolbar drives the editor. Parity with field.hbs.
            // The value is sanitised on the way in, as in the read-only view.
            const placeholder = f.placeholder ? ` data-placeholder="${escapeHtml(f.placeholder)}"` : "";
            const html = sanitizeRichText(f.value != null ? String(f.value) : "");
            return `<div class="sui-richtext" data-sui-richtext>${renderRichTextToolbar()}`
                + `<div class="sui-richtext-editor" id="${id}" contenteditable="true" role="textbox" aria-multiline="true"${placeholder}>${html}</div>`
                + `<input type="hidden" name="${name}" value="${escapeHtml(html)}" data-sui-type="RICHTEXT"${changeAttrs}></div>`;
        }
        case "BOOLEAN":
            return `<input type="checkbox" id="${id}" name="${name}"${changeAttrs} ${f.value ? "checked" : ""}>`;
        case "SELECT": {
            if (f.expanded) return renderChoices(f, id, name, changeAttrs);
            return `<select id="${id}" name="${name}"${changeAttrs}>${renderOptions(f)}</select>`;
        }
        case "MULTISELECT": {
            if (f.expanded) return renderChoices(f, id, name, changeAttrs);
            const size = Math.min((f.options || []).length + 1, 6);
            return `<select id="${id}" name="${name}"${changeAttrs} multiple size="${size}">${renderOptions(f)}</select>`;
        }
        case "NUMBER":
        case "CURRENCY":
        case "PERCENT":
            return `<input type="number" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "DATE":
            return `<input type="date" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "DATETIME":
            return `<input type="datetime-local" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "TIME": {
            // HH:mm; step is in seconds ("900" = quarter hours). A step of five
            // minutes or more comes with a datalist of the times it allows,
            // which is what makes the browser's picker offer only those.
            // Parity with field.hbs.
            const times = timeOptions(f);
            const list = times.length > 0 ? ` list="${id}__list"` : "";
            const datalist = times.length > 0
                ? `<datalist id="${id}__list">${times.map(t => `<option value="${t}"></option>`).join("")}</datalist>` : "";
            return `<input type="time" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${list}${changeAttrs}>${datalist}`;
        }
        case "FILE": {
            const accept = f.accept ? ` accept="${escapeHtml(f.accept)}"` : "";
            const multiple = f.multiple ? " multiple" : "";
            // No value attribute — file inputs are set by the user only.
            return `<input type="file" id="${id}" name="${name}"${accept}${multiple}${changeAttrs}>`;
        }
        case "PASSWORD":
            // Masked input + eye toggle. The toggle is handled by the
            // EventBus ([data-sui-password-toggle]) which flips the input's
            // type and the wrapper's .is-revealed class; CSS swaps the
            // show/hide glyphs. Parity with field.hbs.
            return `<div class="sui-input-reveal">`
                + `<input type="password" id="${id}" name="${name}" value="${valueAttr}" placeholder="${escapeHtml(f.placeholder ?? "")}"${changeAttrs}>`
                + `<button type="button" class="sui-input-reveal-btn" data-sui-password-toggle aria-label="Show password">`
                + `<span class="sui-reveal-show">${renderIcon("show")}</span>`
                + `<span class="sui-reveal-hide">${renderIcon("hide")}</span>`
                + `</button></div>`;
        default:
            return `<input type="text" id="${id}" name="${name}" value="${valueAttr}" placeholder="${escapeHtml(f.placeholder ?? "")}"${changeAttrs}>`;
    }
}

/**
 * An editable SELECT or MULTISELECT shown as radios / checkboxes. Its caption
 * labels a group rather than one control, so it takes an id the group points
 * at instead of a {@code for}. Same test as the SSR {@code expandedChoice} helper.
 */
function isExpandedChoice(f: UiField): boolean {
    return f.editable === true && f.expanded === true
        && (f.fieldType === "SELECT" || f.fieldType === "MULTISELECT");
}

/**
 * An expanded SELECT (radios) or MULTISELECT (checkboxes). Parity with
 * choice-group.hbs, down to the byte: options, order and checked state come
 * from {@link choicesInDisplayOrder}; every input shares {@code name=} and
 * carries its own id, {@code <field>__opt<option index>}, so a re-render
 * matches rows by option rather than position; the change markers sit on each
 * input. An orderable group wraps each option in a row that records its option
 * index and carries the move buttons.
 */
function renderChoices(f: UiField, id: string, name: string, changeAttrs: string): string {
    const multi = f.fieldType === "MULTISELECT";
    const orderable = multi && f.orderable === true;
    const inputs = choicesInDisplayOrder(f).map(c => {
        const choice = `<label class="sui-choice"><input type="${multi ? "checkbox" : "radio"}" id="${name}__opt${c.index}" name="${name}" value="${escapeHtml(c.option.value ?? "")}" data-sui-type="${f.fieldType}"${changeAttrs}${c.checked ? " checked" : ""}><span>${escapeHtml(c.option.label ?? "")}</span></label>`;
        if (!orderable) return choice;
        return `<div class="sui-choice-row" data-sui-index="${c.index}">${choice}<span class="sui-choice-move">`
            + `<button type="button" class="sui-icon-btn sui-icon-btn--secondary" data-sui-move="up" aria-label="Move up" title="Move up">${renderIcon("chevron-up")}</button>`
            + `<button type="button" class="sui-icon-btn sui-icon-btn--secondary" data-sui-move="down" aria-label="Move down" title="Move down">${renderIcon("chevron-down")}</button>`
            + `</span></div>`;
    }).join("");
    return `<div class="sui-choice-group${orderable ? " sui-choice-group--orderable" : ""}" id="${id}" role="${multi ? "group" : "radiogroup"}" aria-labelledby="${name}__label">${inputs}</div>`;
}

/** A dropdown's options, selected by the same rule as an expanded group ({@link choicesInDisplayOrder}). */
function renderOptions(f: UiField): string {
    return choicesInDisplayOrder(f).map(c =>
        `<option value="${escapeHtml(c.option.value ?? "")}" ${c.checked ? "selected" : ""}>${escapeHtml(c.option.label ?? "")}</option>`
    ).join("");
}
