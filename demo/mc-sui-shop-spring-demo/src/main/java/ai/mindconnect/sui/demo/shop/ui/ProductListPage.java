package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.jpa.Product;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiTrigger;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * The product list page. Header bar with a search form and a "New product"
 * action; below, a table with pagination.
 *
 * <p>This is intentionally a single self-contained class — no component
 * decomposition yet. The admin-ui app's Page/Component split makes sense
 * once a page has 3+ patchable areas; this read-only page renders in one go.
 */
public final class ProductListPage {

    private final Page<Product> page;
    private final String query;
    private final int pageNumber;
    private final int pageSize;

    public ProductListPage(Page<Product> page, String query, int pageNumber, int pageSize) {
        this.page = page;
        this.query = query == null ? "" : query;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    /**
     * Body that goes inside the AdminPage's "Products" tab panel. Returns
     * a {@link UiStack} (not a full {@link UiPage}) because it's embedded
     * inside the admin shell; the AdminPage owns the outer UiPage wrapper.
     */
    public UiStack renderBody() {
        var searchForm = buildSearchForm();
        var table      = buildTable();
        // Vertical stack: search form on top, table below.
        return UiStack.of("product-page-stack")
                .child(searchForm)
                .child(table);
    }

    private UiForm buildSearchForm() {
        // GET /admin/products?q=… : a plain HTML search box. The form
        // method is GET so the query lands in the URL and the result is
        // shareable / bookmarkable.
        return UiForm.of("product-search", null)
                .field(UiField.text("q", "Search", query)
                        .asEditable()
                        .placeholder("name or SKU")
                        .hint("Empty matches everything."))
                .action(UiAction.primary("search", "Search")
                        .dispatch("GET", "/admin/products"))
                // Prominent button (not a link) so it visually matches the
                // SPA renderer where "+ New" was the same button shape.
                .action(UiAction.secondary("create", "+ New product")
                        .onClick(UiTrigger.go("/admin/products/new")));
    }

    private UiTable buildTable() {
        // SKU column: renders each cell as a real navigation link to the
        // product detail page. The {sku} / {id} placeholders are filled in
        // per row by the table renderer from row.data + row.id. Without
        // cellTemplate the column would render plain "X-1" text; with it
        // the user gets a clickable cell that works in both SSR and SPA.
        var skuColumn = UiColumn.text("sku", "SKU").asSortable()
                .withCellTemplate(UiLink.of("sku-link", "/admin/products/{id}", "{sku}"));

        var table = UiTable.of("product-table", null)
                // Multi-select: a leading checkbox column lets the user pick
                // any number of rows for a future bulk-action (delete / export).
                // The choices submit under name="product-table__selection".
                .selectMode(UiTable.SelectMode.MULTI)
                .column(skuColumn)
                .column(UiColumn.text("name",  "Name").asSortable())
                .column(UiColumn.text("price", "Price"))
                .rowAction(UiAction.secondary("view", "View")
                        .onClick(UiTrigger.go("/admin/products/{id}")))
                .rowAction(UiAction.secondary("edit", "Edit")
                        .onClick(UiTrigger.go("/admin/products/{id}/edit")))
                .rowAction(UiAction.danger("delete", "Delete")
                        .confirm("Delete this product?")
                        .dispatch("DELETE", "/admin/products/{id}"));

        for (Product p : page.getContent()) {
            table.row(Map.of(
                    "id",    p.getId().toString(),
                    "sku",   p.getSku(),
                    "name",  p.getName(),
                    "price", formatPrice(p.getPriceCents())
            ));
        }

        // Pagination — page trigger embeds the current query so search-result
        // pagination keeps the filter. {page} is substituted client-side by
        // the SPA; in pure SSR mode the buttons aren't per-page-rewritten
        // (documented limitation), but Next/Prev still navigate.
        UiTrigger pageTrigger = UiTrigger.go(
                "/admin/products?q=" + urlEncode(query) + "&page={page}");
        table.paginate(pageNumber, pageSize, page.getTotalElements(), pageTrigger);

        return table;
    }

    private static String formatPrice(long cents) {
        return String.format("%,.2f €", cents / 100.0);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
