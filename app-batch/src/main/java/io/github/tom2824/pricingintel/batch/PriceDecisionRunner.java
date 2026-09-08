package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.persistence.MarketOfferQuery;
import io.github.tom2824.pricingintel.persistence.PostgresPriceDecisionStore;
import io.github.tom2824.pricingintel.pricing.DecisionLottery;
import io.github.tom2824.pricingintel.pricing.MarketScope;
import io.github.tom2824.pricingintel.pricing.Recommendation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Après les recommandations : pour chaque produit actif, tire au sort une règle parmi celles qui proposent un
 * prix sur le marché strict, et applique sa recommandation comme nouveau prix courant (ADR 0023). Les garde-fous
 * du moteur (variation maximale par jour, marge plancher, plafond, arrondi) sont déjà dans la recommandation.
 * Une décision par produit et par jour, toujours enregistrée, même quand elle ne change rien.
 */
@Component
@Order(3)
class PriceDecisionRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PriceDecisionRunner.class);

    private final PricingProperties properties;
    private final PricingRunner pricing;
    private final ObjectProvider<MarketOfferQuery> offers;
    private final ObjectProvider<PostgresPriceDecisionStore> store;
    private final Clock clock;

    PriceDecisionRunner(PricingProperties properties, PricingRunner pricing, ObjectProvider<MarketOfferQuery> offers,
                        ObjectProvider<PostgresPriceDecisionStore> store, Clock clock) {
        this.properties = properties;
        this.pricing = pricing;
        this.offers = offers;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        MarketOfferQuery query = offers.getIfAvailable();
        PostgresPriceDecisionStore target = store.getIfAvailable();
        if (!properties.enabled() || !properties.dailyDecision() || query == null || target == null) {
            return;
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Map<String, List<Recommendation>> byProduct = new LinkedHashMap<>();
        for (Recommendation r : pricing.lastRecommendations()) {
            if (r.market().scope() == MarketScope.STRICT) {
                byProduct.computeIfAbsent(r.product().value(), k -> new ArrayList<>()).add(r);
            }
        }

        int recorded = 0;
        int changed = 0;
        for (MarketOfferQuery.ActiveProduct product : query.activeProducts()) {
            List<Recommendation> candidates = byProduct.getOrDefault(String.valueOf(product.id()), List.of()).stream()
                    .filter(r -> !r.fellBack() && r.price() != null)
                    .toList();
            BigDecimal oldPrice = product.context().currentPrice() == null ? null : product.context().currentPrice().amount();
            PostgresPriceDecisionStore.Decision decision;
            if (candidates.isEmpty()) {
                decision = new PostgresPriceDecisionStore.Decision(product.id(), today, now, oldPrice, oldPrice,
                        product.currency().getCurrencyCode(), properties.defaultProfileKey(), "hold", false,
                        "aucune règle ne propose de prix aujourd'hui (marché insuffisant) : prix maintenu");
            } else {
                List<String> keys = candidates.stream().map(Recommendation::profileKey).sorted().toList();
                String key = DecisionLottery.pick(today, product.context().id(), keys);
                Recommendation chosen = candidates.stream().filter(r -> r.profileKey().equals(key)).findFirst().orElseThrow();
                BigDecimal newPrice = chosen.price().amount();
                boolean moves = oldPrice == null || oldPrice.compareTo(newPrice) != 0;
                decision = new PostgresPriceDecisionStore.Decision(product.id(), today, now, oldPrice, newPrice,
                        product.currency().getCurrencyCode(), key, chosen.strategyId(), moves,
                        (moves ? "règle « " + key + " » : " + oldPrice + " → " + newPrice : "règle « " + key + " » : prix inchangé à " + newPrice)
                                + " · " + chosen.explanation().render());
            }
            if (target.record(decision)) {
                recorded++;
                if (decision.changed()) {
                    changed++;
                    LOG.info("  {} · règle {} · {} → {}", product.context().name(), decision.profileKey(), decision.oldPrice(), decision.newPrice());
                }
            }
        }
        LOG.info("Décisions tarifaires du {} : {} enregistrée(s), {} prix modifié(s)", today, recorded, changed);
    }
}
