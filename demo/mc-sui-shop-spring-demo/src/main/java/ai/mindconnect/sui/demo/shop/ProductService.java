package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.sui.demo.shop.jpa.Product;
import ai.mindconnect.sui.demo.shop.jpa.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Page<Product> list(String query, int page, int size) {
        return repository.search(query, PageRequest.of(page - 1, size));
    }

    public Optional<Product> findById(UUID id) {
        return repository.findById(id);
    }

    public Product create(String sku, String name, String description, long priceCents) {
        var p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setDescription(description);
        p.setPriceCents(priceCents);
        return repository.save(p);
    }

    public Optional<Product> update(UUID id, String sku, String name, String description, long priceCents) {
        return repository.findById(id).map(p -> {
            p.setSku(sku);
            p.setName(name);
            p.setDescription(description);
            p.setPriceCents(priceCents);
            return repository.save(p);
        });
    }

    public boolean delete(UUID id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
