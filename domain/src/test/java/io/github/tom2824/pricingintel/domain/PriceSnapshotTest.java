package io.github.tom2824.pricingintel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PriceSnapshotTest {

    private static final ListingId LISTING = new ListingId("ldlc-rtx4070");
    private static final URI URL = URI.create("https://example.test/p/1");

    @Test
    void defaultsOptionalEnumsToUnknown() {
        PriceSnapshot snapshot = PriceSnapshot.builder(LISTING, Instant.EPOCH, URL, Money.eur("599.99")).build();

        assertThat(snapshot.availability()).isEqualTo(Availability.UNKNOWN);
        assertThat(snapshot.condition()).isEqualTo(ItemCondition.UNKNOWN);
        assertThat(snapshot.sellerType()).isEqualTo(SellerType.UNKNOWN);
        assertThat(snapshot.identity()).isEqualTo(ObservedIdentity.EMPTY);
        assertThat(snapshot.isDiscounted()).isFalse();
    }

    @Test
    void detectsDiscountWhenListPriceIsHigher() {
        PriceSnapshot snapshot = PriceSnapshot.builder(LISTING, Instant.EPOCH, URL, Money.eur("549.99"))
                .listPrice(Money.eur("599.99"))
                .build();

        assertThat(snapshot.isDiscounted()).isTrue();
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> PriceSnapshot.builder(LISTING, Instant.EPOCH, URL, Money.eur("0")).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityNormalizesBlanksToNull() {
        ObservedIdentity identity = new ObservedIdentity("  ", "MSI", " ", null, "RTX 4070 SUPER");

        assertThat(identity.gtin()).isNull();
        assertThat(identity.mpn()).isNull();
        assertThat(identity.hasGtin()).isFalse();
        assertThat(identity.brand()).isEqualTo("MSI");
    }
}
