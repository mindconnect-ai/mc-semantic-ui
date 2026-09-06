package ai.mindconnect.sui.demo.stateful;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory products, seeded at start. A real app would put a repository here. */
@Service
public class ProductRepository {

    public static final List<String> CATEGORIES = List.of("Shoes", "Shirts", "Hats", "Bags");

    private final Map<UUID, Product> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        String[] names = {"Runner", "Trail", "Court", "Classic", "Oxford", "Polo", "Tee", "Beanie",
                "Cap", "Fedora", "Tote", "Backpack", "Duffel", "Loafer", "Boot", "Sandal", "Hoodie",
                "Cardigan", "Visor", "Satchel", "Sneaker", "Slipper", "Blazer", "Vest", "Bucket"};
        for (int i = 0; i < names.length; i++) {
            String category = CATEGORIES.get(i % CATEGORIES.size());
            save(new Product(UUID.randomUUID(), String.format("SKU-%03d", i + 1), names[i],
                    category, 1990L + i * 350L, i % 7 != 3));
        }
    }

    public List<Product> find(String query, int page, int size) {
        return matching(query).stream().skip((long) (page - 1) * size).limit(size).toList();
    }

    public long count(String query) {
        return matching(query).size();
    }

    public Optional<Product> byId(UUID id) {
        return Optional.ofNullable(products.get(id));
    }

    public Product save(Product product) {
        products.put(product.id(), product);
        return product;
    }

    public boolean delete(UUID id) {
        return products.remove(id) != null;
    }

    private List<Product> matching(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Product> out = new ArrayList<>();
        for (Product p : products.values()) {
            if (q.isEmpty() || p.name().toLowerCase().contains(q) || p.sku().toLowerCase().contains(q)
                    || p.category().toLowerCase().contains(q)) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparing(Product::sku));
        return out;
    }
}
