package io.github.tom2824.pricingintel.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductIdentifierRepository extends JpaRepository<ProductIdentifierEntity, Long> {

    Optional<ProductIdentifierEntity> findBySchemeAndValue(String scheme, String value);
}
