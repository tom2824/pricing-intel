package io.github.tom2824.pricingintel.scraper;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import java.util.Locale;

/** Correspondances entre le vocabulaire schema.org ({@code https://schema.org/InStock}...) et le domaine. */
final class SchemaOrg {

    private SchemaOrg() {
    }

    static Availability availability(String value) {
        if (value == null) {
            return Availability.UNKNOWN;
        }
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("instock") || v.contains("limitedavailability") || v.contains("onlineonly") || v.contains("instoreonly")) {
            return Availability.IN_STOCK;
        }
        if (v.contains("outofstock") || v.contains("soldout") || v.contains("discontinued")) {
            return Availability.OUT_OF_STOCK;
        }
        if (v.contains("preorder") || v.contains("presale")) {
            return Availability.PREORDER;
        }
        if (v.contains("backorder")) {
            return Availability.BACKORDER;
        }
        return Availability.UNKNOWN;
    }

    static ItemCondition condition(String value) {
        if (value == null) {
            return ItemCondition.UNKNOWN;
        }
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("newcondition")) {
            return ItemCondition.NEW;
        }
        if (v.contains("refurbishedcondition")) {
            return ItemCondition.REFURBISHED;
        }
        if (v.contains("usedcondition") || v.contains("damagedcondition")) {
            return ItemCondition.USED;
        }
        return ItemCondition.UNKNOWN;
    }
}
