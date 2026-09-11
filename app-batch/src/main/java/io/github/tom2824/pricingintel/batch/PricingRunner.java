package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.persistence.MarketOfferQuery;
import io.github.tom2824.pricingintel.persistence.PostgresRecommendationSink;
import io.github.tom2824.pricingintel.pricing.MarketBuilder;
import io.github.tom2824.pricingintel.pricing.MarketScope;
import io.github.tom2824.pricingintel.pricing.MarketView;
import io.github.tom2824.pricingintel.pricing.ObservedOffer;
import io.github.tom2824.pricingintel.pricing.PricingEngine;
import io.github.tom2824.pricingintel.pricing.PricingProfile;
import io.github.tom2824.pricingintel.pricing.Recommendation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Après la collecte : pour chaque produit actif, construit le marché strict et le marché de segment sur la
 * fenêtre de fraîcheur, calcule une recommandation par profil déclaré, affiche celle du profil de référence
 * et stocke le tout (ADR 0005, 0022). Ne fait rien sans base (profil postgres absent) ou si {@code pricing.enabled=false}.
 */
@Component
@Order(2)
class PricingRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PricingRunner.class);

    private final PricingProperties properties;
    private final ObjectProvider<MarketOfferQuery> offers;
    private final ObjectProvider<PostgresRecommendationSink> sink;
    private final Clock clock;
    private List<Recommendation> lastRecommendations = List.of();

    PricingRunner(PricingProperties properties, ObjectProvider<MarketOfferQuery> offers,
                  ObjectProvider<PostgresRecommendationSink> sink, Clock clock) {
        this.properties = properties;
        this.offers = offers;
        this.sink = sink;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        MarketOfferQuery query = offers.getIfAvailable();
        if (!properties.enabled() || query == null) {
            return;
        }
        Map<String, PricingProfile> profiles = properties.toProfiles();
        String defaultKey = properties.defaultProfileKey();
        MarketBuilder builder = new MarketBuilder(properties.toMarketRules());
        PricingEngine engine = new PricingEngine();
        Instant now = clock.instant();
        Instant since = now.minus(properties.freshness());
        List<Recommendation> recommendations = new ArrayList<>();

        LOG.info("Recommandations : {} profil(s) ({}), référence « {} » ; {} source(s) min, plancher marge {} %, plafond {} % médiane, ±{} %/jour, arrondi ,{}",
                profiles.size(), String.join(", ", profiles.keySet()), defaultKey, properties.minSources(),
                properties.marginFloorPercent(), properties.ceilingPercentOfMedian(), properties.maxDailyMovePercent(), properties.roundingCents());
        int failed = 0;
        for (MarketOfferQuery.ActiveProduct product : query.activeProducts()) {
            // Un produit en échec (relevé inattendu, erreur SQL) prive ce produit de recommandation, pas les autres.
            try {
                for (MarketScope scope : MarketScope.values()) {
                    List<ObservedOffer> observed = query.latestOffers(product.id(), scope, since);
                    MarketView market = builder.build(product.context().id(), scope, now, product.currency(), observed);
                    for (Map.Entry<String, PricingProfile> entry : profiles.entrySet()) {
                        boolean isDefault = entry.getKey().equals(defaultKey);
                        Recommendation r = engine.recommend(market, product.context(), entry.getValue(), entry.getKey(), isDefault);
                        recommendations.add(r);
                        if (scope == MarketScope.STRICT && isDefault) {
                            LOG.info("  {} · {} · {}", product.context().name(), summary(r), r.explanation().render());
                        }
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                LOG.warn("  {} : recommandations impossibles ({})", product.context().name(), e.toString());
            }
        }
        if (failed > 0) {
            LOG.error("{} produit(s) sans recommandation aujourd'hui", failed);
        }
        lastRecommendations = List.copyOf(recommendations);

        PostgresRecommendationSink target = sink.getIfAvailable();
        if (target != null) {
            target.accept(recommendations);
            LOG.info("{} recommandation(s) stockée(s)", recommendations.size());
        }
    }

    private static String summary(Recommendation r) {
        Optional<BigDecimal> index = r.indexVersusMedian();
        return r.priceIfAny().map(p -> "proposé " + p + index.map(i -> " (index " + i + ")").orElse("")
                + (r.fellBack() ? " [repli]" : "")).orElse("aucun prix proposable");
    }

    List<Recommendation> lastRecommendations() {
        return lastRecommendations;
    }
}
