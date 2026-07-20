import type { UiField } from "../model.js";
import { escapeHtml, encodeTrigger } from "../renderer.js";
import { renderIcon } from "./icon.js";
import { evt } from "./util.js";

export function renderField(f: UiField): string {
    let input = f.editable
        ? renderInput(f)
        : `<span class="sui-value">${f.value != null ? escapeHtml(f.value) : "—"}</span>`;
    // Leading in-field icon (decorative): wrap the control so CSS can lay the
    // icon over the input's left padding. Only meaningful for editable
    // single-line controls; harmless otherwise.
    if (f.icon && f.editable) {
        input = `<div class="sui-input-icon">${renderIcon(f.icon)}${input}</div>`;
    }
    // The wrapper carries the UiNode id so the editor's id-based selection
    // works symmetrically with every other node type. The inner control
    // takes a derived "<id>__input" so <label for> still hooks up and we
    // don't ship duplicate ids in the DOM. Form submission uses `name`,
    // which keeps the original UiField id — nothing on the server cares
    // about the control's DOM id.
    return `<div class="sui-field ${f.validationError ? "sui-field--error" : ""}"${evt(f, "change")} id="${escapeHtml(f.id)}" data-field="${escapeHtml(f.id)}">
        <label for="${escapeHtml(f.id)}__input">${escapeHtml(f.label)}${f.required ? ' <span class="sui-required">*</span>' : ""}</label>
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
        case "BOOLEAN":
            return `<input type="checkbox" id="${id}" name="${name}"${changeAttrs} ${f.value ? "checked" : ""}>`;
        case "SELECT": {
            const opts = (f.options || []).map(o =>
                `<option value="${escapeHtml(o.value)}" ${f.value === o.value ? "selected" : ""}>${escapeHtml(o.label)}</option>`
            ).join("");
            return `<select id="${id}" name="${name}"${changeAttrs}>${opts}</select>`;
        }
        case "MULTISELECT": {
            const selected = Array.isArray(f.value)
                ? f.value
                : (f.value ? String(f.value).split(",").map(s => s.trim()) : []);
            const opts = (f.options || []).map(o =>
                `<option value="${escapeHtml(o.value)}" ${selected.includes(o.value) ? "selected" : ""}>${escapeHtml(o.label)}</option>`
            ).join("");
            const size = Math.min((f.options || []).length + 1, 6);
            return `<select id="${id}" name="${name}"${changeAttrs} multiple size="${size}">${opts}</select>`;
        }
        case "NUMBER":
        case "CURRENCY":
        case "PERCENT":
            return `<input type="number" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "DATE":
            return `<input type="date" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "DATETIME":
            return `<input type="datetime-local" id="${id}" name="${name}" value="${valueAttr}"${rangeAttrs}${changeAttrs}>`;
        case "FILE": {
            const accept = f.accept ? ` accept="${escapeHtml(f.accept)}"` : "";
            const multiple = f.multiple ? " multiple" : "";
            // No value attribute — file inputs are set by the user only.
            return `<input type="file" id="${id}" name="${name}"${accept}${multiple}${changeAttrs}>`;
        }
        default:
            return `<input type="text" id="${id}" name="${name}" value="${valueAttr}" placeholder="${escapeHtml(f.placeholder ?? "")}"${changeAttrs}>`;
    }
}
