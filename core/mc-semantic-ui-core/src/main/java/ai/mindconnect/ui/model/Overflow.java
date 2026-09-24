package ai.mindconnect.ui.model;

/**
 * What a single-row bar does with the entries that do not fit its width —
 * the button bar of a {@link UiList}, {@link UiTable}, {@link UiForm} or
 * {@link UiDetail}. The same choice {@link UiSection.TabOverflow} offers
 * for tabs and {@link UiHeader.ExtrasOverflow} for a header's extras.
 *
 * <ul>
 *   <li>{@link #WRAP} (default) — the bar grows taller and the entries wrap
 *       onto another line. No JavaScript involved, so it also holds in plain
 *       SSR.</li>
 *   <li>{@link #MENU} — the bar stays one row high and the entries that do
 *       not fit collapse, from the end, into a trailing "⋯" dropdown. Needs
 *       the browser bundle; without it the entries simply wrap, so nothing is
 *       ever unreachable.</li>
 * </ul>
 */
public enum Overflow { WRAP, MENU }
