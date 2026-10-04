package com.pgoogol.testdiagnostics.spring;

import com.pgoogol.testdiagnostics.core.AttributeDifference;
import com.pgoogol.testdiagnostics.core.EnvironmentCause;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wybiera powód nowego środowiska z opisów środowisk uruchomionych wcześniej:
 * <ul>
 *   <li>brak wcześniejszych: pierwsze środowisko;</li>
 *   <li>opis równy wcześniejszemu: ta sama konfiguracja, tamto środowisko wypadło
 *       z pamięci podręcznej (najnowsze z równych, bo to ono wypadło ostatnie);</li>
 *   <li>w innym razie: różnice względem najbliższego, czyli tego z najmniejszą liczbą
 *       różniących się wartości; przy remisie wcześniejszego.</li>
 * </ul>
 */
final class EnvironmentCauseResolver {

    EnvironmentCause resolve(ContextDescription description, List<KnownEnvironment> known, Map<Integer, String> closedBy) {

        if (known.isEmpty()) {

            return EnvironmentCause.first();
        }
        Optional<KnownEnvironment> same = known.reversed().stream()
            .filter(environment -> environment.description().equals(description))
            .findFirst();
        if (same.isPresent()) {

            int number = same.get().number();
            String closer = closedBy.getOrDefault(number, "");
            return new EnvironmentCause.Reloaded(number, closer);
        }
        KnownEnvironment nearest = known.stream()
            .min(Comparator.comparingInt(environment -> distance(environment.description(), description)))
            .orElseThrow();
        List<AttributeDifference> differences = differences(nearest.description(), description);
        return new EnvironmentCause.Differs(nearest.number(), differences);
    }

    /** Różnice atrybut po atrybucie, w kolejności atrybutów nowego opisu. */
    static List<AttributeDifference> differences(ContextDescription before, ContextDescription after) {

        Set<String> names = new LinkedHashSet<>(after.names());
        names.addAll(before.names());
        return names.stream()
            .map(name -> difference(name, before, after))
            .filter(difference -> !difference.added().isEmpty() || !difference.removed().isEmpty())
            .toList();
    }

    private static int distance(ContextDescription before, ContextDescription after) {

        return differences(before, after).stream()
            .mapToInt(difference -> difference.added().size() + difference.removed().size())
            .sum();
    }

    private static AttributeDifference difference(String name, ContextDescription before, ContextDescription after) {

        List<String> previous = before.values(name);
        List<String> current = after.values(name);
        List<String> added = current.stream().filter(value -> !previous.contains(value)).toList();
        List<String> removed = previous.stream().filter(value -> !current.contains(value)).toList();
        return new AttributeDifference(name, added, removed);
    }
}
