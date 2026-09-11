package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écrit les relevés dans {@code price_snapshot} : une ligne par annonce et par jour (upsert : rejouer une
 * collecte remplace le relevé du jour), et décide de la quarantaine (ADR 0017) en comparant au dernier relevé
 * de confiance de l'annonce, avec le seuil de la famille du produit en vigueur.
 *
 * <p>Deux allers-retours par relevé, quelle que soit la décision : une lecture qui résout l'annonce, son seuil
 * et ses deux relevés de référence, puis une écriture qui pose le relevé et, dans la même instruction, confirme
 * ou rejette le relevé suspect précédent. Depuis un runner distant, chaque aller-retour compte.
 */
public class PostgresPriceSink implements PriceSink {

    static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.500");

    static final String QUARANTINE_NONE = "none";
    static final String QUARANTINE_SUSPECT = "suspect";
    static final String QUARANTINE_CONFIRMED = "confirmed";
    static final String QUARANTINE_REJECTED = "rejected";

    /** L'annonce, le seuil de sa famille, son dernier relevé et son dernier relevé de confiance, en une lecture. */
    private static final String CONTEXT = """
            select l.id as listing_id,
                   coalesce(f.quarantine_threshold, :default_threshold) as threshold,
                   last.id as last_id, last.price as last_price, last.quarantine as last_quarantine,
                   trusted.id as trusted_id, trusted.price as trusted_price
            from listing l
            left join listing_current_product lcp on lcp.listing_id = l.id
            left join product p on p.id = lcp.product_id
            left join product_family f on f.code = p.family_code
            left join lateral (
                select s.id, s.price, s.quarantine from price_snapshot s
                where s.listing_id = l.id and s.observed_date < :day
                order by s.observed_date desc limit 1
            ) last on true
            left join lateral (
                select s.id, s.price from price_snapshot s
                where s.listing_id = l.id and s.observed_date < :day and s.quarantine in ('none', 'confirmed')
                order by s.observed_date desc limit 1
            ) trusted on true
            where l.code = :code
            """;

    /** Le relevé du jour, et le sort du relevé suspect précédent dans la même instruction (rien si :resolve_id est nul). */
    private static final String UPSERT = """
            with resolved as (
                update price_snapshot set quarantine = :resolve_quarantine where id = :resolve_id
            )
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
        LocalDate day = snapshot.observedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        Context context = jdbc.sql(CONTEXT)
                .param("code", code)
                .param("day", day)
                .param("default_threshold", DEFAULT_THRESHOLD)
                .query(PostgresPriceSink::toContext)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown listing '" + code + "': import it into the catalogue before collecting"));
        Decision decision = decide(context, snapshot.price().amount());

        jdbc.sql(UPSERT)
                .param("resolve_id", decision.resolveId(), Types.BIGINT)
                .param("resolve_quarantine", decision.resolveQuarantine(), Types.VARCHAR)
                .param("listing_id", context.listingId())
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
                .param("quarantine", decision.quarantine())
                .update();
    }

    /** Un relevé antérieur : identifiant, prix, et son état de quarantaine. */
    record Previous(long id, BigDecimal price, String quarantine) {
    }

    /**
     * @param last    le dernier relevé de l'annonce, quel que soit son état, ou {@code null}
     * @param trusted le dernier relevé de confiance (ni suspect ni rejeté), ou {@code null}
     */
    record Context(long listingId, BigDecimal threshold, Previous last, Previous trusted) {
    }

    /**
     * @param quarantine        état du relevé du jour
     * @param resolveId         relevé suspect précédent à trancher, ou {@code null}
     * @param resolveQuarantine son nouvel état ({@code confirmed} ou {@code rejected}), ou {@code null}
     */
    record Decision(String quarantine, Long resolveId, String resolveQuarantine) {
        static Decision of(String quarantine) {
            return new Decision(quarantine, null, null);
        }
    }

    /**
     * Le dernier relevé est-il une référence fiable ? Sinon, ce relevé le confirme ou le rejette.
     * Un prix qui s'écarte de plus que le seuil de la dernière référence fiable est suspect. Fonction pure.
     */
    static Decision decide(Context context, BigDecimal price) {
        Previous last = context.last();
        if (last == null) {
            return Decision.of(QUARANTINE_NONE);
        }
        BigDecimal threshold = context.threshold();
        Previous trusted = context.trusted();
        if (QUARANTINE_SUSPECT.equals(last.quarantine())) {
            if (within(price, last.price(), threshold)) {
                return new Decision(QUARANTINE_NONE, last.id(), QUARANTINE_CONFIRMED);
            }
            if (trusted != null && within(price, trusted.price(), threshold)) {
                return new Decision(QUARANTINE_NONE, last.id(), QUARANTINE_REJECTED);
            }
            return Decision.of(QUARANTINE_SUSPECT);
        }
        Previous reference = trusted != null ? trusted : last;
        return Decision.of(within(price, reference.price(), threshold) ? QUARANTINE_NONE : QUARANTINE_SUSPECT);
    }

    private static Context toContext(ResultSet rs, int row) throws SQLException {
        long lastId = rs.getLong("last_id");
        Previous last = rs.wasNull() ? null : new Previous(lastId, rs.getBigDecimal("last_price"), rs.getString("last_quarantine"));
        long trustedId = rs.getLong("trusted_id");
        Previous trusted = rs.wasNull() ? null : new Previous(trustedId, rs.getBigDecimal("trusted_price"), QUARANTINE_NONE);
        return new Context(rs.getLong("listing_id"), rs.getBigDecimal("threshold"), last, trusted);
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
}
