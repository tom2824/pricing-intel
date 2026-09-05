package io.github.tom2824.pricingintel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<SourceEntity, String> {
}
