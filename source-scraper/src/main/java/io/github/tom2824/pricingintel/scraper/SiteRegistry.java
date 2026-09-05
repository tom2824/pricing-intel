package io.github.tom2824.pricingintel.scraper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Les sites connus, et éventuellement un site de repli pour les hôtes non déclarés. */
public final class SiteRegistry {

    private final List<SiteDefinition> sites;
    private final SiteDefinition fallback;

    /** @param fallback site appliqué aux hôtes qu'aucun site ne couvre, ou null pour les refuser */
    public SiteRegistry(List<SiteDefinition> sites, SiteDefinition fallback) {
        Set<String> ids = new HashSet<>();
        for (SiteDefinition site : sites) {
            if (!ids.add(site.id())) {
                throw new IllegalArgumentException("Duplicate site id '" + site.id() + "'");
            }
        }
        this.sites = sites.stream()
                .sorted(Comparator.comparingInt(SiteDefinition::specificity).reversed())
                .toList();
        this.fallback = fallback;
    }

    public static SiteRegistry strict(List<SiteDefinition> sites) {
        return new SiteRegistry(sites, null);
    }

    public static SiteRegistry withGenericFallback(List<SiteDefinition> sites) {
        return new SiteRegistry(sites, SiteDefinition.genericJsonLd());
    }

    public Optional<SiteDefinition> find(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return sites.stream().filter(s -> s.matches(host)).findFirst()
                .or(() -> Optional.ofNullable(fallback));
    }

    public List<SiteDefinition> sites() {
        return sites;
    }
}
