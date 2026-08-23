package com.pgoogol.finance.categorization.infrastructure;

import com.pgoogol.finance.categorization.domain.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    /**
     * Reguły w kolejności rozstrzygania, z dociągniętą kategorią — podgląd
     * wyciągu pokazuje jej nazwę przy każdym wierszu, więc bez tego byłoby
     * jedno zapytanie na wiersz.
     */
    @Query("""
        select r from CategoryRule r \
        join fetch r.category \
        where (:activeOnly = false or r.active = true) \
        order by r.priority asc, r.id asc""")
    List<CategoryRule> findAllOrdered(@Param("activeOnly") boolean activeOnly);

    @Query("""
        select r from CategoryRule r \
        join fetch r.category \
        where r.id = :id""")
    Optional<CategoryRule> findDetailedById(@Param("id") long id);

    boolean existsByPatternIgnoreCaseAndCategoryId(String pattern, long categoryId);
}
