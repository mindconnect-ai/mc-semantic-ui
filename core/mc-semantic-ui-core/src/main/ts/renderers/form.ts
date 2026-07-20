import type { UiForm, UiAction } from "../model.js";
import { escapeHtml, type SuiRenderer } from "../renderer.js";
import { cls, evt } from "./util.js";
import { renderField } from "./field.js";
import { renderActions, renderLinks } from "./shared.js";

export function renderForm(node: UiForm, r: SuiRenderer): string {
    const fields = (node.fields || []).map(renderField).join("");
    // Rich body: any layout nodes (stacks/sections/groups) rendered through the
    // dispatcher, after the flat fields. Named inputs anywhere inside still get
    // collected on submit, so the whole form travels as one payload.
    const content = (node.content || []).map(c => r.render(c)).join("");
    // The form needs its own serialised JSON so submit handlers can resolve
    // field metadata without walking the DOM back to the model.
    const nodeJson = escapeHtml(JSON.stringify(node));
    // reloadOnSubmit: opt out of the EventBus's submit interception so the
    // browser does a native full-page navigation. Required for state changes
    // that live outside #sui-root (theme stylesheet, SPA bootstrap script).
    const reload = node.reloadOnSubmit ? ' data-sui-reload="true"' : "";
    // Native submit fallback: mirror form.hbs by writing method+action from
    // the primary action so a reload-on-submit form (or a JS-free browser)
    // can submit the form correctly. Without these the browser would default
    // to GET against the current URL — completely wrong for our POSTs.
    const primary = primaryAction(node);
    const method  = (primary?.onClick?.method ?? "GET").toUpperCase();
    const url     = primary?.onClick?.url ?? "";
    const tunneled = method !== "GET" && method !== "POST";
    const methodAttr = primary
        ? ` method="${tunneled ? "post" : method.toLowerCase()}" action="${escapeHtml(url)}"`
        : "";
    const methodOverride = tunneled
        ? `<input type="hidden" name="_method" value="${escapeHtml(method)}">`
        : "";
    return `<form class="${cls("sui-form", node)}"${evt(node)} id="${escapeHtml(node.id)}" data-sui="form"${reload}${methodAttr} data-node='${nodeJson}'>
        ${methodOverride}
        ${node.title ? `<h2>${escapeHtml(node.title)}</h2>` : ""}
        ${node.formError ? `<div class="sui-form-error" role="alert">${escapeHtml(node.formError)}</div>` : ""}
        ${fields}
        ${content}
        <div class="sui-form-footer">
            ${renderActions(node.actions || [])}
            ${renderLinks(node.links || [])}
        </div>
    </form>`;
}

/** Returns the form's primary submit action: first PRIMARY-styled, else first. */
function primaryAction(node: UiForm): UiAction | undefined {
    const actions = node.actions ?? [];
    return actions.find(a => a.style === "PRIMARY") ?? actions[0];
}
