package com.finsight.repository;

import com.finsight.domain.entity.CategorizationRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, UUID> {

    @EntityGraph(attributePaths = "category")
    List<CategorizationRule> findAllByUserIdOrderByKeywordAsc(UUID userId);

    Optional<CategorizationRule> findByUserIdAndKeyword(UUID userId, String keyword);

    Optional<CategorizationRule> findByIdAndUserId(UUID id, UUID userId);

    void deleteAllByCategoryId(UUID categoryId);
}
