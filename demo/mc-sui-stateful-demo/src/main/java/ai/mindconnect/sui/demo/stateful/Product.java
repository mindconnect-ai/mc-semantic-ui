package ai.mindconnect.sui.demo.stateful;

import java.util.UUID;

/** A product; the demo's whole domain model. */
public record Product(UUID id, String sku, String name, String category, long priceCents, boolean active) {

    public Product with(String sku, String name, String category, long priceCents, boolean active) {
        return new Product(id, sku, name, category, priceCents, active);
    }

    public String price() {
        return String.format("%d.%02d", priceCents / 100, priceCents % 100);
    }
}
