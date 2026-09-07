package io.github.tom2824.pricingintel.pricing;

import java.util.List;

/** Port de sortie : où vont les recommandations calculées (base, console...). */
@FunctionalInterface
public interface RecommendationSink {

    void accept(List<Recommendation> recommendations);

    static RecommendationSink none() {
        return recommendations -> {
        };
    }
}
