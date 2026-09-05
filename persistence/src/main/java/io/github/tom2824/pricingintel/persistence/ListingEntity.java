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

@Entity
@Table(name = "listing")
public class ListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_code", nullable = false)
    private SourceEntity source;

    @Column(nullable = false)
    private String url;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ListingEntity() {
    }

    public ListingEntity(String code, SourceEntity source, String url, String externalRef) {
        this.code = code;
        this.source = source;
        this.url = url;
        this.externalRef = externalRef;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public SourceEntity getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public boolean isActive() {
        return active;
    }

    public void update(SourceEntity source, String url, String externalRef, boolean active) {
        this.source = source;
        this.url = url;
        this.externalRef = externalRef;
        this.active = active;
    }
}
