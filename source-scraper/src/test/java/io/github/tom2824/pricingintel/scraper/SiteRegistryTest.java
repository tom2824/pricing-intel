package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SiteRegistryTest {

    private static SiteDefinition site(String id, String host) {
        return new SiteDefinition(id, host, List.of(new ExtractorSpec.JsonLd()));
    }

    @Test
    void matchesExactHostAndSubdomainPatterns() {
        SiteDefinition ldlc = site("ldlc", "*.ldlc.com");
        SiteDefinition topachat = site("topachat", "www.topachat.com");

        assertThat(ldlc.matches("www.ldlc.com")).isTrue();
        assertThat(ldlc.matches("ldlc.com")).isTrue();
        assertThat(ldlc.matches("LDLC.COM")).isTrue();
        assertThat(ldlc.matches("notldlc.com")).isFalse();
        assertThat(topachat.matches("www.topachat.com")).isTrue();
        assertThat(topachat.matches("topachat.com")).isFalse();
    }

    @Test
    void prefersTheMostSpecificSiteRegardlessOfDeclarationOrder() {
        SiteDefinition any = site("any", "*");
        SiteDefinition domain = site("domain", "*.shop.test");
        SiteDefinition exact = site("exact", "www.shop.test");

        SiteRegistry registry = SiteRegistry.strict(List.of(any, domain, exact));

        assertThat(registry.find("www.shop.test")).map(SiteDefinition::id).contains("exact");
        assertThat(registry.find("m.shop.test")).map(SiteDefinition::id).contains("domain");
        assertThat(registry.find("other.test")).map(SiteDefinition::id).contains("any");
    }

    @Test
    void strictRegistryRefusesUnknownHostsWhileFallbackAcceptsThem() {
        List<SiteDefinition> sites = List.of(site("ldlc", "*.ldlc.com"));

        assertThat(SiteRegistry.strict(sites).find("unknown.test")).isEmpty();
        assertThat(SiteRegistry.withGenericFallback(sites).find("unknown.test"))
                .map(SiteDefinition::id).contains("generic-jsonld");
        assertThat(SiteRegistry.strict(sites).find("")).isEmpty();
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatThrownBy(() -> SiteRegistry.strict(List.of(site("a", "a.test"), site("a", "b.test"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
