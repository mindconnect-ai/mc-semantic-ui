package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.jpa.Product;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiTrigger;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Read-only product detail. Header actions edit / delete. */
public final class ProductDetailPage {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Product product;

    public ProductDetailPage(Product product) {
        this.product = product;
    }

    public UiPage render() {
        return UiPage.of(pageUrl(), detail());
    }

    /**
     * Returns just the detail node (no UiPage wrapper). Used by the
     * controller when composing the detail as a {@link ai.mindconnect.ui.model.UiDialog}
     * layered on top of the product list — the read-only view IS a dialog
     * over the list, the SSR view shows it as such.
     */
    public UiDetail detail() {
        return UiDetail.of("product-detail-" + product.getId(), null)
                .field(UiField.text("sku",         "SKU",         product.getSku()))
                .field(UiField.text("name",        "Name",        product.getName()))
                .field(UiField.text("description", "Description", product.getDescription()))
                .field(UiField.text("price",       "Price",
                        String.format("%,.2f €", product.getPriceCents() / 100.0)))
                .field(UiField.text("createdAt",   "Created",
                        DT_FMT.format(product.getCreatedAt())))
                .field(UiField.text("updatedAt",   "Updated",
                        DT_FMT.format(product.getUpdatedAt())))
                .action(UiAction.primary("edit", "Edit")
                        .onClick(UiTrigger.go("/admin/products/" + product.getId() + "/edit")))
                .action(UiAction.danger("delete", "Delete")
                        .confirm("Delete '" + product.getName() + "'?")
                        .dispatch("DELETE", "/admin/products/" + product.getId()))
                .link(UiLink.of("back", "/admin/products", "← Back to products"));
    }

    public String title()   { return product.getName(); }
    public String pageUrl() { return "/admin/products/" + product.getId(); }
}
