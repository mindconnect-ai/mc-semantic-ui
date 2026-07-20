/*
 * Semantic UI — Widget Showcase (static, backend-free).
 *
 * This whole page is driven by plain JavaScript objects. Each object is a
 * `UiNode` literal — exactly the JSON a server would normally send — and the
 * core `SuiRenderer` turns it into DOM. There is no backend, no fetch, no
 * build step: the renderer and stylesheets are the ones compiled from
 * mc-semantic-ui-core (served here from ./sui/).
 *
 * Every widget is shown with a collapsible "Show code" panel containing two
 * tabs: the JSON the renderer consumes, and the equivalent Java builder code.
 *
 * Answer to "can the renderer load a UiNode straight from JS?" — yes, natively:
 *
 *     import { createDefaultRenderer } from "./sui/renderer.js";
 *     createDefaultRenderer().attach(el).mount({ type: "text", id: "t", text: "hi" });
 */
import { createDefaultRenderer, escapeHtml, renderIcon } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";
// Diagram extension: a separate browser bundle (served under /sui-ext). Its
// install() registers the "diagram" node handler on the renderer — the same
// way this demo registers its own chart/code handlers below.
import { install as installDiagram } from "./sui-ext/diagram/extension.js";
// Chart extension: the shipped painter for the core's chart node. The demo
// used to carry its own inline-SVG handler here; showing the real extension is
// both less code and an honest picture of what a consumer gets.
import { install as installChart } from "./sui-ext/chart/extension.js";

// All icon tokens in the sprite, filled at boot from ./sui/icons.svg so the
// gallery always reflects whatever the sprite actually ships.
let ALL_ICONS = [];
async function loadIconList() {
    try {
        const txt = await (await fetch("./sui/icons.svg")).text();
        const ids = [...txt.matchAll(/<symbol[^>]*\bid="([^"]+)"/g)].map(m => m[1]);
        ALL_ICONS = [...new Set(ids)].sort();
    } catch { ALL_ICONS = []; }
}

// ── Trigger helpers (mirror UiTrigger.* factories) ──────────────────────────
const go   = (url)               => ({ behavior: "APPLY_RESPONSE", method: "GET", url });
const api  = (method, url)       => ({ behavior: "APPLY_RESPONSE", method, url });
const api3 = (method, url, pay)  => ({ behavior: "APPLY_RESPONSE", method, url, payload: pay });
// Backend-free: an inline PATCH trigger that only shows a toast (no fetch), so
// clicks give visible feedback and work unchanged in an exported CodePen.
const toastTrigger = (message, level = "INFO") =>
    ({ behavior: "PATCH", patch: { patches: [], toasts: [{ level, message, durationMs: 2200 }] } });

// ── Small node builders (keep the literals below readable) ──────────────────
const text    = (id, t)           => ({ type: "text", id, text: t });
const heading = (t)               => ({ type: "text", id: `h-${slug(t)}`, text: t, cssClass: "demo-h" });
const stack   = (id, children, o) => ({ type: "stack", id, children, ...o });
const codeNode = (id, code)       => ({ type: "code", id, code });
let _uid = 0;
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || `n${_uid++}`;

// The product-form fields, defined once (clean). The Forms tab renders them
// with errors on load; Cancel patches these clean versions back to clear them.
const DEMO_FORM_ERRORS = {
    "f-name": "Name is required.", "f-desc": "Add at least a short description.",
    "f-price": "Price must be greater than 0.", "f-count": "Enter a whole number.",
    "f-launch": "Launch date can't be in the past.", "f-cat": "Pick a category.",
    "f-tags": "Choose at least one tag.", "f-active": "This must be confirmed.",
};
const DEMO_FORM_FIELDS = [
    { type: "field", id: "f-name",  label: "Name",        fieldType: "TEXT",     required: true, editable: true, placeholder: "e.g. Widget" },
    { type: "field", id: "f-desc",  label: "Description", fieldType: "TEXTAREA", editable: true, hint: "Markdown supported", placeholder: "Describe the product…" },
    { type: "field", id: "f-price", label: "Price",       fieldType: "NUMBER",   editable: true, value: 19.0, step: "0.01" },
    { type: "field", id: "f-count", label: "In stock",    fieldType: "NUMBER",   editable: true, value: 128, min: "0" },
    { type: "field", id: "f-launch",label: "Launch date", fieldType: "DATE",     editable: true, value: "2026-07-06" },
    { type: "field", id: "f-cat",   label: "Category",    fieldType: "SELECT",   editable: true, value: "tools",
      options: [{ value: "tools", label: "Tools" }, { value: "toys", label: "Toys" }, { value: "home", label: "Home" }] },
    { type: "field", id: "f-tags",  label: "Tags",        fieldType: "MULTISELECT", editable: true,
      options: [{ value: "new", label: "New" }, { value: "sale", label: "Sale" }, { value: "eco", label: "Eco" }] },
    { type: "field", id: "f-active",label: "Active",      fieldType: "BOOLEAN",  editable: true, value: true },
];
const withError = (f) => ({ ...f, validationError: DEMO_FORM_ERRORS[f.id] });
// A fully client-side trigger (behavior "PATCH"): the patch is baked into the
// button and applied by the bus with no fetch — so it needs no backend and
// travels intact into an exported CodePen. Re-flags / clears every field.
const formPatch = (mapFn, toast) => ({
    behavior: "PATCH",
    patch: {
        patches: DEMO_FORM_FIELDS.map(f => ({ op: "REPLACE", targetId: f.id, node: mapFn(f) })),
        toasts: [toast],
    },
});

// A collapsible "Show code" panel with JSON | Java tabs. The JSON is generated
// straight from the node being rendered, so it can never drift from the widget.
function codePanel(id, node, java) {
    const json = JSON.stringify(node, null, 2);
    return {
        type: "section", id: `${id}-code`,
        collapseSummary: "Show code — JSON · Java", collapseOpen: false,
        sections: [
            { type: "section-entry", id: `${id}-json`, title: "JSON", content: codeNode(`${id}-json-c`, json) },
            { type: "section-entry", id: `${id}-java`, title: "Java", content: codeNode(`${id}-java-c`, java) },
        ],
    };
}

// A showcased widget: heading + the widget + its code panel + an "Open in
// CodePen" button (opens the exact node in a live sandbox off the CDN bundle).
function specimen(id, headingText, node, java) {
    return stack(`${id}-wrap`, [heading(headingText), node, codePanel(id, node, java), codepenBtn(id, node)], { gap: 8 });
}

// A demo-only node carrying a node's JSON; wireCodePen() opens it in CodePen.
const codepenBtn = (id, node) => ({ type: "codepen", id: `${id}-cp`, json: JSON.stringify(node) });

// ── Page header (chrome) ────────────────────────────────────────────────────
function pageHeader() {
    return { type: "header", id: "demo-header", brand: "Semantic UI", brandLogo: "./mindconnect-logo.svg", user: { name: "Ada Lovelace", initials: "AL" } };
}

function introNote() {
    return {
        type: "text", id: "demo-intro", cssClass: "demo-intro",
        text: "Every widget below is a plain UiNode. Expand “Show code” under any widget to see the JSON the renderer consumes and the equivalent Java builder. (Server-side defaults such as enabled:true and generated DOM ids may differ slightly from the trimmed JSON shown.)",
    };
}

// ── Tab: Tree ───────────────────────────────────────────────────────────────
function treeTab() {
    const explorer = {
        type: "tree", id: "tree-explorer", title: "File explorer",
        nodes: [
            { type: "tree-node", id: "t-src", label: "src", icon: "folder", open: true, children: [
                { type: "tree-node", id: "t-main", label: "main", icon: "folder", open: true, children: [
                    { type: "tree-node", id: "t-app",  label: "app.ts",  icon: "document", onClick: go("/files/app.ts") },
                    { type: "tree-node", id: "t-boot", label: "boot.ts", icon: "document", onClick: go("/files/boot.ts") },
                    { type: "tree-node", id: "t-cmp", label: "components", icon: "folder", children: [
                        { type: "tree-node", id: "t-btn",  label: "Button.ts", icon: "document", onClick: go("/files/Button.ts") },
                        { type: "tree-node", id: "t-tree", label: "Tree.ts",   icon: "document", selected: true, onClick: go("/files/Tree.ts") },
                    ] },
                ] },
                { type: "tree-node", id: "t-test", label: "test", icon: "folder", children: [
                    { type: "tree-node", id: "t-spec", label: "app.spec.ts", icon: "document", onClick: go("/files/app.spec.ts") },
                ] },
            ] },
            { type: "tree-node", id: "t-readme", label: "README.md", icon: "document", onClick: go("/files/README.md") },
            { type: "tree-node", id: "t-pom",    label: "pom.xml",   icon: "document", onClick: go("/files/pom.xml") },
        ],
    };
    const explorerJava =
`UiTree.of("tree-explorer", "File explorer")
    .node(UiTreeNode.of("t-src", "src").icon("folder").open(true)
        .child(UiTreeNode.of("t-main", "main").icon("folder").open(true)
            .child(UiTreeNode.of("t-app",  "app.ts").icon("document").onClick(UiTrigger.go("/files/app.ts")))
            .child(UiTreeNode.of("t-boot", "boot.ts").icon("document").onClick(UiTrigger.go("/files/boot.ts")))
            .child(UiTreeNode.of("t-cmp", "components").icon("folder")
                .child(UiTreeNode.of("t-btn",  "Button.ts").icon("document").onClick(UiTrigger.go("/files/Button.ts")))
                .child(UiTreeNode.of("t-tree", "Tree.ts").icon("document").selected(true).onClick(UiTrigger.go("/files/Tree.ts")))))
        .child(UiTreeNode.of("t-test", "test").icon("folder")
            .child(UiTreeNode.of("t-spec", "app.spec.ts").icon("document").onClick(UiTrigger.go("/files/app.spec.ts")))))
    .node(UiTreeNode.of("t-readme", "README.md").icon("document").onClick(UiTrigger.go("/files/README.md")))
    .node(UiTreeNode.of("t-pom", "pom.xml").icon("document").onClick(UiTrigger.go("/files/pom.xml")));`;

    const rich = {
        type: "tree", id: "tree-rich", title: "Nodes with rich content",
        nodes: [
            { type: "tree-node", id: "r-order", label: "Order #1024", icon: "document", open: true, content: {
                type: "detail", id: "r-order-detail", fields: [
                    { type: "field", id: "r-cust",   label: "Customer", fieldType: "TEXT",   value: "Grace Hopper" },
                    { type: "field", id: "r-total",  label: "Total",    fieldType: "NUMBER", value: 249.0 },
                    { type: "field", id: "r-status", label: "Status",   fieldType: "TEXT",   value: "Shipped" },
                ],
            } },
            { type: "tree-node", id: "r-metrics", label: "Metrics", icon: "chart", content: {
                type: "chart", id: "r-chart", chartType: "BAR",
                data: { labels: ["Mon", "Tue", "Wed", "Thu", "Fri"], series: [{ name: "Visits", values: [12, 19, 9, 22, 17] }] },
            }, children: [
                { type: "tree-node", id: "r-child", label: "Drill down…", icon: "chevron-right", onClick: go("/metrics") },
            ] },
        ],
    };
    const richJava =
`var visits = new UiChart.ChartData();
visits.setLabels(List.of("Mon", "Tue", "Wed", "Thu", "Fri"));
var series = new UiChart.ChartData.Series();
series.setName("Visits");
series.setValues(List.of(12, 19, 9, 22, 17));
visits.setSeries(List.of(series));

UiTree.of("tree-rich", "Nodes with rich content")
    .node(UiTreeNode.of("r-order", "Order #1024").icon("document").open(true)
        .content(UiDetail.of("r-order-detail", null)
            .field(UiField.text("r-cust",   "Customer", "Grace Hopper"))
            .field(UiField.number("r-total", "Total",    249.0))
            .field(UiField.text("r-status", "Status",   "Shipped"))))
    .node(UiTreeNode.of("r-metrics", "Metrics").icon("chart")
        .content(UiChart.of("r-chart", null, UiChart.ChartType.BAR, visits))
        .child(UiTreeNode.of("r-child", "Drill down…").icon("chevron-right").onClick(UiTrigger.go("/metrics"))));`;

    // Per-row context menu: a node's labelNode is a stack of the filename + a
    // UiMenuButton, so every row carries its own "⋮" menu. The menu-button
    // aligns to its END edge and (thanks to a CSS rule) sits at the row's right.
    const fileRow = (id, name, icon) => ({
        type: "tree-node", id, icon,
        labelNode: { type: "stack", id: `${id}-lbl`, direction: "HORIZONTAL", gap: 8, children: [
            { type: "text", id: `${id}-name`, text: name },
            { type: "menu-button", id: `${id}-menu`, align: "END", items: [
                { type: "menu-item", id: `${id}-ren`, label: "Rename",    icon: "edit",     onClick: toastTrigger(`Rename ${name}`) },
                { type: "menu-item", id: `${id}-dup`, label: "Duplicate", icon: "copy",     onClick: toastTrigger(`Duplicated ${name}`) },
                { type: "menu-item", id: `${id}-dl`,  label: "Download",  icon: "download", onClick: toastTrigger(`Downloading ${name}`) },
                { type: "menu-item", id: `${id}-sep`, divider: true },
                { type: "menu-item", id: `${id}-del`, label: "Delete",    icon: "delete", danger: true, onClick: toastTrigger(`Deleted ${name}`, "ERROR") },
            ] },
        ] },
    });
    const ctxTree = {
        type: "tree", id: "tree-ctx", title: "Files — each row has its own ⋮ menu",
        nodes: [
            fileRow("cf-report", "report.pdf", "document"),
            fileRow("cf-budget", "budget.xlsx", "table"),
            { type: "tree-node", id: "cf-assets", icon: "grid", open: true,
              labelNode: { type: "stack", id: "cf-assets-lbl", direction: "HORIZONTAL", gap: 8, children: [
                  { type: "text", id: "cf-assets-name", text: "Assets" },
                  { type: "menu-button", id: "cf-assets-menu", align: "END", items: [
                      { type: "menu-item", id: "cf-assets-new", label: "New file",      icon: "edit",   onClick: toastTrigger("New file in Assets") },
                      { type: "menu-item", id: "cf-assets-sep", divider: true },
                      { type: "menu-item", id: "cf-assets-del", label: "Delete folder", icon: "delete", danger: true, onClick: toastTrigger("Deleted Assets", "ERROR") },
                  ] },
              ] },
              children: [ fileRow("cf-logo", "logo.svg", "document"), fileRow("cf-hero", "hero.jpg", "document") ],
            },
        ],
    };
    const ctxTreeJava =
`// Each row's label is a stack of the name + a UiMenuButton → a per-row context
// menu. (A small CSS rule stretches the label so the "⋮" sits at the far right.)
UiTreeNode fileRow(String id, String name, String icon) {
    return UiTreeNode.of(id, name).icon(icon).labelNode(UiStack.of(
        UiText.of(name),
        UiMenuButton.of(id + "-menu",
            UiMenuItem.of(id + "-ren", "Rename").icon("edit").onClick(UiTrigger.toast("Rename " + name)),
            UiMenuItem.of(id + "-dup", "Duplicate").icon("copy").onClick(UiTrigger.toast("Duplicated " + name)),
            UiMenuItem.of(id + "-dl",  "Download").icon("download").onClick(UiTrigger.toast("Downloading " + name)),
            UiMenuItem.divider(),
            UiMenuItem.of(id + "-del", "Delete").icon("delete").danger(true)
                .onClick(UiTrigger.toast(UiToast.error("Deleted " + name)))
        )).direction(UiStack.Direction.HORIZONTAL).gap(8));
}

UiTree.of("tree-ctx", "Files — each row has its own ⋮ menu")
    .node(fileRow("cf-report", "report.pdf", "document"))
    .node(fileRow("cf-budget", "budget.xlsx", "table"))
    .node(UiTreeNode.of("cf-assets", "Assets").icon("grid").open(true)
        .labelNode(UiStack.of(
            UiText.of("Assets"),
            UiMenuButton.of("cf-assets-menu",
                UiMenuItem.of("cf-assets-new", "New file").icon("edit").onClick(UiTrigger.toast("New file in Assets")),
                UiMenuItem.divider(),
                UiMenuItem.of("cf-assets-del", "Delete folder").icon("delete").danger(true)
                    .onClick(UiTrigger.toast(UiToast.error("Deleted Assets")))
            )).direction(UiStack.Direction.HORIZONTAL).gap(8))
        .child(fileRow("cf-logo", "logo.svg", "document"))
        .child(fileRow("cf-hero", "hero.jpg", "document")));`;

    return stack("tab-tree", [
        text("tree-intro", "Nodes with children (or content) render as a native <details> disclosure with client-controlled state: expand/collapse survives re-renders. Click a twisty to toggle; click a label to fire its action."),
        specimen("sp-tree", "Generic Tree — expandable / collapsible nodes", explorer, explorerJava),
        specimen("sp-tree-rich", "Trees can carry any component as node content", rich, richJava),
        specimen("sp-tree-ctx", "Context menu per row — a UiMenuButton in each node's labelNode", ctxTree, ctxTreeJava),
    ], { gap: 16 });
}

// ── Tab: Lists & tables ─────────────────────────────────────────────────────
function dataTab() {
    const list = {
        type: "list", id: "demo-list", title: "Activity",
        actions: [{ type: "action", id: "list-refresh", label: "Refresh", style: "SECONDARY", onClick: api("POST", "/activity/refresh") }],
        items: [
            { id: "l1", label: "Deployment finished",   description: "web-frontend · 2m ago",        onClick: go("/activity/l1") },
            { id: "l2", label: "New comment on PR #42",  description: "workflow-controller · 14m ago", onClick: go("/activity/l2") },
            { id: "l3", label: "Nightly build", collapseSummary: "3 warnings (click to expand)", collapseClientControlled: true,
              collapseSummaryId: "l3-sum", content: text("l3-body", "Build passed with 3 non-blocking lint warnings.") },
        ],
        pagination: { page: 1, size: 3, total: 42 },
    };
    const listJava =
`UiList.of("demo-list", "Activity")
    .action(UiAction.secondary("list-refresh", "Refresh").onClick(UiTrigger.api("POST", "/activity/refresh")))
    .item(UiList.Item.of("l1", "Deployment finished").description("web-frontend · 2m ago").onClick(UiTrigger.go("/activity/l1")))
    .item(UiList.Item.of("l2", "New comment on PR #42").description("workflow-controller · 14m ago").onClick(UiTrigger.go("/activity/l2")))
    .item(UiList.Item.of("l3", "Nightly build")
        .collapsibleClient("3 warnings (click to expand)", "l3-sum")
        .content(UiText.of("l3-body", "Build passed with 3 non-blocking lint warnings.")))
    .paginate(1, 3, 42);`;

    const table = {
        type: "table", id: "demo-table", title: "Products",
        selectMode: "MULTI", selectedRowIds: ["p2"], stackOnMobile: true,
        rowActions: [{ type: "action", id: "row-edit", label: "Edit", style: "SECONDARY", onClick: api("GET", "/products/edit") }],
        columns: [
            { type: "column", id: "col-name",  label: "Name",  dataKey: "name" },
            { type: "column", id: "col-price", label: "Price", dataKey: "price" },
            { type: "column", id: "col-stock", label: "Stock", dataKey: "stock" },
        ],
        rows: [
            { type: "row", id: "p1", data: { name: "Widget", price: "€ 19.00", stock: "128" } },
            { type: "row", id: "p2", data: { name: "Gadget", price: "€ 49.00", stock: "12" } },
            { type: "row", id: "p3", data: { name: "Gizmo",  price: "€ 99.00", stock: "0" } },
        ],
        pagination: { page: 1, size: 3, total: 57 },
    };
    const tableJava =
`UiTable.of("demo-table", "Products")
    .column(UiColumn.of("name", "Name"))
    .column(UiColumn.of("price", "Price"))
    .column(UiColumn.of("stock", "Stock"))
    .row(Map.of("id", "p1", "name", "Widget", "price", "€ 19.00", "stock", "128"))
    .row(Map.of("id", "p2", "name", "Gadget", "price", "€ 49.00", "stock", "12"))
    .row(Map.of("id", "p3", "name", "Gizmo",  "price", "€ 99.00", "stock", "0"))
    .selectMode(UiTable.SelectMode.MULTI)
    .selectedRowIds(List.of("p2"))
    .stackOnMobile(true)   // narrow screens: each row becomes a Column: value card
    .rowAction(UiAction.secondary("row-edit", "Edit").onClick(UiTrigger.api("GET", "/products/edit")))
    .paginate(1, 3, 57);`;

    return stack("tab-data", [
        specimen("sp-list",  "List — items, collapsible rows, actions, pagination", list, listJava),
        specimen("sp-table", "Table — columns, row selection, row actions, pagination", table, tableJava),
    ], { gap: 16 });
}

// ── Tab: Forms ──────────────────────────────────────────────────────────────
function formsTab() {
    const form = {
        type: "form", id: "demo-form", title: "New product",
        fields: DEMO_FORM_FIELDS.map(withError),
        actions: [
            { type: "action", id: "f-save",   label: "Save",   style: "PRIMARY",   onClick: formPatch(withError, { level: "ERROR", message: "Please fix the errors below", durationMs: 3000 }) },
            { type: "action", id: "f-cancel", label: "Cancel", style: "SECONDARY", onClick: formPatch(f => f, { level: "INFO", message: "Changes discarded", durationMs: 2500 }) },
            { type: "action", id: "f-delete", label: "Delete", style: "DANGER", confirm: "Delete this product?", onClick: api("DELETE", "/products/1") },
        ],
        links: [{ type: "link", id: "f-help", rel: "ref", href: "#", label: "Need help?" }],
    };
    const formJava =
`UiForm.of("demo-form", "New product")
    .field(UiField.text("f-name", "Name", null).asRequired().asEditable().placeholder("e.g. Widget"))
    .field(UiField.textarea("f-desc", "Description", null).asEditable().hint("Markdown supported").placeholder("Describe the product…"))
    .field(UiField.number("f-price", "Price", 19.0).asEditable().step("0.01"))
    .field(UiField.number("f-count", "In stock", 128).asEditable().min("0"))
    .field(UiField.date("f-launch", "Launch date", "2026-07-06").asEditable())
    .field(UiField.select("f-cat", "Category", "tools", List.of(
        UiField.Option.of("tools", "Tools"), UiField.Option.of("toys", "Toys"), UiField.Option.of("home", "Home"))).asEditable())
    .field(UiField.multiselect("f-tags", "Tags", null, List.of(
        UiField.Option.of("new", "New"), UiField.Option.of("sale", "Sale"), UiField.Option.of("eco", "Eco"))).asEditable())
    .field(UiField.bool("f-active", "Active", true).asEditable())
    // Fully client-side — the patch is baked into the trigger (behavior PATCH),
    // applied by the bus with no server call at all.
    .action(UiAction.primary("f-save", "Save").onClick(UiTrigger.patch(allFieldsError)))
    .action(UiAction.secondary("f-cancel", "Cancel").onClick(UiTrigger.patch(allFieldsClean)))
    .action(UiAction.danger("f-delete", "Delete").confirm("Delete this product?").onClick(UiTrigger.api("DELETE", "/products/1")))
    .link(UiLink.of("ref", "#", "Need help?"));`;

    const detail = {
        type: "detail", id: "demo-detail", title: "Product detail (read-only)",
        fields: [
            { type: "field", id: "d-name",   label: "Name",   fieldType: "TEXT",    value: "Gadget" },
            { type: "field", id: "d-price",  label: "Price",  fieldType: "NUMBER",  value: 49.0 },
            { type: "field", id: "d-active", label: "Active", fieldType: "BOOLEAN", value: true },
        ],
        links: [{ type: "link", id: "d-more", rel: "ref", href: "#", label: "View history" }],
    };
    const detailJava =
`UiDetail.of("demo-detail", "Product detail (read-only)")
    .field(UiField.text("d-name", "Name", "Gadget"))
    .field(UiField.number("d-price", "Price", 49.0))
    .field(UiField.bool("d-active", "Active", true))
    .link(UiLink.of("ref", "#", "View history"));`;

    return stack("tab-forms", [
        text("forms-intro", "The form loads with every field flagged. Save re-flags them all, Cancel clears them — no backend involved: each button carries an inline PATCH trigger that the bus applies client-side. That's why it works unchanged in an exported CodePen."),
        specimen("sp-form",   "Form — field types, action styles, links", form, formJava),
        specimen("sp-detail", "Detail — a read-only definition list", detail, detailJava),
    ], { gap: 16 });
}

// ── Tab: Layout, text, actions, charts ──────────────────────────────────────
function layoutTab() {
    const txt = text("txt-1", "A plain text node — the simplest leaf widget.");
    const txtJava = `UiText.of("txt-1", "A plain text node — the simplest leaf widget.");`;

    const actions = {
        type: "stack", id: "row-actions", direction: "HORIZONTAL", gap: 8, children: [
            { type: "action", id: "btn-primary",   label: "Primary",   style: "PRIMARY",   onClick: api("POST", "/do/primary") },
            { type: "action", id: "btn-secondary", label: "Secondary", style: "SECONDARY", onClick: api("POST", "/do/secondary") },
            { type: "action", id: "btn-danger",    label: "Danger",    style: "DANGER", confirm: "Are you sure?", onClick: api("DELETE", "/do/danger") },
        ],
    };
    const actionsJava =
`UiStack.of(
    UiAction.primary("btn-primary", "Primary").onClick(UiTrigger.api("POST", "/do/primary")),
    UiAction.secondary("btn-secondary", "Secondary").onClick(UiTrigger.api("POST", "/do/secondary")),
    UiAction.danger("btn-danger", "Danger").confirm("Are you sure?").onClick(UiTrigger.api("DELETE", "/do/danger"))
).direction(UiStack.Direction.HORIZONTAL).gap(8);`;

    const links = {
        type: "stack", id: "row-links", direction: "HORIZONTAL", gap: 16, children: [
            { type: "link", id: "lnk-docs", rel: "ref", href: "https://example.com/docs", label: "Documentation" },
            { type: "link", id: "lnk-gh",   rel: "ref", href: "https://example.com/gh",   label: "GitHub" },
        ],
    };
    const linksJava =
`UiStack.of(
    UiLink.of("ref", "https://example.com/docs", "Documentation"),
    UiLink.of("ref", "https://example.com/gh", "GitHub")
).direction(UiStack.Direction.HORIZONTAL).gap(16);`;

    const collapsible = {
        type: "section", id: "demo-collapsible", collapseSummary: "Collapsible section (click to toggle)", collapseOpen: false,
        sections: [{ type: "section-entry", id: "cs-body", content: text("cs-text", "This whole section is wrapped in a disclosure. The server sets the initial open state; the user controls it afterwards.") }],
    };
    const collapsibleJava =
`UiSection.of("demo-collapsible", null)
    .section("cs-body", null, UiText.of("cs-text",
        "This whole section is wrapped in a disclosure. The server sets the initial open state; the user controls it afterwards."))
    .collapsible("Collapsible section (click to toggle)", false);`;

    const charts = {
        type: "stack", id: "row-charts", gap: 12, children: ["BAR", "LINE", "AREA", "DONUT", "PIE"].map((t) => ({
            type: "chart", id: `chart-${t.toLowerCase()}`, title: `${t} chart`, chartType: t,
            data: { labels: ["Q1", "Q2", "Q3", "Q4"], series: [{ name: "Revenue", values: [24, 38, 30, 45] }] },
        })),
    };
    const chartsJava =
`var data = new UiChart.ChartData();
data.setLabels(List.of("Q1", "Q2", "Q3", "Q4"));
var revenue = new UiChart.ChartData.Series();
revenue.setName("Revenue");
revenue.setValues(List.of(24, 38, 30, 45));
data.setSeries(List.of(revenue));

UiStack.of(
    UiChart.of("chart-bar",   "BAR chart",   UiChart.ChartType.BAR,   data),
    UiChart.of("chart-line",  "LINE chart",  UiChart.ChartType.LINE,  data),
    UiChart.of("chart-area",  "AREA chart",  UiChart.ChartType.AREA,  data),
    UiChart.of("chart-donut", "DONUT chart", UiChart.ChartType.DONUT, data),
    UiChart.of("chart-pie",   "PIE chart",   UiChart.ChartType.PIE,   data)
).gap(12);

// The chart node is core; the drawing comes from mc-semantic-ui-ext-chart.
// Browser:  import { install } from "/sui-ext/chart/extension.js"; install(renderer);
// Server:   add the dependency — it ships an SSR painter too, so a chart
//           rendered by Spring Boot works with JavaScript switched off.`;

    return stack("tab-layout", [
        specimen("sp-text",     "Text", txt, txtJava),
        specimen("sp-actions",  "Actions (button styles)", actions, actionsJava),
        specimen("sp-links",    "Links", links, linksJava),
        specimen("sp-collapse", "Collapsible section", collapsible, collapsibleJava),
        specimen("sp-charts",   "Charts (drawn by the chart extension)", charts, chartsJava),
    ], { gap: 16 });
}

// ── Tab: Diagram (extension) ─────────────────────────────────────────────────
// The diagram node comes from mc-semantic-ui-ext-diagram — a separate browser
// bundle (/sui-ext). Its handler is registered on the renderer in boot() via
// the extension's install(). Rendered fully client-side, like everything here.
function diagramTab() {
    const diagram = {
        type: "diagram", id: "demo-diagram", width: 580, height: 140,
        nodes: [
            { id: "d-a", shape: "rounded-rect", label: "Start",   position: { x: 20,  y: 42 } },
            { id: "d-b", shape: "rounded-rect", label: "Process", position: { x: 210, y: 42 } },
            { id: "d-c", shape: "rounded-rect", label: "Done",    position: { x: 400, y: 42 } },
        ],
        edges: [
            { id: "d-e1", source: "d-a", target: "d-b", kind: "flow" },
            { id: "d-e2", source: "d-b", target: "d-c", kind: "flow" },
        ],
    };
    const diagramJava =
`var diagram = new UiDiagram();
diagram.setId("demo-diagram");
diagram.setWidth(580);
diagram.setHeight(140);

var a = UiDiagramNode.of("d-a", "rounded-rect", "Start");
a.setPosition(Position.of(20, 42));
var b = UiDiagramNode.of("d-b", "rounded-rect", "Process");
b.setPosition(Position.of(210, 42));
var c = UiDiagramNode.of("d-c", "rounded-rect", "Done");
c.setPosition(Position.of(400, 42));

diagram.addNode(a).addNode(b).addNode(c)
       .addEdge(UiDiagramEdge.flow("d-e1", "d-a", "d-b"))
       .addEdge(UiDiagramEdge.flow("d-e2", "d-b", "d-c"));`;
    // Custom specimen (no "Open in CodePen": the CDN snippet doesn't load the
    // diagram extension bundle).
    return stack("tab-diagram", [
        text("diagram-intro",
            "The diagram node ships in the mc-semantic-ui-ext-diagram module — a graph of shapes and edges rendered to interactive SVG. It's a separate browser bundle, installed on the renderer alongside the core handlers. Everything below is client-side, no backend."),
        stack("sp-diagram-wrap", [
            heading("Flow — 3 shapes wired by arrows"),
            diagram,
            codePanel("sp-diagram", diagram, diagramJava),
        ], { gap: 8 }),
    ], { gap: 16 });
}

// ── Tab: Icons ───────────────────────────────────────────────────────────────
function iconsTab() {
    const buttons = {
        type: "stack", id: "ic-buttons", direction: "HORIZONTAL", gap: 8, children: [
            { type: "action", id: "ic-save",  label: "Save",   icon: "save",   style: "PRIMARY",   onClick: api("POST", "/save") },
            { type: "action", id: "ic-add",   label: "Add",    icon: "add",    style: "SECONDARY", onClick: api("POST", "/add") },
            { type: "action", id: "ic-del",   label: "Delete", icon: "delete", style: "DANGER", confirm: "Delete?", onClick: api("DELETE", "/x") },
            { type: "action", id: "ic-edit",  label: "Edit",   icon: "pencil", appearance: "ICON", style: "SECONDARY", onClick: api("GET", "/edit") },
            { type: "action", id: "ic-more",  label: "More",   icon: "more",   appearance: "ICON", style: "SECONDARY", onClick: api("GET", "/more") },
        ],
    };
    const buttonsJava =
`UiStack.of(
    UiAction.primary("ic-save", "Save").icon("save").onClick(UiTrigger.api("POST", "/save")),
    UiAction.secondary("ic-add", "Add").icon("add").onClick(UiTrigger.api("POST", "/add")),
    UiAction.danger("ic-del", "Delete").icon("delete").confirm("Delete?").onClick(UiTrigger.api("DELETE", "/x")),
    // Icon-only: label becomes the accessible name (aria-label).
    UiAction.secondary("ic-edit", "Edit").icon("pencil").appearance(UiAction.Appearance.ICON).onClick(UiTrigger.api("GET", "/edit")),
    UiAction.secondary("ic-more", "More").icon("more").appearance(UiAction.Appearance.ICON).onClick(UiTrigger.api("GET", "/more"))
).direction(UiStack.Direction.HORIZONTAL).gap(8);`;

    const field = {
        type: "field", id: "ic-search", label: "Search", fieldType: "TEXT",
        editable: true, icon: "search", placeholder: "Filter products…",
    };
    const fieldJava = `UiField.text("ic-search", "Search", null).asEditable().icon("search").placeholder("Filter products…");`;

    const link = { type: "link", id: "ic-link", rel: "ref", href: "https://example.com", label: "External docs", icon: "external", external: true };
    const linkJava = `UiLink.external("ref", "https://example.com", "External docs").icon("external");`;

    // Standalone UiIcon nodes in a row, with status-colour cssClass helpers.
    const gallery = {
        type: "stack", id: "ic-gallery", direction: "HORIZONTAL", gap: 16, children: [
            { type: "icon", id: "ic-ok",   name: "success", title: "Success", cssClass: "sui-icon--success" },
            { type: "icon", id: "ic-warn", name: "warning", title: "Warning", cssClass: "sui-icon--warning" },
            { type: "icon", id: "ic-err",  name: "error",   title: "Error",   cssClass: "sui-icon--danger" },
            { type: "icon", id: "ic-user", name: "user",    title: "User" },
            { type: "icon", id: "ic-star", name: "star",    title: "Star" },
            { type: "icon", id: "ic-emoji", name: "🎉",     title: "Legacy emoji still works" },
        ],
    };
    const galleryJava =
`UiStack.of(
    UiIcon.of("ic-ok",   "success").labelled("Success").withCssClass("sui-icon--success"),
    UiIcon.of("ic-warn", "warning").labelled("Warning").withCssClass("sui-icon--warning"),
    UiIcon.of("ic-err",  "error").labelled("Error").withCssClass("sui-icon--danger"),
    UiIcon.of("ic-user", "user").labelled("User"),
    UiIcon.of("ic-star", "star").labelled("Star"),
    UiIcon.of("ic-emoji", "🎉").labelled("Legacy emoji still works")   // rendered verbatim
).direction(UiStack.Direction.HORIZONTAL).gap(16);`;

    return stack("tab-icons", [
        text("ic-intro", "Icons are tokens (e.g. \"save\", \"delete\") resolved to a curated SVG sprite. They inherit text colour and size. The library is swappable via setIconResolver — the model never names a concrete library."),
        specimen("sp-ic-buttons", "Buttons with icons (leading + icon-only)", buttons, buttonsJava),
        specimen("sp-ic-field",   "In-field icon", field, fieldJava),
        specimen("sp-ic-link",    "Link with icon", link, linkJava),
        specimen("sp-ic-gallery", "UiIcon nodes — status colours + legacy emoji", gallery, galleryJava),
        heading("All icons — search by name, click to copy"),
        { type: "icon-gallery", id: "ic-library", icons: ALL_ICONS },
    ], { gap: 16 });
}

// ── Tab: Feedback (spinners, progress, inline loading) ──────────────────────
function feedbackTab() {
    // Spinners: three sizes + one labelled. Each is a plain UiSpinner node.
    const spinners = {
        type: "stack", id: "fb-spinners", direction: "HORIZONTAL", gap: 24, children: [
            { type: "spinner", id: "fb-sp-sm", size: "SM", title: "Loading" },
            { type: "spinner", id: "fb-sp-md", size: "MD", title: "Loading" },
            { type: "spinner", id: "fb-sp-lg", size: "LG", title: "Loading" },
            { type: "spinner", id: "fb-sp-lbl", label: "Loading…" },
        ],
    };
    const spinnersJava =
`UiStack.of(
    UiSpinner.of().size(UiSpinner.Size.SM).labelled("Loading"),
    UiSpinner.of().size(UiSpinner.Size.MD).labelled("Loading"),
    UiSpinner.of().size(UiSpinner.Size.LG).labelled("Loading"),
    UiSpinner.of("Loading…")
).direction(UiStack.Direction.HORIZONTAL).gap(24);`;

    // Progress bars: determinate at various values + status colours + one
    // indeterminate. "fb-live-bar" is animated post-mount via patches.
    const bars = {
        type: "stack", id: "fb-bars", gap: 14, children: [
            { type: "progress", id: "fb-bar-25", value: 25 },
            { type: "progress", id: "fb-live-bar", value: 0 },
            { type: "progress", id: "fb-bar-100", value: 100, status: "SUCCESS" },
            { type: "progress", id: "fb-bar-warn", value: 80, status: "WARNING", showValue: false },
            { type: "progress", id: "fb-bar-err", value: 45, status: "ERROR" },
            { type: "progress", id: "fb-bar-ind" },
        ],
    };
    const barsJava =
`UiStack.of(
    UiProgress.of(25),
    UiProgress.of(0),                                   // animated live via patches
    UiProgress.of(100).status(UiProgress.Status.SUCCESS),
    UiProgress.of(80).status(UiProgress.Status.WARNING).showValue(false),
    UiProgress.of(45).status(UiProgress.Status.ERROR),
    UiProgress.indeterminate()
).gap(14);`;

    // Circular progress: determinate + success + indeterminate ring.
    const rings = {
        type: "stack", id: "fb-rings", direction: "HORIZONTAL", gap: 24, children: [
            { type: "progress", id: "fb-live-ring", value: 0, variant: "CIRCLE" },
            { type: "progress", id: "fb-ring-100", value: 100, variant: "CIRCLE", status: "SUCCESS" },
            { type: "progress", id: "fb-ring-ind", variant: "CIRCLE" },
        ],
    };
    const ringsJava =
`UiStack.of(
    UiProgress.of(0).variant(UiProgress.Variant.CIRCLE),   // animated live
    UiProgress.of(100).variant(UiProgress.Variant.CIRCLE).status(UiProgress.Status.SUCCESS),
    UiProgress.indeterminate().variant(UiProgress.Variant.CIRCLE)
).direction(UiStack.Direction.HORIZONTAL).gap(24);`;

    // Inline loading: clicking any of these fires a trigger; the event bus
    // paints a spinner on the clicked control until the request resolves. The
    // demo fetcher delays ~700ms so the effect is visible without a backend.
    const buttons = {
        type: "stack", id: "fb-load-btns", direction: "HORIZONTAL", gap: 8, children: [
            { type: "action", id: "fb-load-save", label: "Save", icon: "save", style: "PRIMARY", onClick: api("POST", "/save") },
            { type: "action", id: "fb-load-sync", label: "Sync", style: "SECONDARY", onClick: api("POST", "/sync") },
            { type: "action", id: "fb-load-ref", label: "Refresh", icon: "refresh", appearance: "ICON", style: "SECONDARY", onClick: api("GET", "/refresh") },
            // A UiLink can carry an onClick trigger too — it dispatches through
            // the bus (and gets inline loading) instead of navigating.
            { type: "link", id: "fb-load-link", rel: "more", href: "/more", label: "Load more", onClick: api("GET", "/more") },
        ],
    };
    const buttonsJava =
`// No node opts in — the bus adds an "is-loading" class to whatever control
// the user clicked, for the duration of the dispatch. Suppress it per-page
// with bus.setLoadingPolicy("manual").
UiStack.of(
    UiAction.primary("fb-load-save", "Save").icon("save").onClick(UiTrigger.api("POST", "/save")),
    UiAction.secondary("fb-load-sync", "Sync").onClick(UiTrigger.api("POST", "/sync")),
    UiAction.secondary("fb-load-ref", "Refresh").icon("refresh").appearance(UiAction.Appearance.ICON).onClick(UiTrigger.api("GET", "/refresh")),
    UiLink.of("more", "/more", "Load more").onClick(UiTrigger.api("GET", "/more"))   // link with onClick
).direction(UiStack.Direction.HORIZONTAL).gap(8);`;

    // Declarative loading: a button forced into the busy state by the model
    // (e.g. the server pushes loading:true, then replaces it with the result).
    const stateful = {
        type: "stack", id: "fb-state-btns", direction: "HORIZONTAL", gap: 8, children: [
            { type: "action", id: "fb-state-idle", label: "Save", icon: "save", style: "PRIMARY", onClick: api("POST", "/x") },
            { type: "action", id: "fb-state-busy", label: "Saving…", style: "PRIMARY", loading: true },
        ],
    };
    const statefulJava =
`UiStack.of(
    UiAction.primary("fb-state-idle", "Save").icon("save").onClick(UiTrigger.api("POST", "/x")),
    UiAction.primary("fb-state-busy", "Saving…").loading(true)   // forced busy + disabled
).direction(UiStack.Direction.HORIZONTAL).gap(8);`;

    // Toasts: one button per level. Each fires an inline PATCH whose only payload
    // is a toast — no server call. UiTrigger.toast(...) is the sugar for this.
    const toasts = {
        type: "stack", id: "fb-toast-btns", direction: "HORIZONTAL", gap: 8, children: [
            { type: "action", id: "fb-t-info", label: "Info",    style: "SECONDARY", onClick: toastTrigger("Saved to drafts", "INFO") },
            { type: "action", id: "fb-t-ok",   label: "Success", style: "SECONDARY", onClick: toastTrigger("Changes published", "SUCCESS") },
            { type: "action", id: "fb-t-warn", label: "Warning", style: "SECONDARY", onClick: toastTrigger("Storage almost full", "WARN") },
            { type: "action", id: "fb-t-err",  label: "Error",   style: "SECONDARY", onClick: toastTrigger("Upload failed", "ERROR") },
        ],
    };
    const toastsJava =
`// A toast is a client-side PATCH carrying no DOM ops, just a UiToast.
UiStack.of(
    UiAction.secondary("fb-t-info", "Info").onClick(UiTrigger.toast(UiToast.info("Saved to drafts"))),
    UiAction.secondary("fb-t-ok",   "Success").onClick(UiTrigger.toast(UiToast.success("Changes published"))),
    UiAction.secondary("fb-t-warn", "Warning").onClick(UiTrigger.toast(UiToast.warn("Storage almost full"))),
    UiAction.secondary("fb-t-err",  "Error").onClick(UiTrigger.toast(UiToast.error("Upload failed")))
).direction(UiStack.Direction.HORIZONTAL).gap(8);`;

    // Dialog: a modal overlay. It is "opened" by APPENDing a UiDialog node into
    // the body-level #sui-dialogs host and "closed" by REMOVE-ing it by id (the
    // × / backdrop do that automatically). Both are plain inline PATCH triggers —
    // no backend. Confirm both closes the dialog and fires a success toast.
    const confirmDialog = {
        type: "dialog", id: "fb-dialog", title: "Delete customer?",
        node: {
            type: "stack", id: "fb-dlg-body", gap: 14, children: [
                { type: "text", id: "fb-dlg-msg", text: "This permanently removes Ada Lovelace and all associated orders. This can’t be undone." },
                {
                    type: "stack", id: "fb-dlg-actions", direction: "HORIZONTAL", gap: 8, children: [
                        { type: "action", id: "fb-dlg-cancel", label: "Cancel", style: "SECONDARY",
                          onClick: { behavior: "PATCH", patch: { patches: [{ op: "REMOVE", targetId: "fb-dialog" }], toasts: [] } } },
                        { type: "action", id: "fb-dlg-confirm", label: "Delete", style: "PRIMARY",
                          onClick: { behavior: "PATCH", patch: { patches: [{ op: "REMOVE", targetId: "fb-dialog" }], toasts: [{ level: "SUCCESS", message: "Customer deleted", durationMs: 2200 }] } } },
                    ],
                },
            ],
        },
    };
    const dialogOpener = {
        type: "action", id: "fb-dlg-open", label: "Delete customer…", icon: "delete", style: "PRIMARY",
        onClick: { behavior: "PATCH", patch: { patches: [{ op: "APPEND", targetId: "sui-dialogs", node: confirmDialog }], toasts: [] } },
    };
    const dialogJava =
`// The dialog body — reused as the node that gets appended into #sui-dialogs.
UiDialog dialog = UiDialog.of("Delete customer?", null, UiStack.of(
    UiText.of("This permanently removes Ada Lovelace and all associated orders. This can’t be undone."),
    UiStack.of(
        UiAction.secondary("fb-dlg-cancel", "Cancel")
            .onClick(UiTrigger.patch(UiPatch.Operation.remove("fb-dialog"))),
        UiAction.primary("fb-dlg-confirm", "Delete")
            .onClick(UiTrigger.patch(UiPatch.of()
                .patch(UiPatch.Operation.remove("fb-dialog"))
                .toast(UiToast.success("Customer deleted"))))
    ).direction(UiStack.Direction.HORIZONTAL).gap(8)
).gap(14));

// Open it: APPEND the dialog into the persistent #sui-dialogs host.
UiAction.primary("fb-dlg-open", "Delete customer…").icon("delete")
    .onClick(UiTrigger.patch(UiPatch.Operation.append("sui-dialogs", dialog)));`;

    return stack("tab-feedback", [
        text("fb-intro", "Two kinds of loading feedback. A UiSpinner / UiProgress node is declarative — you place it in the tree and replace it via a patch when data arrives. Inline loading is automatic — the event bus marks the clicked control busy for the duration of its request. No node needed."),
        specimen("sp-fb-spin", "Spinners — SM · MD · LG · labelled", spinners, spinnersJava),
        specimen("sp-fb-bars", "Progress bars — determinate, status colours, indeterminate", bars, barsJava),
        specimen("sp-fb-rings", "Circular progress", rings, ringsJava),
        specimen("sp-fb-load", "Inline loading on click — press a button (demo delays ~700ms)", buttons, buttonsJava),
        specimen("sp-fb-state", "Declarative loading — a button set busy by the model (loading:true)", stateful, statefulJava),
        specimen("sp-fb-toast", "Toasts — inline PATCH, one per level (info · success · warn · error)", toasts, toastsJava),
        specimen("sp-fb-dialog", "Dialog — modal overlay opened/closed by an inline PATCH", dialogOpener, dialogJava),
    ], { gap: 16 });
}

// ── Tab: Navigation (collapsible sidebar menu) ──────────────────────────────
function navTab() {
    const menu = {
        type: "menu", id: "demo-menu", title: "Admin", state: "EXPANDED",
        items: [
            { type: "menu-item", id: "nm-dash", label: "Dashboard", icon: "dashboard", href: "/dash", selected: true },
            { type: "menu-item", id: "nm-cat", label: "Catalog", icon: "grid", open: true, children: [
                { type: "menu-item", id: "nm-prod", label: "Products",  icon: "tag",      href: "/products" },
                { type: "menu-item", id: "nm-cust", label: "Customers", icon: "users",    href: "/customers" },
                { type: "menu-item", id: "nm-inv",  label: "Inventory", icon: "database", href: "/inventory" },
            ] },
            { type: "menu-item", id: "nm-orders", label: "Orders", icon: "table", onClick: api("GET", "/orders") },
            { type: "menu-item", id: "nm-set", label: "Settings", icon: "settings", children: [
                { type: "menu-item", id: "nm-users", label: "Users",    icon: "user", href: "/settings/users" },
                { type: "menu-item", id: "nm-sec",   label: "Security", icon: "lock", href: "/settings/security" },
            ] },
            { type: "menu-item", id: "nm-logout", label: "Log out", icon: "logout", href: "/logout" },
        ],
    };
    const menuJava =
`UiMenu.of("demo-menu", "Admin",
    UiMenuItem.link("nm-dash", "Dashboard", "/dash").icon("dashboard").selected(true),
    UiMenuItem.group("nm-cat", "Catalog",
        UiMenuItem.link("nm-prod", "Products",  "/products").icon("tag"),
        UiMenuItem.link("nm-cust", "Customers", "/customers").icon("users"),
        UiMenuItem.link("nm-inv",  "Inventory", "/inventory").icon("database")
    ).icon("grid").open(true),
    UiMenuItem.of("nm-orders", "Orders").icon("table").onClick(UiTrigger.api("GET", "/orders")),
    UiMenuItem.group("nm-set", "Settings",
        UiMenuItem.link("nm-users", "Users",    "/settings/users").icon("user"),
        UiMenuItem.link("nm-sec",   "Security", "/settings/security").icon("lock")
    ).icon("settings"),
    UiMenuItem.link("nm-logout", "Log out", "/logout").icon("logout")
).state(UiMenu.State.EXPANDED);`;

    // A menu-button: a trigger that opens a floating dropdown / context menu.
    // Placeable anywhere; here a kebab (icon-only) + a labelled "Actions" button.
    // Items fire backend-free toasts so it's live with no server (and in a pen).
    const menuBtns = {
        type: "stack", id: "mb-row", direction: "HORIZONTAL", gap: 16, children: [
            { type: "menu-button", id: "mb-kebab", align: "START", items: [
                { type: "menu-item", id: "mb-k-edit",  label: "Rename",    icon: "edit",   onClick: toastTrigger("Rename") },
                { type: "menu-item", id: "mb-k-dup",   label: "Duplicate", icon: "copy",   onClick: toastTrigger("Duplicated") },
                { type: "menu-item", id: "mb-k-share", label: "Share",     icon: "share",  onClick: toastTrigger("Share link copied", "SUCCESS") },
                { type: "menu-item", id: "mb-k-sep", divider: true },
                { type: "menu-item", id: "mb-k-del",   label: "Delete",    icon: "delete", danger: true, onClick: toastTrigger("Deleted", "ERROR") },
            ] },
            { type: "menu-button", id: "mb-actions", label: "Actions", items: [
                { type: "menu-item", id: "mb-a-exp", label: "Export CSV", icon: "download", onClick: toastTrigger("Exporting…") },
                { type: "menu-item", id: "mb-a-ref", label: "Refresh",    icon: "refresh",  onClick: toastTrigger("Refreshed") },
                // A nested submenu — the same UiMenuItem.children the sidebar uses.
                { type: "menu-item", id: "mb-a-move", label: "Move to", icon: "share", children: [
                    { type: "menu-item", id: "mb-a-mv1", label: "Inbox",   onClick: toastTrigger("Moved to Inbox") },
                    { type: "menu-item", id: "mb-a-mv2", label: "Archive", onClick: toastTrigger("Moved to Archive") },
                    { type: "menu-item", id: "mb-a-sep", divider: true },
                    { type: "menu-item", id: "mb-a-mv3", label: "Trash",   danger: true, onClick: toastTrigger("Moved to Trash", "ERROR") },
                ] },
                { type: "menu-item", id: "mb-a-set", label: "Settings",   icon: "settings", href: "/settings" },
            ] },
        ],
    };
    const menuBtnsJava =
`UiStack.of(
    // Icon-only "kebab" — a context menu, aligned to its start edge.
    UiMenuButton.of("mb-kebab",
        UiMenuItem.of("mb-k-edit",  "Rename").icon("edit").onClick(UiTrigger.toast("Rename")),
        UiMenuItem.of("mb-k-dup",   "Duplicate").icon("copy").onClick(UiTrigger.toast("Duplicated")),
        UiMenuItem.of("mb-k-share", "Share").icon("share").onClick(UiTrigger.toast(UiToast.success("Share link copied"))),
        UiMenuItem.divider(),
        UiMenuItem.of("mb-k-del",   "Delete").icon("delete").danger(true).onClick(UiTrigger.toast(UiToast.error("Deleted")))
    ).align(UiMenuButton.Align.START),
    // Labelled dropdown button — with a nested submenu (UiMenuItem.group).
    UiMenuButton.of("mb-actions",
        UiMenuItem.of("mb-a-exp", "Export CSV").icon("download").onClick(UiTrigger.toast("Exporting…")),
        UiMenuItem.of("mb-a-ref", "Refresh").icon("refresh").onClick(UiTrigger.toast("Refreshed")),
        UiMenuItem.group("mb-a-move", "Move to",           // same nesting as the sidebar
            UiMenuItem.of("mb-a-mv1", "Inbox").onClick(UiTrigger.toast("Moved to Inbox")),
            UiMenuItem.of("mb-a-mv2", "Archive").onClick(UiTrigger.toast("Moved to Archive")),
            UiMenuItem.divider(),
            UiMenuItem.of("mb-a-mv3", "Trash").danger(true).onClick(UiTrigger.toast(UiToast.error("Moved to Trash")))
        ).icon("share"),
        UiMenuItem.link("mb-a-set", "Settings", "/settings").icon("settings")
    ).label("Actions")
).direction(UiStack.Direction.HORIZONTAL).gap(16);`;

    return stack("tab-nav", [
        text("nav-intro", "A collapsible sidebar for admin shells. Click the ☰ hamburger to cycle three states: expanded (icon + label) → rail (icons only; hover a group for a fly-out submenu, hover a leaf for its tooltip) → hidden. The choice is remembered in localStorage. Groups nest arbitrarily and expand inline when expanded. Without JS the items are real links, groups are native <details>, and the hamburger still shows/hides via a checkbox."),
        specimen("sp-nav-menu", "Sidebar menu — click ☰ to cycle expanded · rail · hidden", menu, menuJava),
        heading("Menu button — dropdown & context menus"),
        text("mb-intro", "A UiMenuButton opens a floating menu anchored to itself: an icon-only \"kebab\" (a context menu) or a labelled dropdown. It's a native <details> so it opens with no JS; the SPA repositions the popover with position:fixed so it's never clipped by a scrolling or overflow-hidden ancestor. Items reuse UiMenuItem (icon, danger, dividers, badges — and children for a nested submenu, the same nesting the sidebar uses; try “Actions → Move to”). Open one, then click outside or press Escape to close. It's placeable anywhere — see the Tree tab for a per-row context menu."),
        specimen("sp-menu-btn", "Menu button — kebab (context) + labelled dropdown", menuBtns, menuBtnsJava),
        heading("Complete app shell — header + sidebar + content"),
        text("shell-intro", "Three ways the sidebar relates to the content, all the same node tree with a different mode. PUSH: the sidebar occupies layout space, so content reflows wider as it collapses (☰ cycles expanded → rail → gone). OVERLAY: a drawer floating over the content with a backdrop; content stays put (click the backdrop or ☰ to close). RESPONSIVE: push on a wide screen (☰ flips expanded ⇄ rail, never fully gone) and an overlay drawer on a narrow one (closed by default) — resize the preview narrow to see it flip. In all three the hamburger lives in the header (UiHeader.menuToggle)."),
        specimen("sp-shell-push", "App shell — PUSH (content reflows)", appShell("shellA", "PUSH"), appShellJava("PUSH", "LEFT")),
        specimen("sp-shell-overlay", "App shell — OVERLAY, right side (drawer from the right)", appShell("shellB", "OVERLAY", "RIGHT"), appShellJava("OVERLAY", "RIGHT")),
        specimen("sp-shell-resp", "App shell — RESPONSIVE (rail on desktop · drawer on mobile)", appShell("shellC", "RESPONSIVE"), appShellJava("RESPONSIVE", "LEFT")),
    ], { gap: 16 });
}

// A complete admin shell. Since UiAppShell exists this is one node: it wires
// the header's burger to the menu, switches the menu's own toggle off, and
// carries its layout in sui.css. The demo only adds a frame around it.
function appShell(id, mode, side) {
    const menuId = `${id}-menu`;
    const p = id;
    // Leaf items fire a backend-free toast on click (inline PATCH) so the shell
    // is interactive without a server — and stays that way in an exported pen.
    const navItems = [
        { type: "menu-item", id: `${p}-dash`, label: "Dashboard", icon: "dashboard", selected: true, onClick: toastTrigger("Dashboard") },
        { type: "menu-item", id: `${p}-cat`, label: "Catalog", icon: "grid", open: true, children: [
            { type: "menu-item", id: `${p}-prod`, label: "Products",  icon: "tag",   onClick: toastTrigger("Opened Products") },
            { type: "menu-item", id: `${p}-cust`, label: "Customers", icon: "users", onClick: toastTrigger("Opened Customers") },
        ] },
        { type: "menu-item", id: `${p}-ord`, label: "Orders", icon: "table", badge: "12", onClick: toastTrigger("Opened Orders (12 new)") },
        { type: "menu-item", id: `${p}-set`, label: "Settings", icon: "settings", onClick: toastTrigger("Opened Settings") },
    ];
    const right = side === "RIGHT";
    // No menuToggle / toggle wiring here — the app-shell node does both.
    const menu = { type: "menu", id: menuId, title: "Acme", state: "EXPANDED", mode, side: side || "LEFT", items: navItems };
    const header = { type: "header", id: `${p}-hdr`, brand: "Acme Admin",
        user: { name: "Ada Lovelace", initials: "AL" } };
    const content = { type: "stack", id: `${p}-content`, gap: 12, children: [
        { type: "text", id: `${p}-h`, text: "Dashboard", cssClass: "demo-h" },
        { type: "text", id: `${p}-t`, text:
            mode === "OVERLAY" ? `The sidebar floats over this panel as a drawer${right ? " from the right" : ""}. Click ☰ to open it, then the dimmed backdrop (or ☰ again) to close — this text never moves.`
            : mode === "RESPONSIVE" ? "On a wide screen ☰ flips the sidebar expanded ⇄ rail (it never fully disappears). Make the preview narrow (< 768px) and ☰ turns it into an overlay drawer that's hidden by default — the mobile pattern."
            : "The sidebar shares the row with this panel. Click ☰ to collapse it to a rail, then away entirely — watch this panel reflow wider each time." },
    ] };
    // One app-shell node instead of two nested stacks: it wires the header's
    // burger to the menu, switches off the menu's own toggle, and brings its
    // own layout CSS. fillViewport:false because these are small embedded
    // frames inside the showcase, not the whole window.
    return {
        type: "app-shell", id, cssClass: "demo-shell", fillViewport: false,
        header, menu, content,
        footer: { type: "text", id: `${p}-foot`, text: `Menu mode: ${mode}${right ? " · right side" : ""}` },
    };
}

function appShellJava(mode, side) {
    const sideCall = side === "RIGHT" ? ".side(UiMenu.Side.RIGHT)" : "";
    return `// One node. It points the header's burger at the menu, switches the
// menu's own toggle off, and brings its layout with it — no shell CSS.
var menu = UiMenu.of("nav", "Acme",
    UiMenuItem.of("dash", "Dashboard").icon("dashboard").selected(true).onClick(UiTrigger.toast("Dashboard")),
    UiMenuItem.group("cat", "Catalog",
        UiMenuItem.of("prod", "Products").icon("tag").onClick(UiTrigger.toast("Opened Products")),
        UiMenuItem.of("cust", "Customers").icon("users").onClick(UiTrigger.toast("Opened Customers"))
    ).icon("grid").open(true),
    UiMenuItem.of("ord", "Orders").icon("table").badge("12").onClick(UiTrigger.toast("Opened Orders (12 new)")),
    UiMenuItem.of("set", "Settings").icon("settings").onClick(UiTrigger.toast("Opened Settings"))
).mode(UiMenu.Mode.${mode})${sideCall};

UiAppShell.of("shell")
    .header(UiHeader.of("Acme Admin")
        .user(UiHeader.User.of("Ada Lovelace", "AL", "/me")))
    .menu(menu)
    .content(contentPanel)
    .footer(UiText.of("foot", "Menu mode: ${mode}"))
    // Embedded in the showcase, so it takes the frame's height rather than
    // the window's. Leave it out for a real application.
    .fillViewport(false);`;
}

function buildPage() {
    return stack("demo-root", [
        pageHeader(),
        introNote(),
        {
            type: "section", id: "demo-tabs", initialSection: "sec-tree", tabOverflow: "MENU",
            sections: [
                { type: "section-entry", id: "sec-tree",   title: "Tree",            content: treeTab() },
                { type: "section-entry", id: "sec-nav",    title: "Navigation", icon: "menu", content: navTab() },
                { type: "section-entry", id: "sec-data",   title: "Lists & Tables",  content: dataTab() },
                { type: "section-entry", id: "sec-forms",  title: "Forms",           content: formsTab() },
                { type: "section-entry", id: "sec-layout", title: "Layout & Charts", content: layoutTab() },
                { type: "section-entry", id: "sec-diagram", title: "Diagram", icon: "grid", content: diagramTab() },
                { type: "section-entry", id: "sec-feedback", title: "Feedback", icon: "loading", content: feedbackTab() },
                { type: "section-entry", id: "sec-icons",  title: "Icons",           icon: "star", content: iconsTab() },
            ],
        },
    ], { gap: 20 });
}

// ── Custom node renderer: a syntax-neutral code block ───────────────────────
function renderCode(node) {
    return `<pre class="demo-code" id="${escapeHtml(node.id)}"><code>${escapeHtml(node.code)}</code></pre>`;
}

// ── Custom node renderer: "Open in CodePen" button ──────────────────────────
// The example is plain UiNode JSON + the pre-compiled renderer, so no build is
// needed — a browser-only sandbox (CodePen) boots it instantly off the CDN
// bundle. wireCodePen() turns the click into a CodePen prefill POST.
const SUI_CDN = "https://mindconnect-ai.github.io/mc-docs/sui";
function renderCodePen(node) {
    return `<button type="button" class="demo-codepen" data-json='${escapeHtml(node.json)}'>${renderIcon("external")} Open in CodePen</button>`;
}
function openInCodePen(json) {
    // The whole example lives in the HTML panel as a module script — that keeps
    // ES-module imports working in any browser-only sandbox, no Node needed.
    const html =
`<link rel="stylesheet" href="${SUI_CDN}/sui.css">
<div id="app"></div>
<script type="module">
  import { createDefaultRenderer, setIconSpriteUrl } from "${SUI_CDN}/renderer.js";
  import { SuiEventBus } from "${SUI_CDN}/eventbus.js";
  // Extensions the showcase uses, so a pen containing a chart or a diagram
  // draws rather than showing an empty placeholder.
  import { install as installChart } from "${SUI_CDN}-ext/chart/extension.js";
  const node = ${json};
  const root = document.getElementById("app");
  // Icons: a cross-origin SVG <use> is blocked by the browser, so inline the
  // sprite once and point icons at same-document refs (<use href="#id">).
  try {
    const sprite = await fetch("${SUI_CDN}/icons.svg").then(r => r.text());
    const holder = document.createElement("div");
    holder.style.display = "none"; holder.innerHTML = sprite;
    document.body.prepend(holder);
    setIconSpriteUrl("");
  } catch (e) { /* icons just won't show */ }
  const renderer = createDefaultRenderer().attach(root);
  installChart(renderer);
  const bus = new SuiEventBus(renderer, root);
  // There is no backend in a pen, but some examples carry server-bound triggers
  // (the Tree's go("/files/…"), the Save/Sync buttons' api("POST","/save")).
  // Instead of a silent no-op, the stub fetcher answers every such call with a
  // UiPatch whose only payload is a toast naming the request — so a click in the
  // pen shows visible feedback ("Demo — would GET /files/app.ts") rather than
  // appearing to do nothing. A real app would return a real UiPage/UiPatch here.
  bus.setFetcher((input, init = {}) => {
    const url = typeof input === "string" ? input : (input && input.url) || "";
    const method = (init.method || "GET").toUpperCase();
    const body = JSON.stringify({ patches: [], toasts: [
      { level: "INFO", message: "Demo — no backend wired. Would " + method + " " + url, durationMs: 2600 }
    ] });
    return Promise.resolve(new Response(body, { headers: { "Content-Type": "application/json" } }));
  });
  renderer.mount(node);
<\/script>`;
    // Base page CSS only. The app-shell layout (positioning context, content
    // min-width, viewport fill) ships in sui.css now, so a pen containing a
    // shell lays out correctly with nothing extra — the rule below is just the
    // demo's framing.
    const css =
`body { margin: 20px; font-family: system-ui, sans-serif; background: #f8fafc; }
.demo-shell { border: 1px solid var(--sui-color-border); border-radius: 12px; overflow: hidden; min-height: 340px; }`;
    const data = { title: "Semantic UI — example", html, css, js: "", editors: "100" };
    // Open the new tab up-front, inside the click gesture, and give it a name.
    // Submitting the POST form at that named window is what reliably lands the
    // pen in a *new* tab: a bare target="_blank" on a scripted submit is often
    // treated as a popup and either blocked or redirected into the current tab
    // (and the POST body can be dropped). A pre-opened named window avoids both.
    const winName = "sui_codepen_" + Date.now();
    const tab = window.open("about:blank", winName);
    const form = document.createElement("form");
    form.method = "POST"; form.action = "https://codepen.io/pen/define";
    form.target = tab ? winName : "_blank";   // fall back to _blank if popup was blocked
    const input = document.createElement("input");
    input.type = "hidden"; input.name = "data"; input.value = JSON.stringify(data);
    form.appendChild(input); document.body.appendChild(form); form.submit(); form.remove();
}
function wireCodePen(root) {
    root.addEventListener("click", (e) => {
        const btn = e.target.closest(".demo-codepen");
        if (btn) openInCodePen(btn.dataset.json);
    });
}

// ── Custom node renderer: searchable icon gallery ───────────────────────────
// A demo-only node type. Each cell uses the core's own renderIcon() so it goes
// through the exact same (swappable) resolver as every other icon on the page.
// Search + click-to-copy are wired post-mount in wireIconGallery().
function renderIconGallery(node) {
    const names = node.icons || [];
    const cells = names.map(name =>
        `<button type="button" class="icon-cell" data-name="${escapeHtml(name)}" title="Click to copy “${escapeHtml(name)}”">
            ${renderIcon(name)}
            <span class="icon-cell-name">${escapeHtml(name)}</span>
        </button>`).join("");
    return `<div class="icon-gallery" id="${escapeHtml(node.id)}">
        <div class="icon-gallery-toolbar">
            <input type="search" class="icon-gallery-search" placeholder="Search ${names.length} icons by name…" aria-label="Search icons">
            <span class="icon-gallery-count" data-total="${names.length}">${names.length} icons</span>
        </div>
        <div class="icon-gallery-grid">${cells}</div>
        <div class="icon-gallery-empty" hidden>No icons match.</div>
    </div>`;
}

// Post-mount wiring for the icon gallery: pure-DOM filter + click-to-copy.
function wireIconGallery(root) {
    const gallery = root.querySelector(".icon-gallery");
    if (!gallery) return;
    const search = gallery.querySelector(".icon-gallery-search");
    const count  = gallery.querySelector(".icon-gallery-count");
    const empty  = gallery.querySelector(".icon-gallery-empty");
    const cells  = [...gallery.querySelectorAll(".icon-cell")];
    const total  = cells.length;
    search.addEventListener("input", () => {
        const q = search.value.trim().toLowerCase();
        let shown = 0;
        for (const c of cells) {
            const match = !q || c.dataset.name.includes(q);
            c.hidden = !match;
            if (match) shown++;
        }
        count.textContent = shown === total ? `${total} icons` : `${shown} / ${total}`;
        empty.hidden = shown !== 0;
    });
    gallery.addEventListener("click", (e) => {
        const cell = e.target.closest(".icon-cell");
        if (!cell) return;
        const name = cell.dataset.name;
        if (navigator.clipboard) navigator.clipboard.writeText(name).catch(() => {});
        showToast(`Copied “${name}”`);
    });
}

// Animate the "live" progress bar + ring by replacing their nodes via patches
// — exactly how a server would push progress: a REPLACE op on the node id. This
// is the idiomatic alternative to poking the DOM directly, and it exercises the
// same patch path the SPA uses for real server-driven updates.
function wireLiveProgress(renderer) {
    let pct = 0;
    setInterval(() => {
        pct = (pct + 5) % 105;               // 0 → 100, then wrap back to 0
        const v = Math.min(pct, 100);
        const done = v === 100;
        renderer.applyPatch({ patches: [
            { op: "REPLACE", targetId: "fb-live-bar",
              node: { type: "progress", id: "fb-live-bar", value: v, status: done ? "SUCCESS" : "NORMAL" } },
            { op: "REPLACE", targetId: "fb-live-ring",
              node: { type: "progress", id: "fb-live-ring", value: v, variant: "CIRCLE", status: done ? "SUCCESS" : "NORMAL" } },
        ] });
    }, 500);
}

// ── A tiny client-side toast, so triggers give visible feedback ─────────────
function showToast(message) {
    const el = document.createElement("div");
    el.textContent = message;
    el.style.cssText = "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);" +
        "background:var(--sui-color-text-strong);color:var(--sui-color-surface);" +
        "padding:8px 14px;border-radius:6px;font-size:13px;box-shadow:var(--sui-shadow-card);z-index:1000;opacity:0;transition:opacity .15s";
    document.body.appendChild(el);
    requestAnimationFrame(() => { el.style.opacity = "1"; });
    setTimeout(() => { el.style.opacity = "0"; setTimeout(() => el.remove(), 200); }, 1600);
}

// Exported so the page tree can be rendered/tested outside the browser too.
export { buildPage, renderCode };

// ── Boot ────────────────────────────────────────────────────────────────────
// Guarded so the module can be imported in a non-DOM environment (e.g. a Node
// smoke test that just renders buildPage() to a string).
// Viewport toggle: swap the whole showcase for a phone-width iframe of itself,
// so the REAL @media breakpoints fire (viewport-based rules like the stacked
// table and the responsive sidebar drawer don't trigger from a narrow container
// alone — they need a narrow viewport, which the iframe provides).
function wireViewportToggle() {
    const btn = document.getElementById("demo-viewport");
    const main = document.querySelector(".demo-main");
    const root = document.getElementById("sui-root");
    if (!btn || !main || !root) return;
    // Real sprite icons: a smartphone when offering the mobile preview, a
    // monitor when offering to go back to desktop.
    const setBtn = (offerMobile) => {
        btn.innerHTML = offerMobile
            ? `${renderIcon("smartphone")}<span>Mobile view</span>`
            : `${renderIcon("monitor")}<span>Desktop view</span>`;
    };
    setBtn(true);
    let device = null;
    btn.addEventListener("click", () => {
        if (device) {
            device.remove(); device = null;
            root.style.display = "";
            setBtn(true);
            btn.classList.remove("active");
            return;
        }
        root.style.display = "none";
        device = document.createElement("div");
        device.className = "demo-device";
        const sep = location.search ? "&" : "?";
        device.innerHTML =
            `<div class="demo-device-notch"></div>` +
            `<iframe title="Mobile preview" src="${location.pathname}${location.search}${sep}embedded=1"></iframe>`;
        main.appendChild(device);
        // The iframe is a separate document — mirror the current theme into it
        // once it loads (and every later theme switch is propagated too).
        const frame = device.querySelector("iframe");
        frame.addEventListener("load", () => applyDemoTheme(currentDemoTheme()));
        setBtn(false);
        btn.classList.add("active");
    });
}

/** Current theme class from the top-bar selector (empty string = light). */
function currentDemoTheme() {
    const sel = document.getElementById("demo-theme");
    return sel ? sel.value : "";
}

/** Applies a theme class to the shell AND the phone-frame iframe, if present. */
function applyDemoTheme(value) {
    document.documentElement.className = value;
    const frame = document.querySelector(".demo-device iframe");
    if (frame && frame.contentDocument) {
        frame.contentDocument.documentElement.className = value;
    }
}

async function boot() {
    // Inside the phone-frame iframe: drop the outer chrome (top bar) so the
    // preview shows just the app, and don't offer a nested viewport toggle.
    const embedded = new URLSearchParams(location.search).has("embedded");
    if (embedded) document.body.classList.add("embedded");

    // Load the sprite's token list first so the icon-library gallery is
    // populated on the initial render.
    await loadIconList();

    const root = document.getElementById("sui-root");
    const renderer = createDefaultRenderer().attach(root);
    installChart(renderer);                               // "chart" node (extension)
    renderer.register("code", renderCode);                // custom code-block node
    renderer.register("codepen", renderCodePen);          // "Open in CodePen" button
    renderer.register("icon-gallery", renderIconGallery); // searchable icon grid
    installDiagram(renderer);                             // "diagram" node (extension)

    const bus = new SuiEventBus(renderer, root);
    // No backend: fake the server. A small delay is deliberate — it lets the
    // inline loading feedback (the spinner the bus paints on the clicked
    // control) actually be visible before the response lands.
    const jsonResponse = (obj) => new Promise(resolve => setTimeout(
        () => resolve(new Response(JSON.stringify(obj), { headers: { "Content-Type": "application/json" } })),
        700));

    // No backend at all: every server-shaped trigger just resolves to an empty
    // patch (with a short delay so the inline loading spinner is visible). The
    // form's Save/Cancel don't come through here — they carry inline PATCH
    // triggers and are applied client-side, so they work in an exported CodePen
    // too. Delay is deliberate — lets the "is-loading" feedback show.
    bus.setFetcher((input, init = {}) => {
        const url = typeof input === "string" ? input : (input && input.url) || "";
        const method = (init && init.method) || "GET";
        showToast(`${method} ${url} — no backend (demo)`);
        return jsonResponse({ patches: [] });
    });

    renderer.mount(buildPage());
    // Tab overflow, menu-button popovers and the menu's persisted collapse state
    // are wired automatically by the SuiEventBus (it watches its root and runs
    // the enhancers on every render) — no wireTabOverflow()/wireMenuButtons() by
    // hand. Only the demo-specific bits below need explicit wiring.
    wireIconGallery(root);   // search + click-to-copy for the icon library
    wireCodePen(root);       // "Open in CodePen" buttons
    wireLiveProgress(renderer);   // animate the "live" progress bar + ring
    if (!embedded) wireViewportToggle();   // 📱 phone-frame preview button

    // Theme switcher — toggles the class on <html>; the stylesheets are all loaded.
    const themeSelect = document.getElementById("demo-theme");
    if (themeSelect) {
        themeSelect.addEventListener("change", () => {
            applyDemoTheme(themeSelect.value);   // shell + phone-frame iframe
        });
    }
}

if (typeof document !== "undefined") boot();
