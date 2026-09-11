package io.github.tom2824.pricingintel.collector;

import java.time.Duration;
import java.time.Instant;

/**
 * Port de sortie : élaguer le stockage selon une politique de rétention (ADR 0024). Ce qui est purgé et ce qui
 * ne l'est jamais relève de l'adaptateur ; le contrat est qu'une purge n'efface jamais la dernière donnée d'une
 * série, et qu'une fenêtre nulle ou négative est refusée plutôt qu'appliquée.
 */
public interface Retention {

    record Policy(Duration defaultProfileRecommendations, Duration otherProfilesRecommendations, Duration failures) {
        public Policy {
            requirePositive(defaultProfileRecommendations, "defaultProfileRecommendations");
            requirePositive(otherProfilesRecommendations, "otherProfilesRecommendations");
            requirePositive(failures, "failures");
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("Retention window '" + name + "' must be positive, got " + duration);
            }
        }
    }

    record Result(int otherProfiles, int defaultProfile, int compacted, int failures) {
        public int deleted() {
            return otherProfiles + defaultProfile + failures;
        }
    }

    Result purge(Policy policy, Instant now);
}
