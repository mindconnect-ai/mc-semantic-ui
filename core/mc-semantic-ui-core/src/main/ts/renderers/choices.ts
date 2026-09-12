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
 * comma-separated string with each part trimmed — empty parts included, so
 * "a," is ["a", ""]. Null, undefined or a blank string means none. Anything
 * else counts as one value, so a number 0 is "0". UiField#selectedValues.
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
    if (f.fieldType === "MULTISELECT") return selectedValues(f.value).includes(String(optionValue));
    return f.value != null && String(optionValue) === String(f.value);
}

/**
 * The options in display order, each marked checked or not, for dropdowns and
 * expanded groups alike — UiField#choicesInDisplayOrder. A null entry in
 * {@code options} is skipped; the others keep their index.
 */
export function choicesInDisplayOrder(f: UiField): Choice[] {
    const multi = f.fieldType === "MULTISELECT";
    const selected = multi ? selectedValues(f.value) : null;
    const single = !multi && f.value != null ? String(f.value) : null;
    const choices: Choice[] = [];
    (f.options ?? []).forEach((option, index) => {
        if (option == null) return;
        // JSON may carry a number where the model says string; Jackson reads it as text.
        const v = option.value != null ? String(option.value) : null;
        const checked = v != null && (selected ? selected.includes(v) : v === single);
        choices.push({ option, index, checked });
    });
    if (!f.orderable || !selected) return choices;
    const rank = new Map<string, number>();
    selected.forEach((v, i) => { if (!rank.has(v)) rank.set(v, i); });
    // Array.prototype.sort is stable, so options sharing a value keep option order.
    const checked = choices.filter(c => c.checked)
        .sort((a, b) => rank.get(String(a.option.value))! - rank.get(String(b.option.value))!);
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
