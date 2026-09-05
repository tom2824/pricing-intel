package io.github.tom2824.pricingintel.domain;

/** État du produit vendu. Seul le neuf est comparable au neuf. */
public enum ItemCondition {
    NEW,
    USED,
    REFURBISHED,
    UNKNOWN
}
