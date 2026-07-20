package ai.mindconnect.sui.demo.shop.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Case-insensitive search over name and SKU. An empty/blank query
     * returns the whole page (the {@code OR :q IS NULL} clause acts as a
     * pass-through, kept inside the query so the caller doesn't have to
     * branch on null themselves).
     */
    @Query("""
        SELECT p FROM Product p
        WHERE :q IS NULL OR :q = ''
           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.name ASC
        """)
    Page<Product> search(@Param("q") String query, Pageable pageable);
}
