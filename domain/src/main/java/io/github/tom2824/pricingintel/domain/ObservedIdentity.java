package io.github.tom2824.pricingintel.domain;

/**
 * Ce que la source dit du produit qu'elle vend. Ces champs alimentent le matching : le GTIN est la clé
 * universelle, marque + référence fabricant la deuxième, le titre le dernier recours.
 * Tous les champs sont optionnels (null si non observé). Les blancs sont normalisés en null.
 */
public record ObservedIdentity(String gtin, String brand, String mpn, String sku, String title) {

    public static final ObservedIdentity EMPTY = new ObservedIdentity(null, null, null, null, null);

    public ObservedIdentity {
        gtin = normalize(gtin);
        brand = normalize(brand);
        mpn = normalize(mpn);
        sku = normalize(sku);
        title = normalize(title);
    }

    public boolean isEmpty() {
        return gtin == null && brand == null && mpn == null && sku == null && title == null;
    }

    public boolean hasGtin() {
        return gtin != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
