package ai.mindconnect.sui.demo.shop.jpa;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the products and customers tables the first time the app boots on
 * an empty schema. Each entity is seeded independently — adding customers
 * to a pre-existing product DB is fine. Idempotent per table: if rows
 * already exist for one of them, that table's seed step is skipped so
 * subsequent restarts don't add duplicates. Real apps would use Flyway
 * migrations instead — but the goal here is "clone, compose up, run, see
 * something immediately".
 */
@Component
public class SampleDataLoader implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public SampleDataLoader(ProductRepository productRepository,
                            CustomerRepository customerRepository) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public void run(String... args) {
        seedProducts();
        seedCustomers();
    }

    private void seedProducts() {
        if (productRepository.count() > 0) return;
        List<Product> seed = List.of(
                product("DESK-001",  "Standing desk",       "Electric height-adjustable",      49900L),
                product("CHAIR-002", "Office chair",        "Ergonomic mesh back",              29900L),
                product("LAMP-003",  "Desk lamp",           "Warm-white LED, dimmable",          4990L),
                product("MON-004",   "27\" monitor",        "4K IPS, 60 Hz",                    34900L),
                product("KEY-005",   "Mechanical keyboard", "Tactile switches, USB-C",          14900L),
                product("MOUSE-006", "Wireless mouse",      "Bluetooth + 2.4 GHz dongle",        7900L),
                product("CABLE-007", "USB-C cable 2m",      "100W PD, e-marker",                 1900L),
                product("DOCK-008",  "USB-C dock",          "HDMI + DP + Ethernet + 4× USB",    19900L)
        );
        productRepository.saveAll(seed);
    }

    private void seedCustomers() {
        if (customerRepository.count() > 0) return;
        List<Customer> seed = List.of(
                customer("C-1001", "Alice Müller",   "alice@example.com",   "Berlin"),
                customer("C-1002", "Bob Schmidt",    "bob@example.com",     "Hamburg"),
                customer("C-1003", "Carla Fischer",  "carla@example.com",   "München"),
                customer("C-1004", "Daniel Weber",   "daniel@example.com",  "Köln"),
                customer("C-1005", "Emma Becker",    "emma@example.com",    "Frankfurt"),
                customer("C-1006", "Felix Hoffmann", "felix@example.com",   "Stuttgart"),
                customer("C-1007", "Greta Wagner",   "greta@example.com",   "Düsseldorf")
        );
        customerRepository.saveAll(seed);
    }

    private static Product product(String sku, String name, String description, long cents) {
        var p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setDescription(description);
        p.setPriceCents(cents);
        return p;
    }

    private static Customer customer(String number, String name, String email, String city) {
        var c = new Customer();
        c.setCustomerNumber(number);
        c.setName(name);
        c.setEmail(email);
        c.setCity(city);
        return c;
    }
}
