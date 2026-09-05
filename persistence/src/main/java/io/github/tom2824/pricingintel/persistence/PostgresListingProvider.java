package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.collector.ListingProvider;
import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les annonces actives, avec le produit de leur correspondance validée en vigueur. Une annonce sans
 * correspondance est relevée quand même (identifiant produit {@value #UNMATCHED}) : la donnée s'accumule,
 * le matching viendra.
 */
public class PostgresListingProvider implements ListingProvider {

    public static final String UNMATCHED = "unmatched";

    private final ListingRepository listings;
    private final JdbcClient jdbc;

    public PostgresListingProvider(ListingRepository listings, JdbcClient jdbc) {
        this.listings = listings;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> listings() {
        Map<Long, Long> currentProducts = jdbc.sql("select listing_id, product_id from listing_current_product")
                .query((rs, row) -> Map.entry(rs.getLong("listing_id"), rs.getLong("product_id")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return listings.findActive().stream()
                .map(entity -> new Listing(
                        new ListingId(entity.getCode()),
                        new ProductId(currentProducts.containsKey(entity.getId())
                                ? String.valueOf(currentProducts.get(entity.getId()))
                                : UNMATCHED),
                        new SourceId(entity.getSource().getCode()),
                        URI.create(entity.getUrl()),
                        entity.getExternalRef()))
                .toList();
    }
}
