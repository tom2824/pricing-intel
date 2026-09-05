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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Correspondance annonce ↔ produit, datée, avec statut, méthode, score et preuve (ADR 0016). */
@Entity
@Table(name = "listing_match")
public class ListingMatchEntity {

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_VALIDATED = "validated";
    public static final String STATUS_REJECTED = "rejected";
    public static final String METHOD_MANUAL = "manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ListingEntity listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @Column(nullable = false)
    private String author;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ListingMatchEntity() {
    }

    public ListingMatchEntity(ListingEntity listing, ProductEntity product, String status, String method,
                              BigDecimal score, Map<String, Object> evidence, String author, Instant validFrom) {
        this.listing = listing;
        this.product = product;
        this.status = status;
        this.method = method;
        this.score = score;
        this.evidence = evidence;
        this.author = author;
        this.validFrom = validFrom;
    }

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getId() {
        return id;
    }

    public ListingEntity getListing() {
        return listing;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public String getStatus() {
        return status;
    }

    public String getMethod() {
        return method;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public String getReason() {
        return reason;
    }

    /** Clôt la correspondance sans la supprimer : l'historique des relevés reste attaché à ce produit jusqu'à cette date. */
    public void close(Instant at, String reason) {
        this.validTo = at;
        this.reason = reason;
    }
}
