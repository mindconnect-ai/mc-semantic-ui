package ai.mindconnect.sui.demo.shop.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Case-insensitive search over name, email and customer number.
     * Empty / blank query returns the whole page (the {@code OR :q IS NULL}
     * clause acts as a pass-through, kept inside the query so the caller
     * doesn't have to branch on null themselves).
     */
    @Query("""
        SELECT c FROM Customer c
        WHERE :q IS NULL OR :q = ''
           OR LOWER(c.name)           LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.email)          LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.customerNumber) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY c.name ASC
        """)
    Page<Customer> search(@Param("q") String query, Pageable pageable);
}
