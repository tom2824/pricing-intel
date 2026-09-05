package io.github.tom2824.pricingintel.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByNaturalKey(String naturalKey);

    List<ProductEntity> findByEquivalenceKeyOrderByBrandAscNameAsc(String equivalenceKey);
}
