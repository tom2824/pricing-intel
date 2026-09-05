package io.github.tom2824.pricingintel.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_family")
public class ProductFamilyEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_schema", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> attributeSchema;

    @Column(name = "quarantine_threshold", nullable = false)
    private BigDecimal quarantineThreshold;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ProductFamilyEntity() {
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getQuarantineThreshold() {
        return quarantineThreshold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AttributeSchema schema() {
        return AttributeSchema.fromJson(attributeSchema);
    }
}
