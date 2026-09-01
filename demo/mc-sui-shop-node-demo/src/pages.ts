// The UiPage trees this demo serves.
//
// This is the whole point of the demo: the wire format is plain JSON, and the
// types describing it come from the same npm package the browser renders with.
// Nothing here imports a renderer — a UiPage is data, and a Java controller
// returning the identical JSON would be indistinguishable to the client.

import type { UiPage, UiDialog, UiTable, UiForm, UiTableRow } from "@mindconnect-ai/mc-semantic-ui-core/model";
import type { PageMeta } from "./document.js";

export interface Product {
    id: string;
    sku: string;
    name: string;
    price: string;
}

// In-memory "database" — no Postgres, no ORM.
let products: Product[] = [
    { id: "p-1", sku: "CHAIR-01", name: "Office Chair", price: "299,00 €" },
    { id: "p-2", sku: "DESK-14", name: "Standing Desk", price: "649,00 €" },
    { id: "p-3", sku: "LAMP-07", name: "Desk Lamp", price: "59,00 €" },
    { id: "p-4", sku: "MON-27", name: '27" Monitor', price: "389,00 €" },
    { id: "p-5", sku: "KEY-MX", name: "Mechanical Keyboard", price: "129,00 €" },
];

export function findProduct(id: string): Product | undefined {
    return products.find(p => p.id === id);
}

/**
 * What the page says about itself to a search engine. Not part of `UiPage` —
 * the model describes the UI, not how it is indexed — so it is derived here,
 * next to the content, the same way a Spring app supplies its own head.
 *
 * The canonical is deliberately query-free: /products?q=desk is a view of the
 * product list, not a page of its own, and left uncanonicalised a crawler would
 * happily index one per search term anyone ever linked.
 */
export function listMeta(query?: string): PageMeta {
    return {
        title: query ? `Products matching “${query}” — semantic-ui shop` : "Products — semantic-ui shop",
        description: "Product catalogue served as semantic-ui UiPage JSON by a Node.js backend.",
        canonical: "/products",
    };
}

export function detailMeta(product: Product): PageMeta {
    return {
        title: `${product.name} (${product.sku}) — semantic-ui shop`,
        description: `${product.name}, ${product.price}. Article ${product.sku}.`,
        canonical: `/products/${product.id}`,
    };
}

export function deleteProduct(id: string): void {
    products = products.filter(p => p.id !== id);
}

function searchForm(query: string | undefined): UiForm {
    return {
        type: "form", id: "product-search",
        fields: [{
            type: "field", id: "q", label: "Search", fieldType: "TEXT",
            value: query ?? "", editable: true,
        }],
        actions: [{
            type: "action", id: "search", label: "Search", style: "PRIMARY",
            icon: "search",
            onClick: { url: "/products", method: "GET" },
        }],
    };
}

function productTable(rows: UiTableRow[]): UiTable {
    return {
        type: "table", id: "products-table", title: "Products",
        icon: "package",
        columns: [
            {
                type: "column", id: "col-sku", label: "SKU", dataKey: "sku",
                // Placeholders are substituted per row from the row's data.
                cellTemplate: {
                    type: "link", id: "sku-link",
                    href: "/products/{id}", label: "{sku}",
                },
            },
            { type: "column", id: "col-name", label: "Name", dataKey: "name" },
            { type: "column", id: "col-price", label: "Price", dataKey: "price" },
        ],
        rows,
        rowActions: [{
            type: "action", id: "delete", label: "Delete", style: "DANGER",
            icon: "trash-2",
            confirm: "Delete this product?",
            onClick: { url: "/products/{id}", method: "DELETE" },
        }],
    };
}

/** The product list: a search form above a table. */
export function productListPage(query?: string): UiPage {
    const q = (query ?? "").toLowerCase();
    const rows: UiTableRow[] = products
        .filter(p => !q || p.sku.toLowerCase().includes(q) || p.name.toLowerCase().includes(q))
        .map(p => ({ type: "row", id: p.id, data: { ...p } }));

    return {
        // The search term belongs in the URL the bus pushes, so a filtered list
        // survives a reload and can be shared.
        navigate: query ? `/products?q=${encodeURIComponent(query)}` : "/products",
        node: {
            type: "stack", id: "product-page",
            children: [searchForm(query), productTable(rows)],
        },
    };
}

/**
 * The product detail, as a modal over the list.
 *
 * It carries the list as its `node` as well as the dialog, so the page is
 * complete on its own: opening it from the list re-renders identical content
 * (the morph is a no-op), while arriving at /products/{id} directly — a reload,
 * a bookmark, a shared link — still gets the list behind the modal instead of
 * a dialog floating over nothing.
 */
export function productDetailPage(product: Product): UiPage {
    const dialog: UiDialog = {
        type: "dialog",
        id: "product-dialog",
        title: product.name,
        closeHref: "/products",
        node: {
            type: "detail", id: "product-detail",
            fields: [
                { type: "field", id: "d-sku", label: "SKU", fieldType: "TEXT", value: product.sku },
                { type: "field", id: "d-name", label: "Name", fieldType: "TEXT", value: product.name },
                { type: "field", id: "d-price", label: "Price", fieldType: "TEXT", value: product.price },
            ],
        },
    };

    return {
        ...productListPage(),
        navigate: `/products/${product.id}`,
        dialogs: [dialog],
    };
}
