package ai.mindconnect.sui.demo.shop.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer record. Mirrors {@link Product} in shape (UUID-id, business
 * identifier, name, two free-form text fields, timestamps) so the demo's
 * two list pages look architecturally identical.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    private UUID id;

    @Column(name = "customer_number", nullable = false, unique = true, length = 32)
    private String customerNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(name = "created_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
