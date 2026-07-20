package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.sui.demo.shop.jpa.Customer;
import ai.mindconnect.sui.demo.shop.jpa.Product;
import ai.mindconnect.sui.demo.shop.ui.AdminPage;
import ai.mindconnect.sui.demo.shop.ui.CustomerListPage;
import ai.mindconnect.sui.demo.shop.ui.ProductDetailPage;
import ai.mindconnect.sui.demo.shop.ui.ProductFormPage;
import ai.mindconnect.sui.demo.shop.ui.ProductListPage;
import ai.mindconnect.sui.demo.shop.ui.ProfilePage;
import ai.mindconnect.ui.ssr.SuiServerRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the demo's page builders against the bundled SUI
 * Handlebars renderer. No Spring context, no DB — just the page → UiPage
 * → HTML pipeline. Catches template / model wiring issues without
 * needing Postgres up.
 */
class PageRenderSmokeTest {

    private final SuiServerRenderer renderer = new SuiServerRenderer();
    private final DemoUser demoUser = new DemoUser();

    @Test
    void renderEmptyProductListInAdminShell() {
        Page<Product> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        var body = new ProductListPage(empty, "", 1, 20).renderBody();
        var page = new AdminPage(demoUser, AdminPage.Tab.PRODUCTS, body).render();
        String html = renderer.renderPage(page);
        // Admin shell shows the tab bar with both tabs.
        assertTrue(html.contains("Shop Admin"), html);
        assertTrue(html.contains(">Products</a>") || html.contains(">Products</button>"), html);
        assertTrue(html.contains(">Customers</a>"), html);
        // The empty product table renders with the "No rows." placeholder.
        assertTrue(html.contains("sui-table"), html);
        assertTrue(html.contains("No rows."), html);
    }

    @Test
    void productListWithRowsRendersInsideAdminTab() {
        Product a = sampleProduct("DESK-001", "Standing desk", 49900);
        Product b = sampleProduct("CHAIR-002", "Office chair", 29900);
        Page<Product> p = new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2);

        var body = new ProductListPage(p, "", 1, 20).renderBody();
        var page = new AdminPage(demoUser, AdminPage.Tab.PRODUCTS, body).render();
        String html = renderer.renderPage(page);
        assertTrue(html.contains("DESK-001"), html);
        assertTrue(html.contains("Standing desk"), html);
        assertTrue(html.contains("499,00 €") || html.contains("499.00 €"), html);
    }

    @Test
    void renderNewForm() {
        var page = new ProductFormPage(null).render();
        String html = renderer.renderPage(page);
        assertTrue(html.contains("New product"), html);
        assertTrue(html.contains("name=\"sku\""), html);
        assertTrue(html.contains("name=\"name\""), html);
        assertTrue(html.contains("name=\"price\""), html);
        // form id reflects the "new" mode.
        assertTrue(html.contains("id=\"product-new\""), html);
    }

    @Test
    void renderDetailPage() {
        Product p = sampleProduct("DESK-001", "Standing desk", 49900);
        p.setDescription("Electric height-adjustable");
        var page = new ProductDetailPage(stampedWithNow(p)).render();
        String html = renderer.renderPage(page);
        assertTrue(html.contains("Standing desk"), html);
        assertTrue(html.contains("Electric height-adjustable"), html);
        assertTrue(html.contains("Edit"), html);
        assertTrue(html.contains("Delete"), html);
    }

    @Test
    void customerListInsideCustomersTab() {
        Customer c = sampleCustomer("C-1001", "Alice Müller", "alice@example.com", "Berlin");
        Page<Customer> p = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
        var body = new CustomerListPage(p, "", 1, 20).renderBody();
        var page = new AdminPage(demoUser, AdminPage.Tab.CUSTOMERS, body).render();
        String html = renderer.renderPage(page);
        // Customers tab is active (real link, also marked active).
        assertTrue(html.contains("class=\"sui-tab active\" href=\"/admin/customers\"")
                   || html.contains("class=\"sui-tab active\" href=\"/admin/customers\" data-href=\"/admin/customers\""),
                "active customers tab missing in:\n" + html);
        assertTrue(html.contains("C-1001"), html);
        assertTrue(html.contains("Alice Müller"), html);
        assertTrue(html.contains("alice@example.com"), html);
    }

    @Test
    void productsTabIsActiveWhenProductsRendered() {
        Page<Product> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        var body = new ProductListPage(empty, "", 1, 20).renderBody();
        var page = new AdminPage(demoUser, AdminPage.Tab.PRODUCTS, body).render();
        String html = renderer.renderPage(page);
        // SSR-friendly tab: <a href> for the inactive tab, the active one
        // gets the .active class.
        assertTrue(html.contains("class=\"sui-tab active\" href=\"/admin/products\""), html);
        // Customers tab must NOT be marked active.
        assertFalse(html.contains("class=\"sui-tab active\" href=\"/admin/customers\""), html);
    }

    @Test
    void headerShowsBrandAndUserOnEveryPage() {
        Page<Product> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        var body = new ProductListPage(empty, "", 1, 20).renderBody();
        var page = new AdminPage(demoUser, AdminPage.Tab.PRODUCTS, body).render();
        String html = renderer.renderPage(page);
        // The UiHeader template emits a <header class="sui-header"> with the
        // brand on one side and the user widget (avatar circle + name) on
        // the other.
        assertTrue(html.contains("class=\"sui-header"), html);
        assertTrue(html.contains("Shop Admin"), html);
        // Avatar circle carries the initials.
        assertTrue(html.contains("class=\"sui-header-avatar\">DA</span>"), html);
        assertTrue(html.contains("Demo Admin"), html);
        // Profile link target.
        assertTrue(html.contains("href=\"/admin/profile\""), html);
    }

    @Test
    void profilePageRendersWithoutActiveTab() {
        var body = new ProfilePage(demoUser).renderBody();
        var page = new AdminPage(demoUser, null, body).render();
        String html = renderer.renderPage(page);
        // Header is still there.
        assertTrue(html.contains("Shop Admin"), html);
        assertTrue(html.contains("Demo Admin"), html);
        // Profile fields show.
        assertTrue(html.contains("Your profile"), html);
        assertTrue(html.contains("demo@example.com"), html);
        assertTrue(html.contains("Administrator"), html);
        // Neither tab is highlighted (no .sui-tab.active).
        assertFalse(html.contains("class=\"sui-tab active\""),
                "no tab should be active on the profile page; got:\n" + html);
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private static Product sampleProduct(String sku, String name, long cents) {
        var p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setPriceCents(cents);
        // The @PrePersist hook normally assigns the id; we're test-bypassing
        // persistence, so set one explicitly so toString() doesn't NPE in
        // the list-row builder.
        setField(Product.class, p, "id", UUID.randomUUID());
        return p;
    }

    private static Customer sampleCustomer(String number, String name, String email, String city) {
        var c = new Customer();
        c.setCustomerNumber(number);
        c.setName(name);
        c.setEmail(email);
        c.setCity(city);
        setField(Customer.class, c, "id", UUID.randomUUID());
        setField(Customer.class, c, "createdAt", java.time.Instant.now());
        setField(Customer.class, c, "updatedAt", java.time.Instant.now());
        return c;
    }

    /** Sets created/updated fields to now so the detail formatter doesn't NPE. */
    private static Product stampedWithNow(Product p) {
        setField(Product.class, p, "createdAt", java.time.Instant.now());
        setField(Product.class, p, "updatedAt", java.time.Instant.now());
        return p;
    }

    private static void setField(Class<?> cls, Object target, String fieldName, Object value) {
        try {
            var f = cls.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
