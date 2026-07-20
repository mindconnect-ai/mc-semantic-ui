/*
 * Semantic UI — Client-Only Shop (backend-free).
 *
 * The whole app runs in the browser. There is no server, no fetch. It shows
 * the three trigger styles that need no backend:
 *
 *   • INVOKE  — the trigger names a JS function registered on the bus
 *               (`bus.registerClientHandler`). The function reads/mutates
 *               local state and returns a UiPage / UiPatch. Used here for
 *               search, the detail dialog, delete, reset, and a checkbox that
 *               enables/disables another field (a state-dependent update).
 *
 *   • PATCH   — the trigger carries a UiPatch inline (`trigger.patch`). No
 *               function at all: the patch is baked in at render time and the
 *               bus just applies it. Used here for the catalog list → detail
 *               pane fill: clicking an item swaps the pane with zero round-trip
 *               and zero handler code.
 *
 *   • onChange — a field-level trigger fired on value change. Used for the
 *               "different delivery address" checkbox below.
 *
 * Data lives in an in-memory array seeded on first load and persisted to
 * localStorage, so deletes and resets survive a page reload.
 */
import { createDefaultRenderer } from "./sui/renderer.js";
import { SuiEventBus } from "./sui/eventbus.js";

// ── Data layer: in-memory array, persisted to localStorage ──────────────────

const STORAGE_KEY = "sui-shop-client-products";

const SEED = [
    { id: "p-1",  sku: "CHAIR-01", name: "Ergo Office Chair",       category: "Furniture",   price: 249.00, stock: 34,  description: "Ergonomic office chair with adjustable lumbar support and a mesh back." },
    { id: "p-2",  sku: "DESK-14",  name: "Height-Adjustable Desk",  category: "Furniture",   price: 599.00, stock: 12,  description: "Electric sit-stand desk, 160×80 cm, with memory positions." },
    { id: "p-3",  sku: "LAMP-07",  name: "LED Desk Lamp",           category: "Lighting",    price: 39.90,  stock: 120, description: "Dimmable LED lamp with adjustable color temperature and a USB charging port." },
    { id: "p-4",  sku: "MON-27",   name: "27\" 4K Monitor",          category: "Electronics", price: 429.00, stock: 21,  description: "27-inch IPS panel, 4K UHD, USB-C with 90 W power delivery." },
    { id: "p-5",  sku: "KEY-88",   name: "Mechanical Keyboard",     category: "Electronics", price: 89.90,  stock: 58,  description: "Mechanical keyboard, hot-swap switches, US layout." },
    { id: "p-6",  sku: "MOU-05",   name: "Vertical Mouse",          category: "Electronics", price: 54.90,  stock: 76,  description: "Ergonomic vertical wireless mouse, reduces wrist strain." },
    { id: "p-7",  sku: "HEAD-33",  name: "Noise-Cancelling Headset", category: "Audio",      price: 179.00, stock: 40,  description: "Over-ear headset with active noise cancellation, 30 h battery." },
    { id: "p-8",  sku: "PLANT-02", name: "Monstera Deliciosa",      category: "Decor",       price: 24.50,  stock: 9,   description: "Low-maintenance houseplant in a 17 cm pot, approx. 60 cm tall." },
    { id: "p-9",  sku: "MUG-11",   name: "Travel Mug 400 ml",       category: "Kitchen",     price: 18.90,  stock: 200, description: "Double-walled stainless-steel travel mug, keeps drinks warm for 6 h." },
    { id: "p-10", sku: "NOTE-A5",  name: "A5 Dotted Notebook",      category: "Office",      price: 12.00,  stock: 340, description: "Hardcover notebook, 192 pages, dotted, with a ribbon marker." },
];

function loadProducts() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) return JSON.parse(raw);
    } catch (_) { /* corrupt / unavailable storage → fall back to seed */ }
    const seeded = SEED.map(p => ({ ...p }));
    saveProducts(seeded);
    return seeded;
}

function saveProducts(list) {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(list)); }
    catch (_) { /* storage full / disabled — demo still works in-memory */ }
}

let products = loadProducts();

const byId = (id) => products.find(p => p.id === id);
const money = (n) => new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(n);

// ── UiNode builders (plain JS literals — the same JSON a server would emit) ──

function header() {
    return {
        type: "header", id: "shop-header", brand: "🛒 Client Shop",
        user: { name: "Ada Lovelace", initials: "AL" },
        extras: [
            { type: "action", id: "reset", label: "↺ Reset demo data", style: "SECONDARY",
              onClick: { behavior: "INVOKE", handler: "products.reset" } },
        ],
    };
}

function sectionTitle(id, text, note) {
    return { type: "stack", id, gap: 2, children: [
        { type: "text", id: `${id}-t`, text, cssClass: "demo-section-title" },
        note ? { type: "text", id: `${id}-n`, text: note, cssClass: "demo-section-note" } : { type: "text", id: `${id}-n`, text: "" },
    ] };
}

// ── Part 1: table + INVOKE detail dialog ────────────────────────────────────

function searchForm(query) {
    return {
        type: "form", id: "product-search",
        fields: [
            { type: "field", id: "q", label: "Search", fieldType: "TEXT", value: query ?? "",
              editable: true, placeholder: "name, SKU or category", submitOnEnter: true },
        ],
        actions: [
            { type: "action", id: "search", label: "Search", style: "PRIMARY",
              onClick: { behavior: "INVOKE", handler: "products.search", payload: "product-search" } },
            { type: "action", id: "clear", label: "Reset", style: "SECONDARY",
              onClick: { behavior: "INVOKE", handler: "products.search" } },
        ],
    };
}

// The product table. Rebuilt (and REPLACEd) on every search / mutation.
// The SKU cell and the "Details" row-action both INVOKE the detail handler,
// which opens a modal dialog — a state we can't bake ahead (the dialog reuses
// the always-current product), so INVOKE (not inline PATCH) is the right tool.
function productTable(list) {
    return {
        type: "table", id: "product-table", title: null,
        columns: [
            { type: "column", id: "sku", label: "SKU", sortable: true, cellTemplate: {
                type: "action", id: "sku-link", label: "{sku}", appearance: "LINK",
                onClick: { behavior: "INVOKE", handler: "products.detail", url: "{id}" },
            } },
            { type: "column", id: "name", label: "Name", sortable: true },
            { type: "column", id: "category", label: "Category" },
            { type: "column", id: "price", label: "Price" },
            { type: "column", id: "stock", label: "Stock" },
        ],
        rowActions: [
            { type: "action", id: "view", label: "Details", style: "SECONDARY",
              onClick: { behavior: "INVOKE", handler: "products.detail", url: "{id}" } },
        ],
        rows: list.map(p => ({
            type: "row", id: p.id,
            data: {
                id: p.id, sku: p.sku, name: p.name, category: p.category,
                price: money(p.price), stock: String(p.stock),
            },
        })),
    };
}

// The detail overlay shown when a product is clicked. A UiDialog node with a
// stable id, APPENDed into the #sui-dialogs host so the list underneath stays
// mounted; closing it is a REMOVE by that id.
const DETAIL_DIALOG_ID = "product-detail-dialog";
function detailDialog(p) {
    return {
        type: "dialog", id: DETAIL_DIALOG_ID,
        title: `${p.name} · ${p.sku}`,
        node: {
            type: "detail", id: "product-detail",
            fields: [
                { type: "field", id: "d-sku",   label: "SKU",         fieldType: "TEXT",   value: p.sku },
                { type: "field", id: "d-name",  label: "Name",        fieldType: "TEXT",   value: p.name },
                { type: "field", id: "d-cat",   label: "Category",    fieldType: "TEXT",   value: p.category },
                { type: "field", id: "d-price", label: "Price",       fieldType: "TEXT",   value: money(p.price) },
                { type: "field", id: "d-stock", label: "Stock",       fieldType: "TEXT",   value: `${p.stock} units` },
                { type: "field", id: "d-desc",  label: "Description", fieldType: "TEXTAREA", value: p.description },
            ],
            actions: [
                { type: "action", id: "d-delete", label: "🗑 Delete", style: "DANGER",
                  confirm: `Really delete “${p.name}”?`,
                  onClick: { behavior: "INVOKE", handler: "products.delete", url: p.id } },
            ],
        },
    };
}

// ── Part 2: catalog list → inline detail via INLINE PATCH (no handler) ──────

// The detail pane on the right. `id` is stable ("catalog-detail") so repeated
// REPLACE patches keep hitting the same target.
function catalogDetail(p) {
    if (!p) {
        return { type: "detail", id: "catalog-detail", title: "Preview",
            fields: [{ type: "field", id: "cd-hint", label: "", fieldType: "TEXT",
                       value: "← Click a product to fill the detail form with no round-trip." }] };
    }
    return {
        type: "detail", id: "catalog-detail", title: `${p.name}`,
        fields: [
            { type: "field", id: "cd-sku",   label: "SKU",         fieldType: "TEXT", value: p.sku },
            { type: "field", id: "cd-cat",   label: "Category",    fieldType: "TEXT", value: p.category },
            { type: "field", id: "cd-price", label: "Price",       fieldType: "TEXT", value: money(p.price) },
            { type: "field", id: "cd-stock", label: "Stock",       fieldType: "TEXT", value: `${p.stock} units` },
            { type: "field", id: "cd-desc",  label: "Description", fieldType: "TEXTAREA", value: p.description },
        ],
    };
}

// A UiList built item-by-item in JS. Because we build each item here, we can
// bake a *unique* inline patch into every item's onClick — no substitution,
// no handler, no fetch. Clicking an item just applies its patch.
function catalogList(list) {
    return {
        type: "list", id: "catalog-list",
        items: list.map(p => ({
            id: `cat-${p.id}`, label: `${p.name} — ${money(p.price)}`, description: p.sku,
            onClick: {
                behavior: "PATCH",
                patch: { patches: [{ op: "REPLACE", targetId: "catalog-detail", node: catalogDetail(p) }] },
            },
        })),
    };
}

function catalogSection() {
    return { type: "stack", id: "catalog-section", gap: 10, children: [
        sectionTitle("catalog-head", "Catalog → detail form (inline patch, no round-trip)",
            "Each list item carries its finished UiPatch in the trigger (behavior: \"PATCH\"). Click = apply the patch, with no handler and no server."),
        { type: "stack", id: "catalog-cols", direction: "HORIZONTAL", gap: 24, children: [
            catalogList(products),
            catalogDetail(null),
        ] },
    ] };
}

// ── Part 3: checkbox onChange enables/disables another field (INVOKE) ───────

function addressField(enabled) {
    return {
        type: "field", id: "delivery-address", label: "Different delivery address",
        fieldType: "TEXT", editable: enabled,
        placeholder: enabled ? "street, ZIP, city" : "",
        value: enabled ? "" : "(disabled — tick the box to enable)",
        hint: enabled ? "Field is now active." : "Enable it via the checkbox.",
    };
}

function deliverySection() {
    return { type: "stack", id: "delivery-section", gap: 10, children: [
        sectionTitle("delivery-head", "Checkbox enables a field (field onChange + INVOKE)",
            "The checkbox carries an onChange trigger. Its handler reads the checkbox state and replaces the address field with an enabled/disabled variant via a patch."),
        { type: "form", id: "delivery-form", fields: [
            { type: "field", id: "diff-address", label: "Ship to a different address?",
              fieldType: "BOOLEAN", editable: true, value: false,
              onChange: { behavior: "INVOKE", handler: "delivery.toggle", payload: "delivery-form" } },
            addressField(false),
        ] },
    ] };
}

// ── Part 4: upload drop zone → client-side image preview (INVOKE + files) ───

function uploadPreview(placeholder) {
    return { type: "stack", id: "upload-preview", gap: 6, children: [
        { type: "text", id: "upload-placeholder",
          text: placeholder ?? "No image selected yet.", cssClass: "demo-section-note" },
    ] };
}

function uploadSection() {
    return { type: "stack", id: "upload-section", gap: 10, children: [
        sectionTitle("upload-head", "File upload (drag & drop, client-side preview)",
            "The UiUpload drop zone hands the dropped File objects to an INVOKE handler via ctx.files — which previews the image with an object URL. Nothing is uploaded; there is no backend."),
        { type: "stack", id: "upload-cols", direction: "HORIZONTAL", gap: 24, children: [
            { type: "upload", id: "product-image", label: "Product image",
              accept: "image/*", buttonLabel: "Choose image…", dropText: "Drop an image here or",
              hint: "PNG or JPG — previewed locally, nothing leaves the browser.",
              onUpload: { behavior: "INVOKE", handler: "image.preview" } },
            uploadPreview(),
        ] },
    ] };
}

// ── Part 5: a richly structured form (tabs · columns · group), one submit ───

// Fields are laid out with a UiSection (tabs) + multi-column stacks + a grouped
// block — all inside UiForm.content. The submit collects EVERY named control in
// the <form>, across tabs and columns, as one payload (inactive tabs are only
// hidden, so their values ride along too).
function structuredForm() {
    const field = (id, label, value) => ({ type: "field", id, label, fieldType: "TEXT", value: value ?? "", editable: true });
    const col = (id, children) => ({ type: "stack", id, direction: "VERTICAL", gap: 0, children });
    return {
        type: "form", id: "structured-form", fields: [],
        content: [
            { type: "section", id: "sform-tabs", sections: [
                { type: "section-entry", id: "tab-contact", title: "Contact", content:
                    { type: "stack", id: "contact-cols", direction: "HORIZONTAL", cssClass: "sui-cols", children: [
                        col("cc1", [ field("firstName", "First name", "Ada"), field("email", "Email", "ada@example.com") ]),
                        col("cc2", [ field("lastName", "Last name", "Lovelace"), field("phone", "Phone", "") ]),
                    ] } },
                { type: "section-entry", id: "tab-address", title: "Address", content:
                    { type: "fieldgroup", id: "addr-group", title: "Shipping address",
                      hint: "Grouped in a <fieldset> — the legend is read out for each field.", content: [
                        field("street", "Street", "221B Baker Street"),
                        { type: "stack", id: "addr-row", direction: "HORIZONTAL", cssClass: "sui-cols", children: [
                            col("ac1", [ field("zip", "ZIP", "NW1 6XE") ]),
                            col("ac2", [ field("city", "City", "London") ]),
                        ] },
                    ] } },
            ] },
        ],
        actions: [
            { type: "action", id: "submit-structured", label: "Submit form", style: "PRIMARY",
              onClick: { behavior: "INVOKE", handler: "form.submit", payload: "structured-form" } },
        ],
    };
}

function structuredSection() {
    return { type: "stack", id: "structured-section", gap: 10, children: [
        sectionTitle("structured-head", "Structured form: tabs · columns · group — one submit",
            "The form body is a UiSection (tabs) with multi-column stacks and a grouped block. Submitting collects every named control in the <form> — across tabs and columns — as a single payload."),
        structuredForm(),
        { type: "stack", id: "form-result", gap: 6, children: [
            { type: "text", id: "form-result-hint", cssClass: "demo-section-note",
              text: "Submit to see the collected payload (all fields, both tabs)." },
        ] },
    ] };
}

// ── Whole page ──────────────────────────────────────────────────────────────

function shopBody(list, query) {
    return {
        type: "stack", id: "shop-stack", gap: 24,
        children: [
            header(),
            { type: "stack", id: "list-section", gap: 12, children: [
                sectionTitle("list-head", "Product list + detail dialog (INVOKE)",
                    "Search and click call registered JS handlers that return a UiPatch (including a dialog)."),
                searchForm(query),
                productTable(list),
            ] },
            catalogSection(),
            deliverySection(),
            uploadSection(),
            structuredSection(),
        ],
    };
}

// ── Client handlers: the browser-local "endpoints" ──────────────────────────

function filter(query) {
    const q = (query ?? "").trim().toLowerCase();
    if (!q) return products;
    return products.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q));
}

const root = document.getElementById("sui-root");
const renderer = createDefaultRenderer().attach(root);
const bus = new SuiEventBus(renderer, root);
// No backend URLs to route, and instant handlers don't need a loading bar.
bus.setHistoryEnabled(false);
bus.setLoadingPolicy("manual");

// A tiny custom node type — shows how the renderer is extended. Renders an
// <img> from an object/data URL so the upload handler can preview locally.
renderer.register("image", (n) =>
    `<img src="${n.src}" alt="${n.alt ?? ""}" class="demo-thumb">`);

// Search / clear → swap the table and the catalog list (REPLACE).
bus.registerClientHandler("products.search", (ctx) => {
    const q = ctx.payload?.q ?? "";
    const hits = filter(q);
    return { patches: [
        { op: "REPLACE", targetId: "product-table", node: productTable(hits) },
        { op: "REPLACE", targetId: "catalog-list",  node: catalogList(hits) },
    ] };
});

// Product click in the table → open the detail dialog on top (no page swap):
// APPEND the UiDialog node into the #sui-dialogs host.
bus.registerClientHandler("products.detail", (ctx) => {
    const p = byId(ctx.trigger.url);
    if (!p) return; // gone (e.g. deleted in another tab) — do nothing
    return { patches: [{ op: "APPEND", targetId: "sui-dialogs", node: detailDialog(p) }] };
});

// Delete → mutate + persist, close the dialog (REMOVE by id), and refresh both lists.
bus.registerClientHandler("products.delete", (ctx) => {
    products = products.filter(p => p.id !== ctx.trigger.url);
    saveProducts(products);
    return {
        patches: [
            { op: "REMOVE",  targetId: DETAIL_DIALOG_ID },
            { op: "REPLACE", targetId: "product-table", node: productTable(products) },
            { op: "REPLACE", targetId: "catalog-list",  node: catalogList(products) },
        ],
        toasts: [{ level: "SUCCESS", message: "Product deleted.", durationMs: 3000 }],
    };
});

// Reset → restore the seed data and re-render the whole shop.
bus.registerClientHandler("products.reset", () => {
    products = SEED.map(p => ({ ...p }));
    saveProducts(products);
    return {
        patches: [
            { op: "REMOVE",  targetId: DETAIL_DIALOG_ID },
            { op: "REPLACE", targetId: "shop-stack", node: shopBody(products, "") },
        ],
        toasts: [{ level: "INFO", message: "Demo data reset.", durationMs: 3000 }],
    };
});

// Checkbox onChange → read the box's state (from the form payload) and swap the
// address field for an enabled/disabled variant. A state-dependent update, so
// INVOKE (not a fixed inline patch) is the right tool.
bus.registerClientHandler("delivery.toggle", (ctx) => {
    const enabled = ctx.payload?.["diff-address"] === true;
    return { patches: [{ op: "REPLACE", targetId: "delivery-address", node: addressField(enabled) }] };
});

// Structured form submit → the whole form (all tabs + columns) arrives as one
// payload in ctx.payload. We echo it back as a read-only detail to prove it.
bus.registerClientHandler("form.submit", (ctx) => {
    const p = ctx.payload || {};
    const detail = { type: "detail", id: "form-result", title: "Collected payload", fields:
        Object.entries(p).map(([k, v]) => ({ type: "field", id: "r-" + k, label: k,
            fieldType: "TEXT", value: (v === null || v === "") ? "—" : String(v) })) };
    return {
        patches: [{ op: "REPLACE", targetId: "form-result", node: detail }],
        toasts: [{ level: "SUCCESS", message: `Submitted — ${Object.keys(p).length} fields collected.`, durationMs: 3000 }],
    };
});

// Upload → the drop zone / picker hands the File objects to us in ctx.files.
// We preview the image via an object URL — entirely in the browser, no upload.
bus.registerClientHandler("image.preview", (ctx) => {
    const file = (ctx.files ?? [])[0];
    if (!file) return;
    const src = URL.createObjectURL(file);
    return { patches: [{ op: "REPLACE", targetId: "upload-preview", node: {
        type: "stack", id: "upload-preview", gap: 6, children: [
            { type: "image", id: "upload-img", src, alt: file.name },
            { type: "text", id: "upload-caption",
              text: `${file.name} — ${(file.size / 1024).toFixed(0)} KB`, cssClass: "demo-section-note" },
        ],
    } }] };
});

// Initial paint — no fetch, just mount the literal tree.
renderer.mount(shopBody(products, ""));
