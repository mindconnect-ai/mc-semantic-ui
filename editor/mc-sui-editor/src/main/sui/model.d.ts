export type FieldType = "TEXT" | "TEXTAREA" | "NUMBER" | "CURRENCY" | "PERCENT" | "DATE" | "DATETIME" | "BOOLEAN" | "SELECT" | "MULTISELECT" | "FILE" | "REFERENCE";
export type ActionStyle = "PRIMARY" | "SECONDARY" | "DANGER";
export type ActionAppearance = "BUTTON" | "LINK" | "ICON";
export type TriggerBehavior = "APPLY_RESPONSE" | "STREAM" | "DOWNLOAD" | "OPEN_IN_TAB" | "INVOKE" | "PATCH" | "UPLOAD";
export interface UiTrigger {
    /**
     * What the client does with the response. Apps may add custom values
     * (registered via {@code SuiEventBus.registerBehavior}); the union here
     * is intentionally open via the {@code string} fallback.
     */
    behavior?: TriggerBehavior | (string & {});
    method?: string;
    url?: string;
    /** ID of a UiForm-like node whose field values are collected as the JSON body. */
    payload?: string;
    /**
     * Name of a client-side handler registered via
     * {@code SuiEventBus.registerClientHandler}. Only meaningful with
     * {@code behavior: "INVOKE"} — the bus calls the named function instead
     * of fetching a URL, and applies whatever {@code UiPage} / {@code UiPatch}
     * it returns. Lets a screen run entirely in the browser, no backend.
     */
    handler?: string;
    /**
     * An inline {@link UiPatch} applied directly when the trigger fires.
     * Only meaningful with {@code behavior: "PATCH"} — no server call, no JS
     * handler: the patch is baked into the trigger at render time and the bus
     * just applies it. Ideal for static, known-ahead UI logic — e.g. a list
     * row whose click fills a detail panel, or a button that opens a fixed
     * dialog — with zero round-trip.
     */
    patch?: UiPatch;
}
export interface UiNodeBase {
    id: string;
    title?: string;
    cssClass?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
export interface UiField {
    type: "field";
    id: string;
    label: string;
    /** Semantic input kind: TEXT / SELECT / DATE / … Renamed from {@code type}
     *  so it stops clashing with the polymorphic UiNode discriminator. */
    fieldType: FieldType;
    value?: unknown;
    editable?: boolean;
    required?: boolean;
    placeholder?: string;
    hint?: string;
    /**
     * Leading icon token shown inside the input, before the value (e.g.
     * `"search"` on a filter box, `"calendar"` on a date field). Decorative.
     * See {@link UiIcon}.
     */
    icon?: string;
    validationError?: string;
    options?: Array<{
        value: string;
        label: string;
    }>;
    /**
     * Only meaningful for {@code TEXTAREA}: when true, pressing Enter
     * inside the textarea submits the surrounding form; Shift+Enter
     * still inserts a newline. Used for chat-style inputs.
     */
    submitOnEnter?: boolean;
    /**
     * When true, any value change (typing in a text input, picking a select
     * option, toggling a checkbox) immediately submits the surrounding form.
     * Used for "instant" controls like a theme picker dropdown where the
     * user's selection IS the action — no separate Save button needed.
     */
    submitOnChange?: boolean;
    /** Only for {@code FILE}: HTML `accept` filter (e.g. `"image/*"`). */
    accept?: string;
    /** Only for {@code FILE}: allow selecting more than one file. */
    multiple?: boolean;
    /** Lower bound for DATE/DATETIME/NUMBER/CURRENCY/PERCENT. */
    min?: string;
    /** Upper bound for the same numeric/date types. */
    max?: string;
    /** Step granularity, e.g. "0.01" for currency. */
    step?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
export interface UiAction {
    type: "action";
    id: string;
    label: string;
    style?: ActionStyle;
    appearance?: ActionAppearance;
    enabled?: boolean;
    disabledReason?: string;
    confirm?: string;
    /**
     * Leading icon token (e.g. `"save"`, `"delete"`). Rendered before the
     * label for BUTTON/LINK; for `appearance: "ICON"` it IS the button and
     * the label becomes the accessible name. See {@link UiIcon}.
     */
    icon?: string;
    /**
     * Force the busy/loading state declaratively — renders with the
     * `is-loading` spinner and disabled. Use this when the server drives the
     * state (push `loading:true` via a patch, then replace with the result).
     * The event bus already toggles the same class automatically for the
     * duration of a click's own request, so you don't set this for that case.
     */
    loading?: boolean;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
export interface UiLink {
    type: "link";
    /** Optional DOM id — matches the Java UiNode.id field for editor selection. */
    id?: string;
    rel?: string;
    href: string;
    label: string;
    cssClass?: string;
    external?: boolean;
    /** Leading icon token rendered before the label. See {@link UiIcon}. */
    icon?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
export interface UiListItem {
    id: string;
    label: string;
    /** Optional rich label: rendered as the item header instead of the plain `label` text. */
    labelNode?: UiNode;
    /** Leading icon token rendered before the label. See {@link UiIcon}. */
    icon?: string;
    description?: string;
    href?: string;
    onClick?: UiTrigger;
    content?: UiNode;
    collapseSummary?: string;
    collapseOpen?: boolean;
    collapseSummaryId?: string;
    /** When true the open/closed state is client-owned; renders collapsed + data-sui-client-collapse. */
    collapseClientControlled?: boolean;
    actions?: UiAction[];
}
/**
 * Column descriptor for {@link UiTable}. Carries a UiNode discriminator so the
 * editor can address columns as first-class tree items. The {@code dataKey}
 * is the lookup into row data; when absent the renderer falls back to the
 * column's {@code id} (which is the common case).
 */
export interface UiTableColumn {
    type: "column";
    id: string;
    label?: string;
    dataKey?: string;
    cssClass?: string;
    /**
     * Renders the header as a clickable sort control. The table's
     * {@link UiTable.sortTrigger} decides whether the server re-sorts
     * ({@code {column}} / {@code {direction}} substituted per header) or the
     * browser reorders the rows it already has.
     */
    sortable?: boolean;
    /**
     * Per-cell render template. Cloned per row, with {@code {dataKey}}
     * substitutions applied recursively to every string field of every
     * descendant node. Substitution context is the row's data map plus the
     * special key {@code id} (= row.id). Unknown keys are left as-is.
     *
     * <p>DOM ids inside the cloned subtree get a per-row suffix so HTML
     * id uniqueness holds: {@code <template-id>__<row-id>}.
     */
    cellTemplate?: UiNode;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
/** Bare text node. Used inside cellTemplate or anywhere a label belongs. */
export interface UiText {
    type: "text";
    id?: string;
    text?: string;
    cssClass?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
/**
 * Standalone icon node — an icon anywhere a {@code UiNode} is accepted (a
 * {@code UiStack} child, a tree/list `labelNode`, a table `cellTemplate`).
 * The convenience `icon` string on {@code UiAction}/{@code UiField}/… covers
 * the common leading-icon case; this node covers free placement.
 *
 * <p>{@code name} is a stable semantic token (`"success"`, `"delete"`) or a
 * raw library id present in the sprite. Resolution is swappable — see
 * `renderers/icon.ts` / {@code IconResolver}. Colour follows
 * {@code currentColor}; size follows the surrounding font (1em). A
 * {@code title} makes it accessible (otherwise it is decorative).
 */
export interface UiIcon {
    type: "icon";
    id?: string;
    /** Icon token: semantic alias (`"delete"`) or raw sprite id (`"trash-2"`). */
    name: string;
    /** Accessible label; when absent the icon is decorative (aria-hidden). */
    title?: string;
    cssClass?: string;
}
/**
 * A busy indicator — a spinning glyph, optionally with a label. Use it as a
 * declarative placeholder while content loads, then replace it via a patch.
 * Distinct from the transient `is-loading` feedback the event bus paints on the
 * clicked control automatically (that one needs no node). Mirrors UiSpinner.java.
 */
export interface UiSpinner {
    type: "spinner";
    id?: string;
    /** Glyph size. Defaults to `"MD"`. */
    size?: "SM" | "MD" | "LG";
    /** Optional visible text next to the glyph (e.g. `"Loading…"`). */
    label?: string;
    /** Accessible label (role="status"). */
    title?: string;
    cssClass?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
/**
 * A progress indicator — a horizontal bar or a circular ring. Set `value`
 * (against `max`, default 100) for determinate progress; leave `value`
 * undefined for an indeterminate (looping) animation. `status` tints the fill.
 * Mirrors UiProgress.java.
 */
export interface UiProgress {
    type: "progress";
    id?: string;
    /** Current progress; undefined renders an indeterminate animation. */
    value?: number;
    /** Upper bound for `value`. Defaults to 100. */
    max?: number;
    /** Bar (default) or circular ring. */
    variant?: "BAR" | "CIRCLE";
    /** Colour intent of the fill. Defaults to `"NORMAL"`. */
    status?: "NORMAL" | "SUCCESS" | "WARNING" | "ERROR";
    /** Whether to show the `NN%` text. Defaults to true. */
    showValue?: boolean;
    title?: string;
    cssClass?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
/**
 * One row of a {@link UiTable}. The cell values live in {@code data} keyed
 * by {@link UiTableColumn#dataKey} (or {@code id} as fallback). Inherits
 * UiNode-style id so {@code selectedRowId} on the table can target it.
 */
export interface UiTableRow {
    type: "row";
    id?: string;
    data?: Record<string, unknown>;
    cssClass?: string;
    /**
     * DOM event triggers, inherited by every node type. `onClick` is also the
     * no-JS contract for an action (the server renders a real anchor/form).
     * The nearest handler wins: a button inside a clickable container keeps
     * its own click.
     */
    onClick?: UiTrigger;
    onDblClick?: UiTrigger;
    /** Pointer enters; moves between the node's own children don't re-fire. */
    onHover?: UiTrigger;
    onLeave?: UiTrigger;
    onChange?: UiTrigger;
    onInput?: UiTrigger;
}
export interface Pagination {
    page: number;
    size: number;
    total: number;
    /**
     * Trigger template fired when the user clicks a page button. The
     * literal {@code {page}} in the trigger's {@code url} is substituted
     * with the target page number at render time. Optional — without it,
     * pagination renders as static informational text.
     */
    pageTrigger?: UiTrigger;
}
export interface UiForm extends UiNodeBase {
    type: "form";
    fields: UiField[];
    actions?: UiAction[];
    links?: UiLink[];
    /**
     * Optional rich body rendered inside the `<form>` after {@link fields}.
     * Any node — a {@link UiStack} for columns, a {@link UiSection} for tabs,
     * nested groups. The payload is collected by walking every named control
     * in the `<form>` element, so the whole form still submits as one object
     * regardless of layout (and across inactive, merely-hidden tabs). Put the
     * inputs as standalone {@link UiField} nodes inside the content.
     */
    content?: UiNode[];
    /**
     * Form-level error banner, shown above the fields. For cross-field or
     * general errors ("Please fix the errors below", "Save failed") that don't
     * belong to a single field — per-field errors go on {@link UiField#validationError}.
     */
    formError?: string;
    /**
     * When true, the EventBus skips its submit-interception so the browser
     * does a native full-page navigation. Used for state changes whose
     * effect lives outside #sui-root (theme stylesheet swap, SSR/SPA mode
     * switch).
     */
    reloadOnSubmit?: boolean;
}
export interface UiDetail extends UiNodeBase {
    type: "detail";
    fields: UiField[];
    actions?: UiAction[];
    links?: UiLink[];
}
export interface UiTable extends UiNodeBase {
    type: "table";
    columns: UiTableColumn[];
    rows: UiTableRow[];
    pagination?: Pagination;
    actions?: UiAction[];
    rowActions?: UiAction[];
    /** Highlights a single row visually; orthogonal to selectMode. */
    selectedRowId?: string;
    /**
     * Row-selection behaviour. {@code NONE} (default) = no selection column.
     * {@code SINGLE} prepends a radio column; {@code MULTI} prepends a
     * checkbox column. Selection inputs all share
     * {@code name="<table.id>__selection"} so the surrounding form submits
     * the chosen row id(s) under that key.
     */
    selectMode?: "NONE" | "SINGLE" | "MULTI";
    /** Pre-selected row ids — pre-checks the radio/checkbox at render time. */
    selectedRowIds?: string[];
    /**
     * When true the table collapses to stacked cards on a narrow screen (header
     * hidden; each row a block of `Column: value` lines via per-cell
     * `data-label`). Wide screens keep the normal table.
     */
    stackOnMobile?: boolean;
    /**
     * Trigger template fired when a sortable header is clicked. `{column}`
     * and `{direction}` in its `url` are substituted per header, so the
     * server re-sorts and returns the page — the same shape as
     * {@link Pagination.pageTrigger}. Without it, sortable columns reorder
     * the rows already in the DOM, client-side.
     */
    sortTrigger?: UiTrigger;
    /** `dataKey` (or column id) the table is currently sorted by. */
    sortColumn?: string;
    /** Direction of {@link sortColumn}; defaults to `ASC`. */
    sortDirection?: "ASC" | "DESC";
    /**
     * CSS length capping the scrollable row area (`"420px"`, `"60vh"`). The
     * rows scroll inside the table while the header row stays pinned.
     */
    maxHeight?: string;
}
/**
 * Application frame: header on top, menu beside the content. The renderer
 * wires the header's burger to the menu's id and suppresses the menu's own
 * toggle, and emits the `sui-shell*` containers whose layout rules live in
 * sui.css — so a shell needs no layout CSS of its own.
 */
export interface UiAppShell extends UiNodeBase {
    type: "app-shell";
    header?: UiHeader;
    menu?: UiMenu;
    /** The page. Rendered into `<shell-id>-content`, so a patch can swap it alone. */
    content?: UiNode;
    /** Optional bar across the bottom, outside the scrolling content area. */
    footer?: UiNode;
    /**
     * Fill the viewport height (default `true`) — sidebar to the bottom of the
     * window, content scrolling inside. Set `false` when the shell is embedded
     * in a larger page and should take only its container's height.
     */
    fillViewport?: boolean;
}
export interface UiList extends UiNodeBase {
    type: "list";
    items: UiListItem[];
    pagination?: Pagination;
    actions?: UiAction[];
}
/**
 * One node of a {@link UiTree}. Recursive: a node with {@code children} (or
 * {@code content}) is expandable and renders as a native {@code <details>};
 * a node with neither is a leaf. Mirrors {@code UiTreeNode.java}.
 *
 * <p>A full {@code UiNode} (type {@code "tree-node"}), so each tree row is
 * individually patch-addressable: {@code REPLACE} its id to re-render one
 * row, {@code REMOVE} its id to drop it from the tree.
 */
export interface UiTreeNode extends UiNodeBase {
    type: "tree-node";
    label?: string;
    /** Optional rich label rendered instead of the plain `label` text. */
    labelNode?: UiNode;
    /** Optional leading icon/emoji shown before the label. */
    icon?: string;
    onClick?: UiTrigger;
    /** Optional rich body rendered inside the node (above its children) when expanded. */
    content?: UiNode;
    children?: UiTreeNode[];
    /** Initial expanded state; user toggles override it thereafter (client-controlled). */
    open?: boolean;
    /** Renders the row with a selected/highlighted style. */
    selected?: boolean;
}
export interface UiTree extends UiNodeBase {
    type: "tree";
    nodes: UiTreeNode[];
}
/**
 * One entry in a {@link UiMenu} — a leaf link or a nesting group. A full
 * `UiNode` (type `"menu-item"`), so a patch can REPLACE a single entry (flip
 * `selected`, swap a label) without re-rendering the menu. Mirrors
 * UiMenuItem.java.
 *
 * A menu item *is an* action: it carries the same clickable behaviour as
 * {@link UiAction} — `onClick`, `confirm`, `icon`, `label`, `enabled` /
 * `disabledReason` — plus the menu-specific fields below.
 */
export interface UiMenuItem extends UiNodeBase {
    type: "menu-item";
    label?: string;
    /** Leading icon token — the only visible affordance in the rail. See {@link UiIcon}. */
    icon?: string;
    /** Navigation target for a leaf; the no-JS fallback when `onClick` is set. */
    href?: string;
    /** Optional click behaviour dispatched via the bus (else plain navigation). */
    onClick?: UiTrigger;
    /** Confirm-dialog text shown before `onClick` fires (as on {@link UiAction}). */
    confirm?: string;
    /** When false, the item is rendered disabled and does not dispatch. */
    enabled?: boolean;
    /** Tooltip explaining why the item is disabled. */
    disabledReason?: string;
    /** Highlights the current item as active. */
    selected?: boolean;
    /** Trailing badge (a count or short status); shrinks to a dot in the rail. */
    badge?: string;
    /** When true (and the item has children), the group renders initially open. */
    open?: boolean;
    /** Destructive action — rendered in the danger colour (mainly in a menu-button). */
    danger?: boolean;
    /** Non-interactive separator line; other fields ignored (mainly in a menu-button). */
    divider?: boolean;
    /** Nested entries; when present this item is a collapsible / fly-out group. */
    children?: UiMenuItem[];
}
/**
 * A button that opens a floating dropdown / context menu of {@link UiMenuItem}s
 * anchored to itself. Transient (opens on click, closes on outside-click /
 * Escape / item pick), placeable anywhere — including as a {@link UiTreeNode}'s
 * `labelNode` to give a row its own context menu. Mirrors UiMenuButton.java.
 */
export interface UiMenuButton extends UiNodeBase {
    type: "menu-button";
    items: UiMenuItem[];
    /** Trigger glyph token. Defaults to `"more"` (a vertical "⋮"). */
    icon?: string;
    /** Optional trigger text; when set the trigger renders as a labelled button. */
    label?: string;
    /** Trigger look. When unset: `"BUTTON"` if `label` is set, else `"ICON"`. */
    variant?: "ICON" | "BUTTON";
    /** Which edge the popover aligns to. Defaults to `"END"` (right-aligned). */
    align?: "START" | "END";
}
/**
 * A vertical navigation menu (the collapsible admin sidebar). Toggles between
 * EXPANDED (icon + label), RAIL (icon-only, groups as hover fly-outs) and
 * HIDDEN (off-canvas) via a hamburger. `state` is the initial state; the SPA
 * bus cycles + persists it client-side, and a server can drive it by patching.
 * Mirrors UiMenu.java.
 */
export interface UiMenu extends UiNodeBase {
    type: "menu";
    items: UiMenuItem[];
    /** Initial display state. Defaults to `"EXPANDED"`. */
    state?: "EXPANDED" | "RAIL" | "HIDDEN";
    /**
     * `"PUSH"` (default) — the menu takes layout space and content reflows as
     * it collapses; `"OVERLAY"` — a drawer floating over the content with a
     * backdrop; `"RESPONSIVE"` — push on a wide screen (hamburger toggles
     * expanded ⇄ rail), an overlay drawer on a narrow one (closed by default).
     * Overlay/responsive need a `position:relative` container; pair with a
     * header hamburger.
     */
    mode?: "PUSH" | "OVERLAY" | "RESPONSIVE";
    /**
     * Which edge the menu sits on — sets the border edge, the direction rail
     * fly-outs/tooltips open, and the way an overlay drawer slides out. Defaults
     * to `"LEFT"`. For a PUSH menu, also order it accordingly in the stack.
     */
    side?: "LEFT" | "RIGHT";
    /** Whether to render the hamburger toggle. Defaults to true. */
    toggle?: boolean;
}
/**
 * Plain composition container — children rendered one after another with no
 * chrome of its own. Parity with {@code UiStack.java}.
 */
export interface UiStack extends UiNodeBase {
    type: "stack";
    children: UiNode[];
    /** Defaults to vertical when unset. */
    direction?: "VERTICAL" | "HORIZONTAL";
    /** CSS gap between children in pixels; falls back to a token default. */
    gap?: number;
}
/**
 * One tab inside a {@link UiSection}. A UiNode in its own right (carries its
 * own {@code id}/{@code title}/{@code cssClass}) so the editor can address
 * it like any other tree item. {@code href} (optional) turns the tab into
 * a real navigation link — clicking it switches the URL rather than
 * swapping a hidden panel into view.
 */
export interface UiSectionEntry extends UiNodeBase {
    type: "section-entry";
    /**
     * Whether clicking the tab also switches to its panel. Default `true`.
     * `false` lets `onClick` decide: the trigger fires, the panel stays put,
     * and the handler selects a tab by patching the section's `initialSection`.
     */
    selectOnClick?: boolean;
    content: UiNode;
    href?: string;
    /** Leading icon token shown before the tab label. See {@link UiIcon}. */
    icon?: string;
    /** Trigger fired on tab click, in addition to activating the panel (e.g. lazy-load). */
    onClick?: UiTrigger;
}
export interface UiSection extends UiNodeBase {
    type: "section";
    sections: UiSectionEntry[];
    initialSection?: string;
    collapseSummary?: string;
    collapseOpen?: boolean;
    /**
     * Tab-bar overflow: `"WRAP"` (default) lets tabs flow onto more rows;
     * `"MENU"` keeps one row and collapses the tabs that don't fit into a
     * trailing "⋯ More" dropdown (needs the SPA; falls back to wrapping
     * without JS).
     */
    tabOverflow?: "WRAP" | "MENU";
}
/**
 * Page-level chrome: brand on the left, optional extras + user widget on
 * the right. Parity with {@code UiHeader.java} / {@code header.hbs}.
 */
export interface UiHeader extends UiNodeBase {
    type: "header";
    brand: string;
    brandHref?: string;
    /** Optional logo image URL rendered to the left of the brand text. */
    brandLogo?: string;
    user?: UiHeaderUser;
    /** Extra widgets rendered between brand and user widget (e.g. theme picker). */
    extras?: UiNode[];
    /**
     * What happens to `extras` that don't fit: `"WRAP"` (default) grows the
     * header onto a second line; `"MENU"` keeps one row and collapses the
     * rest into a trailing "⋯" dropdown (needs `wireHeaderOverflow()`).
     */
    extrasOverflow?: "WRAP" | "MENU";
    /**
     * Id of a {@link UiMenu} this header controls. When set, a leading
     * hamburger is rendered that toggles that menu (same `data-menu-toggle`
     * hook as the menu's own). Moves the burger into the top bar.
     */
    menuToggle?: string;
}
export interface UiHeaderUser {
    name: string;
    initials: string;
    profileHref?: string;
}
/**
 * Drag-and-drop file-upload area. Renders a drop zone with a browse button and
 * a hidden `<input type="file">`. When files are dropped or picked, the bus
 * fires {@link UiUpload#onUpload}: an `UPLOAD` trigger POSTs them as
 * multipart/form-data; an `INVOKE` trigger hands the `File[]` to a client
 * handler (via {@code ctx.files}) for a backend-free preview. Mirrors
 * {@code UiUpload.java}.
 */
export interface UiUpload extends UiNodeBase {
    type: "upload";
    label?: string;
    hint?: string;
    /** Multipart field name; defaults to the node id. */
    name?: string;
    /** HTML `accept` filter (e.g. `"image/*"` or `".pdf,.docx"`). */
    accept?: string;
    multiple?: boolean;
    /** Browse-button label; defaults to "Browse…". */
    buttonLabel?: string;
    /** Drop-zone prompt; defaults to "Drag files here or". */
    dropText?: string;
    onUpload?: UiTrigger;
}
/**
 * A titled group of related fields, rendered as a `<fieldset><legend>`. The
 * body holds any node (usually {@link UiField}s). Transparent to submission —
 * the fields inside still ride along in the single form payload. Mirrors
 * {@code UiFieldGroup.java}.
 */
export interface UiFieldGroup extends UiNodeBase {
    type: "fieldgroup";
    hint?: string;
    content?: UiNode[];
}
export type UiNode = UiForm | UiFieldGroup | UiDetail | UiTable | UiList | UiTree | UiTreeNode | UiMenu | UiMenuItem | UiMenuButton | UiSection | UiStack | UiHeader | UiText | UiIcon | UiSpinner | UiProgress | UiLink | UiAction | UiField | UiDialog | UiUpload;
export interface UiPage {
    navigate?: string;
    node?: UiNode;
    /** Transient toasts to surface alongside the page content. */
    toasts?: UiToast[];
    /**
     * Dialogs open on this page. Each is a {@link UiDialog} node identified by
     * its id; the bus paints them into the body-level `#sui-dialogs` host on
     * every applyPage. Opening one later is an APPEND into that host, closing
     * it a REMOVE by id.
     */
    dialogs?: UiDialog[];
    /**
     * Server-known SSE streams the SPA may want to re-attach to. On every
     * applyPage the bus walks this list and opens a GET reconnect for any
     * channelId it doesn't already have a live reader for — survives
     * navigations, F5, and second tabs joining the same session.
     */
    activeStreams?: UiPageActiveStream[];
}
/**
 * A modal dialog overlay — a first-class UiNode (mirrors UiDialog.java).
 * Rendered as a fixed-position overlay wherever it sits in the tree, so it is
 * opened by APPENDing it into the `#sui-dialogs` host and closed by REMOVE-ing
 * it by id. Pages declare their initially-open dialogs in `UiPage.dialogs`;
 * there is no singular `dialog` field or `closeDialog` flag any more, and
 * several dialogs can be stacked, each addressed by its own id.
 */
export interface UiDialog extends UiNodeBase {
    type: "dialog";
    /** URL the close button navigates to (SSR closes by navigation). */
    closeHref?: string;
    /** The dialog body. Same UiNode types as a regular page node. */
    node?: UiNode;
}
export interface UiPageActiveStream {
    channelId: string;
    /** GET endpoint that opens a fresh SSE connection and replays missed events. */
    resumeUrl: string;
    label?: string;
    returnHref?: string;
}
export type PatchOp = "REPLACE" | "APPEND" | "CLEAR" | "REMOVE";
export interface UiPatchOperation {
    op: PatchOp;
    targetId: string;
    /** Required for REPLACE and APPEND, omitted for CLEAR and REMOVE. */
    node?: UiNode | {
        type: string;
        [k: string]: unknown;
    };
}
export interface UiPatch {
    patches: UiPatchOperation[];
    /** Toasts to display alongside the patch operations. */
    toasts?: UiToast[];
}
export type UiToastLevel = "INFO" | "SUCCESS" | "WARN" | "ERROR";
export interface UiToast {
    level: UiToastLevel;
    title?: string;
    message: string;
    /** Auto-dismiss timeout in ms. 0 = sticky (user-dismiss only). */
    durationMs: number;
}
