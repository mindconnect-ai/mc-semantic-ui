package ai.mindconnect.sui.demo.shop.controller;

import ai.mindconnect.sui.demo.shop.DemoUser;
import ai.mindconnect.sui.demo.shop.ProductService;
import ai.mindconnect.sui.demo.shop.SuiMode;
import ai.mindconnect.sui.demo.shop.SuiThemeRef;
import ai.mindconnect.sui.demo.shop.ToastQueue;
import ai.mindconnect.ui.model.UiToast;
import ai.mindconnect.sui.demo.shop.ui.AdminPage;
import ai.mindconnect.sui.demo.shop.ui.ProductDetailPage;
import ai.mindconnect.sui.demo.shop.ui.ProductFormPage;
import ai.mindconnect.sui.demo.shop.ui.ProductListPage;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiPage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * One controller serves the whole product CRUD. GETs return {@link UiPage}
 * objects; the SUI message converter serialises them to HTML for
 * {@code Accept: text/html} requests and to JSON for everything else.
 *
 * <p>Mutating endpoints (POST/PUT/DELETE) follow the Post/Redirect/Get
 * pattern: on success they return {@code 302} with a {@code Location}
 * pointing at the relevant page, so the browser issues a fresh GET. This
 * keeps the URL accurate and avoids the "resubmit form?" dialog on reload.
 *
 * <p>Form bodies arrive in two flavours:
 * <ul>
 *   <li>SSR / native HTML form → {@code application/x-www-form-urlencoded},
 *       fields bound with {@code @RequestParam}.</li>
 *   <li>SPA EventBus (fetch) → {@code application/json}, fields bound from
 *       a {@link ProductForm} record via {@code @RequestBody}.</li>
 * </ul>
 * Both flavours delegate into the same private helpers, so behaviour stays
 * identical regardless of how the client encoded its payload. We register
 * each verb twice (once per {@code consumes}) — Spring picks by the
 * inbound {@code Content-Type}.
 */
@RestController
@RequestMapping("/admin/products")
public class ProductController {

    private final ProductService service;
    private final DemoUser user;
    private final SuiMode mode;
    private final SuiThemeRef theme;
    private final ToastQueue toasts;

    public ProductController(ProductService service, DemoUser user, SuiMode mode,
                              SuiThemeRef theme, ToastQueue toasts) {
        this.service = service;
        this.user = user;
        this.mode = mode;
        this.theme = theme;
        this.toasts = toasts;
    }

    // ── Read-only views ─────────────────────────────────────────────────────

    @GetMapping
    public UiPage list(
            @RequestParam(value = "q",    required = false) String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var products = service.list(query, page, size);
        var body = new ProductListPage(products, query, page, size).renderBody();
        // Wrap in the shared admin shell so the header + Products/Customers
        // tab bar show above the list.
        var rendered = new AdminPage(user, AdminPage.Tab.PRODUCTS, body, mode.isSpa(), theme.current()).render();
        toasts.drainOnto(rendered);
        return rendered;
    }

    @GetMapping("/new")
    public UiPage newForm() {
        return new ProductFormPage(null).render();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UiPage> detail(@PathVariable UUID id) {
        return service.findById(id)
                .map(this::detailPageWithDialog)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/edit")
    public ResponseEntity<UiPage> editForm(@PathVariable UUID id) {
        return service.findById(id)
                .map(this::editPageWithDialog)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Builds the product list with the read-only detail view layered on top
     * as a dialog. Closing the dialog (× / backdrop / SSR navigation) lands
     * back on the list — never on the product detail's own URL, so the
     * close-to-list flow is symmetric for both view and edit.
     */
    private UiPage detailPageWithDialog(ai.mindconnect.sui.demo.shop.jpa.Product p) {
        var background = backgroundList();
        var view = new ProductDetailPage(p);
        var rendered = UiPage.of(view.pageUrl(), background.getNode())
                .dialog(UiDialog.of(view.title(), "/admin/products", view.detail()));
        toasts.drainOnto(rendered);
        return rendered;
    }

    /**
     * Builds the edit view as a dialog overlayed on top of the product list
     * page. Close (× / backdrop / Cancel) always navigates back to the list
     * itself — not to the read-only view — so the user finishes editing
     * where they started browsing.
     */
    private UiPage editPageWithDialog(ai.mindconnect.sui.demo.shop.jpa.Product p) {
        var background = backgroundList();
        var form = new ProductFormPage(p);
        var rendered = UiPage.of(form.pageUrl(), background.getNode())
                .dialog(UiDialog.of(form.title(), "/admin/products", form.form()));
        toasts.drainOnto(rendered);
        return rendered;
    }

    /** The shared backdrop for both view and edit dialogs — the product list. */
    private UiPage backgroundList() {
        var list = service.list(null, 1, 20);
        var body = new ProductListPage(list, null, 1, 20).renderBody();
        return new AdminPage(user, AdminPage.Tab.PRODUCTS, body, mode.isSpa(), theme.current()).render();
    }

    // ── Mutating endpoints (PRG) ────────────────────────────────────────────

    /** Form-encoded payload from a native HTML {@code <form>} submission. */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> createForm(
            @RequestParam String sku,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String price) {
        return doCreate(sku, name, description, price);
    }

    /** JSON payload from the SPA EventBus's {@code fetch} call. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createJson(@RequestBody ProductForm form) {
        return doCreate(form.sku(), form.name(), form.description(), form.priceString());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> updateForm(
            @PathVariable UUID id,
            @RequestParam String sku,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String price) {
        return doUpdate(id, sku, name, description, price);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateJson(@PathVariable UUID id, @RequestBody ProductForm form) {
        return doUpdate(id, form.sku(), form.name(), form.description(), form.priceString());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        boolean ok = service.delete(id);
        if (!ok) return ResponseEntity.notFound().build();
        toasts.add(UiToast.success("Product deleted"));
        return seeOther("/admin/products");
    }

    private ResponseEntity<Void> doCreate(String sku, String name, String description, String price) {
        var product = service.create(sku, name, description, parsePriceEuro(price));
        toasts.add(UiToast.success("Product \"" + product.getName() + "\" created"));
        return seeOther("/admin/products/" + product.getId());
    }

    private ResponseEntity<Void> doUpdate(UUID id, String sku, String name, String description, String price) {
        return service.update(id, sku, name, description, parsePriceEuro(price))
                .map(p -> {
                    toasts.add(UiToast.success("Product \"" + p.getName() + "\" saved"));
                    return seeOther("/admin/products/" + p.getId());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Mirror of the form fields for the JSON-bodied SPA path. {@code price}
     * is typed as {@link Object} because the SPA EventBus harvests it from
     * an {@code <input type="number">} and sends it as a JSON number, while
     * a hand-crafted client might send it as a string. {@link #priceString()}
     * normalises both into the comma/dot-tolerant string form expected by
     * {@link #parsePriceEuro(String)}, the single source of parsing truth.
     */
    public record ProductForm(String sku, String name, String description, Object price) {
        public String priceString() {
            return price == null ? null : price.toString();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Parses a user-typed "49.99" / "49,99" euro string into cents. Falls
     * back to 0 on garbage input — production code would surface a
     * validation error, but the demo aims for shortest-path.
     */
    private static long parsePriceEuro(String s) {
        if (s == null) return 0;
        String normalised = s.trim().replace(',', '.');
        if (normalised.isEmpty()) return 0;
        try {
            double euros = Double.parseDouble(normalised);
            return Math.round(euros * 100.0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ResponseEntity<Void> seeOther(String location) {
        var headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }
}
