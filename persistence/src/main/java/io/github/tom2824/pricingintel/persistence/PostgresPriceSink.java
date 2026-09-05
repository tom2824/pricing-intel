package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écrit les relevés dans {@code price_snapshot} : une ligne par annonce et par jour (upsert : rejouer une
 * collecte remplace le relevé du jour), et décide de la quarantaine (ADR 0017) en comparant au dernier relevé
 * de confiance de l'annonce, avec le seuil de la famille du produit en vigueur.
 */
public class PostgresPriceSink implements PriceSink {

    static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.500");

    static final String QUARANTINE_NONE = "none";
    static final String QUARANTINE_SUSPECT = "suspect";
    static final String QUARANTINE_CONFIRMED = "confirmed";
    static final String QUARANTINE_REJECTED = "rejected";

    private static final String UPSERT = """
            insert into price_snapshot (listing_id, observed_at, observed_date, observed_url, price, list_price,
                shipping_cost, currency, availability, item_condition, seller_type, observed_gtin, observed_brand,
                observed_mpn, observed_sku, observed_title, extraction_method, extraction_confidence, quarantine)
            values (:listing_id, :observed_at, :observed_date, :observed_url, :price, :list_price,
                :shipping_cost, :currency, :availability, :item_condition, :seller_type, :observed_gtin, :observed_brand,
                :observed_mpn, :observed_sku, :observed_title, :extraction_method, :extraction_confidence, :quarantine)
            on conflict (listing_id, observed_date) do update set
                observed_at = excluded.observed_at, observed_url = excluded.observed_url, price = excluded.price,
                list_price = excluded.list_price, shipping_cost = excluded.shipping_cost, currency = excluded.currency,
                availability = excluded.availability, item_condition = excluded.item_condition,
                seller_type = excluded.seller_type, observed_gtin = excluded.observed_gtin,
                observed_brand = excluded.observed_brand, observed_mpn = excluded.observed_mpn,
                observed_sku = excluded.observed_sku, observed_title = excluded.observed_title,
                extraction_method = excluded.extraction_method, extraction_confidence = excluded.extraction_confidence,
                quarantine = excluded.quarantine
            """;

    private final JdbcClient jdbc;

    public PostgresPriceSink(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void accept(PriceSnapshot snapshot) {
        String code = snapshot.listingId().value();
        long listingId = jdbc.sql("select id from listing where code = :code")
                .param("code", code)
                .query(Long.class).optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown listing '" + code + "': import it into the catalogue before collecting"));
        LocalDate day = snapshot.observedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        String quarantine = decideQuarantine(listingId, day, snapshot.price().amount());

        jdbc.sql(UPSERT)
                .param("listing_id", listingId)
                .param("observed_at", snapshot.observedAt().atOffset(ZoneOffset.UTC))
                .param("observed_date", day)
                .param("observed_url", snapshot.observedUrl().toString())
                .param("price", snapshot.price().amount())
                .param("list_price", amountOrNull(snapshot.listPrice()), Types.NUMERIC)
                .param("shipping_cost", amountOrNull(snapshot.shippingCost()), Types.NUMERIC)
                .param("currency", snapshot.price().currency().getCurrencyCode())
                .param("availability", snapshot.availability().name())
                .param("item_condition", snapshot.condition().name())
                .param("seller_type", snapshot.sellerType().name())
                .param("observed_gtin", snapshot.identity().gtin(), Types.VARCHAR)
                .param("observed_brand", snapshot.identity().brand(), Types.VARCHAR)
                .param("observed_mpn", snapshot.identity().mpn(), Types.VARCHAR)
                .param("observed_sku", snapshot.identity().sku(), Types.VARCHAR)
                .param("observed_title", snapshot.identity().title(), Types.VARCHAR)
                .param("extraction_method", snapshot.extraction().method())
                .param("extraction_confidence", BigDecimal.valueOf(snapshot.extraction().confidence()).setScale(2, RoundingMode.HALF_UP))
                .param("quarantine", quarantine)
                .update();
    }

    /**
     * Le dernier relevé est-il une référence fiable ? Sinon, ce relevé le confirme ou le rejette.
     * Un prix qui s'écarte de plus que le seuil de la dernière référence fiable est suspect.
     */
    private String decideQuarantine(long listingId, LocalDate day, BigDecimal price) {
        Optional<Previous> latest = previous(listingId, day, false);
        if (latest.isEmpty()) {
            return QUARANTINE_NONE;
        }
        BigDecimal threshold = thresholdFor(listingId);
        Previous last = latest.get();
        if (QUARANTINE_SUSPECT.equals(last.quarantine())) {
            if (within(price, last.price(), threshold)) {
                setQuarantine(last.id(), QUARANTINE_CONFIRMED);
                return QUARANTINE_NONE;
            }
            Optional<Previous> trusted = previous(listingId, day, true);
            if (trusted.isPresent() && within(price, trusted.get().price(), threshold)) {
                setQuarantine(last.id(), QUARANTINE_REJECTED);
                return QUARANTINE_NONE;
            }
            return QUARANTINE_SUSPECT;
        }
        Previous reference = previous(listingId, day, true).orElse(last);
        return within(price, reference.price(), threshold) ? QUARANTINE_NONE : QUARANTINE_SUSPECT;
    }

    private Optional<Previous> previous(long listingId, LocalDate before, boolean trustedOnly) {
        String filter = trustedOnly ? " and quarantine in ('none', 'confirmed')" : "";
        return jdbc.sql("select id, price, quarantine from price_snapshot where listing_id = :listing_id"
                        + " and observed_date < :before" + filter + " order by observed_date desc limit 1")
                .param("listing_id", listingId)
                .param("before", before)
                .query((rs, row) -> new Previous(rs.getLong("id"), rs.getBigDecimal("price"), rs.getString("quarantine")))
                .optional();
    }

    private BigDecimal thresholdFor(long listingId) {
        return jdbc.sql("""
                        select f.quarantine_threshold from listing_current_product lcp
                        join product p on p.id = lcp.product_id
                        join product_family f on f.code = p.family_code
                        where lcp.listing_id = :listing_id
                        """)
                .param("listing_id", listingId)
                .query(BigDecimal.class).optional()
                .orElse(DEFAULT_THRESHOLD);
    }

    private void setQuarantine(long snapshotId, String quarantine) {
        jdbc.sql("update price_snapshot set quarantine = :quarantine where id = :id")
                .param("quarantine", quarantine)
                .param("id", snapshotId)
                .update();
    }

    static boolean within(BigDecimal price, BigDecimal reference, BigDecimal threshold) {
        if (reference.signum() <= 0) {
            return true;
        }
        BigDecimal deviation = price.subtract(reference).abs().divide(reference, 6, RoundingMode.HALF_UP);
        return deviation.compareTo(threshold) <= 0;
    }

    private static BigDecimal amountOrNull(Money money) {
        return money == null ? null : money.amount();
    }

    private record Previous(long id, BigDecimal price, String quarantine) {
    }
}
