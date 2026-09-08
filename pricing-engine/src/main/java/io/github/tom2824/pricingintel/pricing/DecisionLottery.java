package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.ProductId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Tirage au sort de la règle appliquée chaque jour à notre prix (ADR 0023). Uniforme entre les règles
 * candidates, mais reproductible : la graine dépend du jour et du produit, donc deux exécutions le même jour
 * prennent la même décision et l'historique reste explicable.
 */
public final class DecisionLottery {

    private DecisionLottery() {
    }

    /**
     * @param candidates clés de profil parmi lesquelles tirer, dans un ordre stable (l'ordre fait partie de la graine)
     * @return la clé tirée
     * @throws IllegalArgumentException si aucune candidate
     */
    public static String pick(LocalDate day, ProductId product, List<String> candidates) {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(product, "product");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidate profile to pick from");
        }
        long seed = day.toEpochDay() * 1_000_003L + product.value().hashCode() * 31L + candidates.hashCode();
        return candidates.get(new Random(seed).nextInt(candidates.size()));
    }
}
