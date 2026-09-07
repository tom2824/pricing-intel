package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La trace d'une recommandation : une étape par décision, avec la valeur avant et après. C'est ce qu'un pricer
 * lit pour accepter ou refuser un prix ; sans elle, la recommandation ne vaut rien.
 */
public final class Explanation {

    /**
     * @param stage  {@code marché}, {@code stratégie} ou {@code règle}
     * @param before valeur en entrée, null pour la première étape
     * @param after  valeur en sortie, null si l'étape n'a rien produit
     */
    public record Step(String stage, String label, Money before, Money after) {
        public boolean changed() {
            return before != null && after != null && before.compareTo(after) != 0;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public Explanation add(String stage, String label, Money before, Money after) {
        steps.add(new Step(stage, label, before, after));
        return this;
    }

    public Explanation note(String stage, String label) {
        return add(stage, label, null, null);
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    /** Une ligne par étape, séparées par « · », comme dans l'exemple de l'ADR 0022. */
    public String render() {
        return steps.stream().map(Explanation::renderStep).collect(Collectors.joining(" · "));
    }

    private static String renderStep(Step step) {
        if (step.after() == null) {
            return step.label();
        }
        if (step.before() == null || !step.changed()) {
            return step.label() + " → " + step.after();
        }
        return step.label() + " : " + step.before() + " → " + step.after();
    }
}
