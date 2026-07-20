package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.jpa.Product;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiPage;

/**
 * Edit form for a product. Used in two modes:
 * <ul>
 *   <li>{@code new}: {@code product} is {@code null}, submit POSTs to
 *       {@code /admin/products}.</li>
 *   <li>{@code edit}: {@code product} is loaded, submit PUTs to
 *       {@code /admin/products/{id}}. The hidden {@code _method=PUT}
 *       gets injected by Spring's {@link
 *       org.springframework.web.filter.HiddenHttpMethodFilter}, which
 *       SUI's SSR auto-config registers automatically.</li>
 * </ul>
 *
 * <p>Price is shown to the user in euros (two decimals) but stored in
 * cents. The conversion lives in the controller, not here — this class is
 * purely the view shape.
 */
public final class ProductFormPage {

    private final Product product;   // null = "new"

    public ProductFormPage(Product product) {
        this.product = product;
    }

    public UiPage render() {
        return UiPage.of(pageUrl(), form());
    }

    /**
     * Returns just the form node (no UiPage wrapper). Used by the controller
     * when composing the form as a {@link ai.mindconnect.ui.model.UiDialog} layered on top of a
     * background page.
     */
    public UiForm form() {
        boolean isNew = product == null;
        String formId = isNew ? "product-new" : "product-" + product.getId();
        String action = isNew
                ? "/admin/products"
                : "/admin/products/" + product.getId();
        String method = isNew ? "POST" : "PUT";

        return UiForm.of(formId, title())
                .field(UiField.text("sku", "SKU", isNew ? null : product.getSku())
                        .asEditable().asRequired()
                        .hint("Unique stock-keeping unit."))
                .field(UiField.text("name", "Name", isNew ? null : product.getName())
                        .asEditable().asRequired())
                .field(UiField.textarea("description", "Description",
                        isNew ? null : product.getDescription())
                        .asEditable())
                .field(UiField.text("price", "Price (€)",
                        isNew ? null : formatPriceEuro(product.getPriceCents()))
                        .asEditable().asRequired()
                        .hint("Format: 49.99"))
                .action(UiAction.primary("save", "Save")
                        .dispatch(method, action, formId))
                .action(UiAction.secondary("cancel", "Cancel")
                        .onClick(ai.mindconnect.ui.model.UiTrigger.go("/admin/products")))
                .link(UiLink.of("back", "/admin/products", "← Back to products"));
    }

    /** Human-readable dialog title — "New product" or "Edit: …". */
    public String title() {
        return product == null ? "New product" : "Edit: " + product.getName();
    }

    /** URL this page lives at — also the {@code navigate} value. */
    public String pageUrl() {
        return product == null
                ? "/admin/products/new"
                : "/admin/products/" + product.getId() + "/edit";
    }

    private static String formatPriceEuro(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
