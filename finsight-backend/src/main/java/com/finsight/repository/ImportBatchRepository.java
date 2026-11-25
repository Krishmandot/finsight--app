package com.finsight.repository;

import com.finsight.domain.entity.ImportBatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ImportBatch> findByIdAndUserId(UUID id, UUID userId);
}
