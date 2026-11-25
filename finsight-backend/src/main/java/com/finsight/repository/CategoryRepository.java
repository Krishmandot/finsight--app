package com.finsight.repository;

import com.finsight.domain.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** System categories plus the given user's own categories. */
    @Query("select c from Category c where c.userId is null or c.userId = :userId order by lower(c.name)")
    List<Category> findAllVisibleTo(@Param("userId") UUID userId);

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name)"
        + " and (c.userId is null or c.userId = :userId) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsNameVisibleTo(@Param("userId") UUID userId, @Param("name") String name,
                                @Param("excludeId") UUID excludeId);

    /**
     * Moves every transaction of the user (including soft-deleted ones, which still hold the foreign key)
     * from one category to another, or to "uncategorized" when {@code target} is null.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Transaction t set t.category = :target where t.category = :source and t.userId = :userId")
    int reassignTransactions(@Param("userId") UUID userId, @Param("source") Category source,
                             @Param("target") Category target);
}
