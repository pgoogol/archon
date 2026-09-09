package com.pgoogol.kitchen.dictionary.application;

import com.pgoogol.kitchen.dictionary.domain.Cuisine;
import com.pgoogol.kitchen.dictionary.domain.Diet;
import com.pgoogol.kitchen.dictionary.domain.DictionaryTerm;
import com.pgoogol.kitchen.dictionary.domain.Equipment;
import com.pgoogol.kitchen.dictionary.domain.NameNormalizer;
import com.pgoogol.kitchen.dictionary.domain.RecipeCategory;
import com.pgoogol.kitchen.dictionary.domain.Tag;
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Zamiana nazw na pozycje słowników.
 *
 * <p>Słowniki zamknięte (kuchnia, kategoria, dieta) rozpoznają wyłącznie to, co
 * w nich jest — nieznana nazwa nie zakłada nowej pozycji i nie jest błędem,
 * bo model językowy potrafi zaproponować „kuchnia fusion". Słowniki otwarte
 * (tagi, sprzęt) rosną same, bo to etykiety właściciela, nie zamknięta lista.</p>
 */
@Service
@RequiredArgsConstructor
public class TermService {

    private final CuisineRepository cuisines;
    private final RecipeCategoryRepository categories;
    private final DietRepository diets;
    private final TagRepository tags;
    private final EquipmentRepository equipment;
    private final UnitRepository units;

    @Transactional(readOnly = true)
    public Optional<Cuisine> findCuisine(String name) {

        return findTerm(cuisines, name);
    }

    @Transactional(readOnly = true)
    public Optional<RecipeCategory> findCategory(String name) {

        return findTerm(categories, name);
    }

    @Transactional(readOnly = true)
    public Set<Diet> findDiets(Collection<String> names) {

        return findTerms(diets, names);
    }

    @Transactional(readOnly = true)
    public Optional<Unit> findUnit(String code) {

        if (Objects.isNull(code) || code.isBlank()) {

            return Optional.empty();
        }
        return units.findByCode(code.trim());
    }

    /** Tagi rosną same: etykieta, której nie ma, powstaje przy zapisie przepisu. */
    @Transactional
    public Set<Tag> resolveTags(Collection<String> names) {

        return resolveOpen(tags, names, Tag::new);
    }

    /** Sprzęt tak samo jak tagi — „termomiks" nie ma jak stać w liście startowej. */
    @Transactional
    public Set<Equipment> resolveEquipment(Collection<String> names) {

        return resolveOpen(equipment, names, Equipment::new);
    }

    private <T extends DictionaryTerm> Optional<T> findTerm(DictionaryTermRepository<T> repository,
                                                            String name) {

        String normalized = NameNormalizer.normalize(name);
        if (Objects.isNull(normalized) || normalized.isBlank()) {

            return Optional.empty();
        }
        return repository.findByNameNormalized(normalized);
    }

    private <T extends DictionaryTerm> Set<T> findTerms(DictionaryTermRepository<T> repository,
                                                        Collection<String> names) {

        Set<String> normalized = normalizeAll(names);
        if (normalized.isEmpty()) {

            return Set.of();
        }
        List<T> found = repository.findByNameNormalizedIn(normalized);
        return new LinkedHashSet<>(found);
    }

    private <T extends DictionaryTerm> Set<T> resolveOpen(DictionaryTermRepository<T> repository,
                                                          Collection<String> names,
                                                          Function<String, T> factory) {

        if (Objects.isNull(names)) {

            return Set.of();
        }
        Set<T> resolved = new LinkedHashSet<>();
        names.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(name -> !name.isBlank())
            .forEach(name -> resolved.add(getOrCreate(repository, name, factory)));
        return resolved;
    }

    /**
     * Szuka po postaci porównawczej, ale zakłada pozycję z oryginalną pisownią:
     * „Łyżka” i „lyzka” to ta sama etykieta, a w słowniku ma zostać ta, którą
     * naprawdę wpisano.
     */
    private <T extends DictionaryTerm> T getOrCreate(DictionaryTermRepository<T> repository,
                                                     String name,
                                                     Function<String, T> factory) {

        String normalized = NameNormalizer.normalize(name);
        Optional<T> existing = repository.findByNameNormalized(normalized);
        if (existing.isPresent()) {

            return existing.get();
        }
        T created = factory.apply(name);
        return repository.save(created);
    }

    private Set<String> normalizeAll(Collection<String> names) {

        if (Objects.isNull(names)) {

            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        names.stream()
            .map(NameNormalizer::normalize)
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .forEach(normalized::add);
        return normalized;
    }
}
