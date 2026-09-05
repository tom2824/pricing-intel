package io.github.tom2824.pricingintel.scraper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class Fixtures {

    private Fixtures() {
    }

    static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Document page(String name) {
        return Jsoup.parse(read("pages/" + name), "https://www.shop.test/fiche/" + name);
    }
}
