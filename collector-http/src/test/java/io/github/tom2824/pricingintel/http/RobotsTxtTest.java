package io.github.tom2824.pricingintel.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    @Test
    void emptyFileAllowsEverything() {
        RobotsTxt robots = RobotsTxt.parse("", "pricing-intel");

        assertThat(robots.isAllowed("/anything")).isTrue();
        assertThat(robots.crawlDelay()).isEmpty();
    }

    @Test
    void appliesWildcardGroupWhenNoSpecificGroupMatches() {
        String content = """
                User-agent: *
                Disallow: /panier/
                Disallow: /compte

                User-agent: Googlebot
                Disallow:
                """;
        RobotsTxt robots = RobotsTxt.parse(content, "pricing-intel");

        assertThat(robots.isAllowed("/fiche/rtx-4070.html")).isTrue();
        assertThat(robots.isAllowed("/panier/")).isFalse();
        assertThat(robots.isAllowed("/panier/etape-2")).isFalse();
        assertThat(robots.isAllowed("/compte-client")).isFalse();
    }

    @Test
    void prefersTheGroupNamingOurToken() {
        String content = """
                User-agent: *
                Disallow: /

                User-agent: pricing-intel
                Disallow: /private/
                Crawl-delay: 5
                """;
        RobotsTxt robots = RobotsTxt.parse(content, "pricing-intel");

        assertThat(robots.isAllowed("/fiche/1")).isTrue();
        assertThat(robots.isAllowed("/private/x")).isFalse();
        assertThat(robots.crawlDelay()).contains(Duration.ofSeconds(5));
    }

    @Test
    void longestRuleWinsAndAllowBeatsDisallowOnTie() {
        String content = """
                User-agent: *
                Disallow: /catalogue/
                Allow: /catalogue/public/
                Allow: /promo
                Disallow: /promo
                """;
        RobotsTxt robots = RobotsTxt.parse(content, "bot");

        assertThat(robots.isAllowed("/catalogue/interne")).isFalse();
        assertThat(robots.isAllowed("/catalogue/public/1")).isTrue();
        assertThat(robots.isAllowed("/promo")).isTrue();
    }

    @Test
    void supportsWildcardsAndEndAnchor() {
        String content = """
                User-agent: *
                Disallow: /*.pdf$
                Disallow: /search*
                """;
        RobotsTxt robots = RobotsTxt.parse(content, "bot");

        assertThat(robots.isAllowed("/docs/notice.pdf")).isFalse();
        assertThat(robots.isAllowed("/docs/notice.pdf?v=2")).isTrue();
        assertThat(robots.isAllowed("/search?q=gpu")).isFalse();
        assertThat(robots.isAllowed("/searching-tips")).isFalse();
        assertThat(robots.isAllowed("/sea")).isTrue();
    }

    @Test
    void ignoresCommentsAndMultipleUserAgentLinesShareRules() {
        String content = """
                # global rules
                User-agent: bot-one
                User-agent: pricing-intel   # nous
                Disallow: /x
                """;
        RobotsTxt robots = RobotsTxt.parse(content, "pricing-intel");

        assertThat(robots.isAllowed("/x/1")).isFalse();
        assertThat(robots.isAllowed("/y")).isTrue();
    }
}
