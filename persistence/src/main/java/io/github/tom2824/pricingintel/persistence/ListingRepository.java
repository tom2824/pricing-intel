package io.github.tom2824.pricingintel.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ListingRepository extends JpaRepository<ListingEntity, Long> {

    Optional<ListingEntity> findByCode(String code);

    @Query("select l from ListingEntity l join fetch l.source where l.active = true order by l.code")
    List<ListingEntity> findActive();
}
