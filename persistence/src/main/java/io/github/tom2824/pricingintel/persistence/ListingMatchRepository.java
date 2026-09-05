package io.github.tom2824.pricingintel.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingMatchRepository extends JpaRepository<ListingMatchEntity, Long> {

    /** La correspondance validée en vigueur d'une annonce, s'il y en a une (au plus une, garantie par un index). */
    @Query("select m from ListingMatchEntity m where m.listing.id = :listingId and m.status = 'validated' and m.validTo is null")
    Optional<ListingMatchEntity> findCurrentValidated(@Param("listingId") Long listingId);
}
