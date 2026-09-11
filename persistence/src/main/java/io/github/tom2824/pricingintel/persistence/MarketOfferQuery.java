package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SellerType;
import io.github.tom2824.pricingintel.domain.SourceId;
import io.github.tom2824.pricingintel.pricing.MarketOffers;
import io.github.tom2824.pricingintel.pricing.MarketScope;
import io.github.tom2824.pricingintel.pricing.ObservedOffer;
import io.github.tom2824.pricingintel.pricing.ProductContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptateur PostgreSQL du port {@link MarketOffers} : le dernier relevé de chaque annonce du périmètre d'un
 * produit (strict : le produit ; segment : les produits de même clé d'équivalence), et le contexte des produits
 * actifs. Les règles du marché (fraîcheur, stock...) ne sont pas appliquées ici mais par le moteur, qui explique
 * chaque exclusion ; on ne remonte simplement pas les relevés plus vieux que la fenêtre demandée.
 * L'identifiant de produit du domaine porte la clé technique de la table, en texte.
 */
public class MarketOfferQuery implements MarketOffers {

    private static final String LATEST_OFFERS = """
            with scope_products as (
                select id from product where id = :product_id
                union
                select p2.id
                from product p1
                join product p2 on p2.equivalence_key = p1.equivalence_key and p1.equivalence_key is not null
                where p1.id = :product_id and :segment = true
            )
            select distinct on (s.listing_id)
                   l.code as listing_code, l.source_code, lcp.product_id, s.price, s.currency, s.observed_at,
                   s.availability, s.item_condition, s.seller_type, s.quarantine, s.extraction_confidence
            from price_snapshot s
            join listing l on l.id = s.listing_id
            join listing_current_product lcp on lcp.listing_id = l.id
            where lcp.product_id in (select id from scope_products)
              and l.active
              and s.observed_at >= :since
            order by s.listing_id, s.observed_at desc
            """;

    private final JdbcClient jdbc;

    public MarketOfferQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObservedOffer> latestOffers(ProductId product, MarketScope scope, Instant since) {
        return jdbc.sql(LATEST_OFFERS)
                .param("product_id", Long.parseLong(product.value()))
                .param("segment", scope == MarketScope.SEGMENT)
                .param("since", since.atOffset(ZoneOffset.UTC))
                .query(MarketOfferQuery::toOffer)
                .list();
    }

    /** Les produits actifs, avec leur prix actuel et leur prix d'achat quand ils sont renseignés. */
    @Override
    @Transactional(readOnly = true)
    public List<ActiveProduct> activeProducts() {
        return jdbc.sql("select id, name, family_code, current_price, purchase_price, currency from product where status = 'active' order by family_code, brand, name")
                .query((rs, row) -> {
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    BigDecimal current = rs.getBigDecimal("current_price");
                    BigDecimal purchase = rs.getBigDecimal("purchase_price");
                    ProductId id = new ProductId(String.valueOf(rs.getLong("id")));
                    ProductContext context = new ProductContext(id, rs.getString("name"),
                            current == null ? null : Money.of(current, currency),
                            purchase == null ? null : Money.of(purchase, currency), null);
                    return new ActiveProduct(id, rs.getString("family_code"), currency, context);
                })
                .list();
    }

    private static ObservedOffer toOffer(ResultSet rs, int row) throws SQLException {
        String quarantine = rs.getString("quarantine");
        return new ObservedOffer(
                new SourceId(rs.getString("source_code")),
                new ListingId(rs.getString("listing_code")),
                new ProductId(String.valueOf(rs.getLong("product_id"))),
                Money.of(rs.getBigDecimal("price"), Currency.getInstance(rs.getString("currency").strip())),
                rs.getObject("observed_at", java.time.OffsetDateTime.class).toInstant(),
                Availability.valueOf(rs.getString("availability")),
                ItemCondition.valueOf(rs.getString("item_condition")),
                SellerType.valueOf(rs.getString("seller_type")),
                "suspect".equals(quarantine) || "rejected".equals(quarantine),
                rs.getBigDecimal("extraction_confidence").doubleValue());
    }
}
