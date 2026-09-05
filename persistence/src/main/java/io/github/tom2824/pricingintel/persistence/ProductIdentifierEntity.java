package io.github.tom2824.pricingintel.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un identifiant externe d'un produit : plusieurs GTIN, une référence fabricant, un id d'API... (ADR 0015). */
@Entity
@Table(name = "product_identifier")
public class ProductIdentifierEntity {

    public static final String SCHEME_GTIN = "gtin";
    public static final String SCHEME_MPN = "mpn";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private String scheme;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductIdentifierEntity() {
    }

    ProductIdentifierEntity(ProductEntity product, String scheme, String value, String origin, boolean confirmed) {
        this.product = product;
        this.scheme = scheme;
        this.value = value;
        this.origin = origin;
        this.confirmed = confirmed;
    }

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getId() {
        return id;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public String getScheme() {
        return scheme;
    }

    public String getValue() {
        return value;
    }

    public String getOrigin() {
        return origin;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void confirm() {
        this.confirmed = true;
    }
}
