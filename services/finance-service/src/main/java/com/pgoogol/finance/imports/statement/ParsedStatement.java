package com.pgoogol.finance.imports.statement;

import com.pgoogol.finance.common.ExceptionMessageConstants;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Wyciąg po sparsowaniu: okres, salda z nagłówka i stopki oraz wiersze.
 *
 * <p>Salda są opcjonalne, bo nie każdy format je podaje. Gdy są, służą
 * wyłącznie do uzgodnienia po zatwierdzeniu — rozjazd pokazujemy, nigdy
 * nie poprawiamy nim danych.</p>
 */
public record ParsedStatement(
        LocalDate periodFrom,
        LocalDate periodTo,
        Long openingBalanceMinor,
        Long closingBalanceMinor,
        List<RawRow> rows) {

    public ParsedStatement {

        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (Objects.nonNull(periodFrom) && Objects.nonNull(periodTo)
                && periodFrom.isAfter(periodTo)) {

            throw new IllegalArgumentException(ExceptionMessageConstants
                .STATEMENT_PERIOD_REVERSED.formatted(periodFrom, periodTo));
        }
    }

    public boolean isEmpty() {

        return rows.isEmpty();
    }
}
