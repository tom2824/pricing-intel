package io.github.tom2824.pricingintel.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_code", nullable = false)
    private ProductFamilyEntity family;

    @Column(nullable = false)
    private String brand;

    private String mpn;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "natural_key", nullable = false)
    private String naturalKey;

    @Column(name = "equivalence_key")
    private String equivalenceKey;

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductIdentifierEntity> identifiers = new ArrayList<>();

    protected ProductEntity() {
    }

    public ProductEntity(ProductFamilyEntity family, String brand, String mpn, String name, Map<String, Object> attributes,
                         String naturalKey, String equivalenceKey, String currency) {
        this.family = family;
        this.brand = brand;
        this.mpn = mpn;
        this.name = name;
        this.attributes = attributes;
        this.naturalKey = naturalKey;
        this.equivalenceKey = equivalenceKey;
        this.currency = currency;
        this.status = "active";
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ProductFamilyEntity getFamily() {
        return family;
    }

    public String getBrand() {
        return brand;
    }

    public String getMpn() {
        return mpn;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getEquivalenceKey() {
        return equivalenceKey;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public List<ProductIdentifierEntity> getIdentifiers() {
        return identifiers;
    }

    public void update(String name, Map<String, Object> attributes, String equivalenceKey,
                       BigDecimal purchasePrice, BigDecimal currentPrice, String currency) {
        this.name = name;
        this.attributes = attributes;
        this.equivalenceKey = equivalenceKey;
        this.purchasePrice = purchasePrice;
        this.currentPrice = currentPrice;
        this.currency = currency;
    }

    public void setPrices(BigDecimal purchasePrice, BigDecimal currentPrice) {
        this.purchasePrice = purchasePrice;
        this.currentPrice = currentPrice;
    }

    public ProductIdentifierEntity addIdentifier(String scheme, String value, String origin, boolean confirmed) {
        ProductIdentifierEntity identifier = new ProductIdentifierEntity(this, scheme, value, origin, confirmed);
        identifiers.add(identifier);
        return identifier;
    }
}
