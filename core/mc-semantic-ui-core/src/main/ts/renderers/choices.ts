/**
 * The rules an expanded SELECT / MULTISELECT follows — which options are
 * checked, in what order they are shown, and where a row goes when it is
 * ticked or unticked. Mirrors {@code UiField#choicesInDisplayOrder} and
 * {@code UiField#isChosen}, so SSR, the SPA and JavaFX agree on the first paint
 * and a server re-render lands on what the user already sees.
 *
 * <p>No DOM in here: the renderer builds markup from it, and the EventBus
 * applies {@link seatAfterToggle} to rows it reads from the page.
 */
import type { UiField } from "../model.js";

type Option = NonNullable<UiField["options"]>[number];

/** One option as an expanded field shows it. */
export interface Choice {
    option: Option;
    /** Position in {@code field.options}. */
    index: number;
    checked: boolean;
}

/**
 * The values of a multi-choice value: an array (null entries dropped), or a
 * comma-separated string with each part trimmed. Null, undefined or a blank
 * string means none. Anything else counts as one value, so a number 0 is "0".
 */
export function selectedValues(value: unknown): string[] {
    if (value == null) return [];
    if (Array.isArray(value)) return value.filter(v => v != null).map(v => String(v));
    const text = String(value);
    return text.trim() === "" ? [] : text.split(",").map(s => s.trim());
}

/** Whether the option with this value is chosen — UiField#isChosen. */
export function isChosen(f: UiField, optionValue: string | null | undefined): boolean {
    if (optionValue == null) return false;
    if (f.fieldType === "MULTISELECT") return selectedValues(f.value).includes(optionValue);
    return f.value != null && optionValue === String(f.value);
}

/** The options in display order, each marked checked or not — UiField#choicesInDisplayOrder. */
export function choicesInDisplayOrder(f: UiField): Choice[] {
    const choices = (f.options ?? []).map((option, index) =>
        ({ option, index, checked: option != null && isChosen(f, option.value) }));
    if (!f.orderable || f.fieldType !== "MULTISELECT") return choices;
    const selected = selectedValues(f.value);
    // Array.prototype.sort is stable, so options sharing a value keep option order.
    const checked = choices.filter(c => c.checked)
        .sort((a, b) => selected.indexOf(a.option.value) - selected.indexOf(b.option.value));
    return [...checked, ...choices.filter(c => !c.checked)];
}

/**
 * Where a row of an orderable group belongs after its box was ticked or
 * unticked, given the rows as shown (the toggled one already carrying its new
 * state). Returns the row's index in the final order.
 *
 * <p>Checked rows lead; the rest follow in option order. A ticked row that is
 * already among the leading checked rows stays put — so the rule is idempotent
 * and a stray change event cannot undo the user's ordering — otherwise it
 * joins the end of them. An unticked row returns to its option place among
 * the unchecked rows, which is also where a server re-render puts it.
 */
export function seatAfterToggle(rows: ReadonlyArray<{ checked: boolean; index: number }>, at: number): number {
    const row = rows[at];
    if (row.checked) {
        return rows.slice(0, at).every(r => r.checked) ? at : rows.filter((r, i) => i !== at && r.checked).length;
    }
    const others = rows.filter((_, i) => i !== at);
    let seat = others.filter(r => r.checked).length;
    for (const r of others.slice(seat)) {
        if (r.index > row.index) break;
        seat++;
    }
    return seat;
}
