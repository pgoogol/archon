package com.pgoogol.finance.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Wspólne parametry każdego raportu. Jeden rekord zamiast sześciu argumentów
 * powtarzanych w każdej sygnaturze — a przy dokładaniu raportu nie ma gdzie
 * pominąć filtra.
 *
 * <p>Puste listy znaczą „wszystkie": brak filtra, nie brak danych.</p>
 *
 * @param categoryIds wskazane kategorie <b>wraz z ich podkategoriami</b> —
 *                    filtr po „Jedzeniu" bez podkategorii pokazywałby zero
 *                    w drzewie, w którym wszystko siedzi w liściach
 */
public record ReportRange(
    LocalDate from,
    LocalDate to,
    Granularity granularity,
    List<Long> accountIds,
    List<Long> categoryIds) {

    public ReportRange {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(granularity, "granularity");
        accountIds = List.copyOf(Objects.requireNonNullElse(accountIds, List.of()));
        categoryIds = List.copyOf(Objects.requireNonNullElse(categoryIds, List.of()));
    }

    public boolean allAccounts() {

        return accountIds.isEmpty();
    }

    public boolean allCategories() {

        return categoryIds.isEmpty();
    }
}
