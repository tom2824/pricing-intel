package io.github.tom2824.pricingintel.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Les règles d'un fichier robots.txt qui s'appliquent à notre User-Agent. Sémantique proche de celle des
 * moteurs de recherche : le groupe le plus spécifique est retenu, la règle la plus longue l'emporte,
 * Allow gagne à longueur égale, et ce qui n'est pas mentionné est autorisé.
 */
public final class RobotsTxt {

    private static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of(), null);

    private final List<Rule> rules;
    private final Duration crawlDelay;

    private RobotsTxt(List<Rule> rules, Duration crawlDelay) {
        this.rules = List.copyOf(rules);
        this.crawlDelay = crawlDelay;
    }

    public static RobotsTxt allowAll() {
        return ALLOW_ALL;
    }

    /**
     * @param userAgentToken le nom de produit de notre User-Agent (ex. {@code pricing-intel}), comparé sans casse
     */
    public static RobotsTxt parse(String content, String userAgentToken) {
        String ourToken = userAgentToken.toLowerCase(Locale.ROOT);
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastLineWasUserAgent = false;

        for (String rawLine : content.split("\\r?\\n")) {
            String line = stripComment(rawLine).strip();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();

            switch (key) {
                case "user-agent" -> {
                    if (!lastLineWasUserAgent || current == null) {
                        current = new Group();
                        groups.add(current);
                    }
                    current.agents.add(value.toLowerCase(Locale.ROOT));
                    lastLineWasUserAgent = true;
                    continue;
                }
                case "allow" -> {
                    if (current != null && !value.isEmpty()) {
                        current.rules.add(new Rule(true, value));
                    }
                }
                case "disallow" -> {
                    if (current != null && !value.isEmpty()) {
                        current.rules.add(new Rule(false, value));
                    }
                }
                case "crawl-delay" -> {
                    if (current != null) {
                        current.crawlDelay = parseDelay(value);
                    }
                }
                default -> {
                    // sitemap, host, etc. : ignorés
                }
            }
            lastLineWasUserAgent = false;
        }

        Group chosen = groups.stream()
                .filter(g -> g.agents.stream().anyMatch(a -> !a.equals("*") && ourToken.contains(a)))
                .findFirst()
                .or(() -> groups.stream().filter(g -> g.agents.contains("*")).findFirst())
                .orElse(null);

        return chosen == null ? ALLOW_ALL : new RobotsTxt(chosen.rules, chosen.crawlDelay);
    }

    public boolean isAllowed(String path) {
        String target = (path == null || path.isEmpty()) ? "/" : path;
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.matches(target) && (best == null || rule.specificity() > best.specificity()
                    || (rule.specificity() == best.specificity() && rule.allow && !best.allow))) {
                best = rule;
            }
        }
        return best == null || best.allow;
    }

    public Optional<Duration> crawlDelay() {
        return Optional.ofNullable(crawlDelay);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static Duration parseDelay(String value) {
        try {
            double seconds = Double.parseDouble(value);
            return seconds > 0 ? Duration.ofMillis((long) (seconds * 1000)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
        private Duration crawlDelay;
    }

    private static final class Rule {
        private final boolean allow;
        private final String pattern;
        private final Pattern regex;

        private Rule(boolean allow, String pattern) {
            this.allow = allow;
            this.pattern = pattern;
            this.regex = toRegex(pattern);
        }

        private boolean matches(String path) {
            return regex.matcher(path).lookingAt();
        }

        private int specificity() {
            return pattern.length();
        }

        private static Pattern toRegex(String pattern) {
            StringBuilder sb = new StringBuilder();
            boolean anchored = pattern.endsWith("$");
            String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
            for (String part : body.split("\\*", -1)) {
                if (!sb.isEmpty()) {
                    sb.append(".*");
                }
                sb.append(Pattern.quote(part));
            }
            if (anchored) {
                sb.append("$");
            }
            return Pattern.compile(sb.toString());
        }
    }
}
