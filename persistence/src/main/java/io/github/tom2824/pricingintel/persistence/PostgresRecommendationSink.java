package io.github.tom2824.pricingintel.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.pricing.Explanation;
import io.github.tom2824.pricingintel.pricing.MarketScope;
import io.github.tom2824.pricingintel.pricing.MarketView;
import io.github.tom2824.pricingintel.pricing.ObservedOffer;
import io.github.tom2824.pricingintel.pricing.PricingProfile;
import io.github.tom2824.pricingintel.pricing.Recommendation;
import io.github.tom2824.pricingintel.pricing.RecommendationSink;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stocke les recommandations avec leur marché et leur explication en JSON : c'est ce que l'onglet du
 * portfolio lira directement, sans recalcul (ADR 0005). Le profil utilisé est enregistré avec chaque ligne.
 */
public class PostgresRecommendationSink implements RecommendationSink {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcClient jdbc;
    private final PricingProfile profile;

    public PostgresRecommendationSink(JdbcClient jdbc, PricingProfile profile) {
        this.jdbc = jdbc;
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    @Override
    @Transactional
    public void accept(List<Recommendation> recommendations) {
        String profileJson = json(profileAsMap(profile));
        for (Recommendation r : recommendations) {
            jdbc.sql("""
                            insert into recommendation (product_id, computed_at, scope, strategy, fell_back, profile, price,
                                                        currency, index_vs_median, market, explanation)
                            values (:product_id, :computed_at, :scope, :strategy, :fell_back, cast(:profile as jsonb), :price,
                                    :currency, :index_vs_median, cast(:market as jsonb), cast(:explanation as jsonb))
                            on conflict (product_id, scope, computed_at) do update set
                                strategy = excluded.strategy, fell_back = excluded.fell_back, profile = excluded.profile,
                                price = excluded.price, currency = excluded.currency, index_vs_median = excluded.index_vs_median,
                                market = excluded.market, explanation = excluded.explanation
                            """)
                    .param("product_id", Long.parseLong(r.product().value()))
                    .param("computed_at", r.computedAt().atOffset(ZoneOffset.UTC))
                    .param("scope", r.market().scope() == MarketScope.STRICT ? "strict" : "segment")
                    .param("strategy", r.strategyId())
                    .param("fell_back", r.fellBack())
                    .param("profile", profileJson)
                    .param("price", r.price() == null ? null : r.price().amount(), Types.NUMERIC)
                    .param("currency", r.market().currency().getCurrencyCode())
                    .param("index_vs_median", r.indexVersusMedian().orElse(null), Types.NUMERIC)
                    .param("market", json(marketAsMap(r.market())))
                    .param("explanation", json(explanationAsMap(r.explanation())))
                    .update();
        }
    }

    static Map<String, Object> profileAsMap(PricingProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("strategy", profile.strategy().id());
        map.put("description", profile.strategy().describe());
        map.put("minSources", profile.minSources());
        map.put("marginFloorPercent", profile.marginFloorPercent());
        map.put("ceilingPercentOfMedian", profile.ceilingPercentOfMedian());
        map.put("maxDailyMovePercent", profile.maxDailyMovePercent());
        map.put("roundingCents", profile.roundingCents());
        return map;
    }

    static Map<String, Object> marketAsMap(MarketView market) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scope", market.scope().name().toLowerCase());
        map.put("asOf", market.asOf().toString());
        map.put("sourceCount", market.sourceCount());
        map.put("min", amount(market.min().orElse(null)));
        map.put("median", amount(market.median().orElse(null)));
        map.put("mean", amount(market.mean().orElse(null)));
        map.put("max", amount(market.max().orElse(null)));
        map.put("retained", market.retained().stream().map(PostgresRecommendationSink::offerAsMap).toList());
        map.put("excluded", market.excluded().stream().map(e -> {
            Map<String, Object> m = offerAsMap(e.offer());
            m.put("reason", e.reason());
            return m;
        }).toList());
        return map;
    }

    private static Map<String, Object> offerAsMap(ObservedOffer offer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", offer.source().value());
        map.put("listing", offer.listing().value());
        map.put("product", offer.product().value());
        map.put("price", offer.price().amount());
        map.put("observedAt", offer.observedAt().toString());
        map.put("availability", offer.availability().name());
        map.put("condition", offer.condition().name());
        map.put("sellerType", offer.sellerType().name());
        map.put("quarantined", offer.quarantined());
        return map;
    }

    static Map<String, Object> explanationAsMap(Explanation explanation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", explanation.render());
        map.put("steps", explanation.steps().stream().map(step -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("stage", step.stage());
            s.put("label", step.label());
            s.put("before", amount(step.before()));
            s.put("after", amount(step.after()));
            return s;
        }).toList());
        return map;
    }

    private static Object amount(Money money) {
        return money == null ? null : money.amount();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize recommendation payload", e);
        }
    }
}
