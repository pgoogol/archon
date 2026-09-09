package com.pgoogol.kitchen.dictionary.application;

import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
import com.pgoogol.kitchen.dictionary.domain.DictionaryEntry;
import com.pgoogol.kitchen.dictionary.domain.DictionaryTerm;
import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.dictionary.infrastructure.CuisineRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.DictionaryTermRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.DietRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.EquipmentRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.RecipeCategoryRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.TagRepository;
import com.pgoogol.kitchen.dictionary.infrastructure.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Odczyt słowników dla formularza przepisu i filtrów wyszukiwarki.
 *
 * <p>Wszystkie sześć naraz jednym wywołaniem: formularz potrzebuje ich razem,
 * a zmieniają się rzadko, więc front pobiera je raz na sesję.</p>
 */
@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final UnitRepository units;
    private final CuisineRepository cuisines;
    private final RecipeCategoryRepository categories;
    private final DietRepository diets;
    private final TagRepository tags;
    private final EquipmentRepository equipment;

    @Transactional(readOnly = true)
    public Dictionaries all() {

        List<Unit> unitList = units.findAllByOrderByIdAsc();
        return new Dictionaries(
            unitList,
            entries(cuisines),
            entries(categories),
            entries(diets),
            entries(tags),
            entries(equipment));
    }

    private List<DictionaryEntry> entries(DictionaryTermRepository<? extends DictionaryTerm> repository) {

        List<? extends DictionaryTerm> terms = repository.findAllByOrderByNameAsc();
        return terms.stream().map(DictionaryEntry::of).toList();
    }
}
