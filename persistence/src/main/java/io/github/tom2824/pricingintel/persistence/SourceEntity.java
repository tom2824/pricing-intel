package io.github.tom2824.pricingintel.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "source")
public class SourceEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String kind;

    private String homepage;

    protected SourceEntity() {
    }

    public SourceEntity(String code, String label, String kind, String homepage) {
        this.code = code;
        this.label = label;
        this.kind = kind;
        this.homepage = homepage;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getKind() {
        return kind;
    }

    public String getHomepage() {
        return homepage;
    }
}
