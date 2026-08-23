package com.pgoogol.finance.category.infrastructure;

import com.pgoogol.finance.category.domain.Category;
import com.pgoogol.finance.category.domain.CategoryDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Całe drzewo jednym zapytaniem — rodzic dociągany joinem, żeby budowanie
     * struktury nie odpytywało bazy raz na węzeł.
     */
    @Query("""
        select c from Category c \
        left join fetch c.parent \
        where (:direction is null or c.direction = :direction) \
          and (:includeArchived = true or c.archived = false) \
        order by c.name asc""")
    List<Category> findTree(@Param("direction") @Nullable CategoryDirection direction,
                            @Param("includeArchived") boolean includeArchived);

    @Query("""
        select count(c) > 0 from Category c \
        where lower(c.name) = lower(:name) \
          and ((:parentId is null and c.parent is null) or c.parent.id = :parentId) \
          and (:excludeId is null or c.id <> :excludeId)""")
    boolean existsSibling(@Param("parentId") @Nullable Long parentId,
                          @Param("name") String name,
                          @Param("excludeId") @Nullable Long excludeId);
}
