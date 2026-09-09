package com.pgoogol.kitchen.dictionary.infrastructure;

import com.pgoogol.kitchen.dictionary.domain.DictionaryTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Wspólny zestaw operacji dla słowników prostych. Sam nie jest beanem —
 * beanami są interfejsy pochodne, po jednym na tabelę.
 */
@NoRepositoryBean
public interface DictionaryTermRepository<T extends DictionaryTerm> extends JpaRepository<T, Long> {

    Optional<T> findByNameNormalized(String nameNormalized);

    List<T> findByNameNormalizedIn(Collection<String> nameNormalized);

    List<T> findAllByOrderByNameAsc();
}
