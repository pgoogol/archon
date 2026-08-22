package com.pgoogol.finance.imports.parser;

import com.pgoogol.finance.imports.statement.AmountParser;
import com.pgoogol.finance.imports.statement.ParsedStatement;
import com.pgoogol.finance.imports.statement.RawRow;
import com.pgoogol.finance.imports.statement.SourceFile;
import com.pgoogol.finance.imports.statement.StatementParser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Wyciąg z polskiego banku w formacie CSV — układ „lista operacji" z mBanku.
 *
 * <p>Cztery rzeczy, które wysypują naiwny parser i dlatego mają tu osobną
 * obsługę:</p>
 * <ul>
 *   <li><b>windows-1250</b> — plik nie jest w UTF-8, a polskie znaki odczytane
 *       jako UTF-8 zamieniają się w krzaki dopiero widoczne w opisie transakcji;</li>
 *   <li><b>separator średnik, przecinek dziesiętny, spacja jako separator
 *       tysięcy</b> — również spacja niełamliwa, patrz {@link AmountParser};</li>
 *   <li><b>nagłówek i stopka poza tabelą</b> — okres oraz salda otwarcia
 *       i zamknięcia stoją w liniach zaczynających się od {@code #}, przemieszanych
 *       z pustymi; tabela danych to tylko fragment pliku;</li>
 *   <li><b>transakcje kartowe w obcej walucie</b> — kwota obciążenia i kwota
 *       oryginalna siedzą w dwóch kolumnach i obie trafiają do wiersza.</li>
 * </ul>
 *
 * <p>Kolumny rozpoznajemy po nazwach nagłówka, nie po pozycji — bank potrafi
 * dołożyć kolumnę w środku między jednym eksportem a drugim.</p>
 */
@Component
@RequiredArgsConstructor
public class MbankCsvStatementParser implements StatementParser {

    /** Kodowanie eksportu. Odczyt jako UTF-8 daje krzaki, nie wyjątek. */
    static final Charset ENCODING = Charset.forName("windows-1250");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setDelimiter(';')
        .setQuote('"')
        .setIgnoreSurroundingSpaces(true)
        .setIgnoreEmptyLines(true)
        .get();

    private static final List<String> DATE_COLUMNS =
        List.of("data operacji", "data księgowania", "data transakcji");
    private static final List<String> DESCRIPTION_COLUMNS =
        List.of("opis operacji", "tytuł", "tytuł operacji");
    private static final List<String> COUNTERPARTY_COLUMNS =
        List.of("kontrahent", "nadawca/odbiorca", "odbiorca/nadawca");
    private static final List<String> REFERENCE_COLUMNS =
        List.of("numer referencyjny", "nr referencyjny", "identyfikator operacji");
    private static final List<String> AMOUNT_COLUMNS =
        List.of("kwota", "kwota operacji");
    private static final List<String> ORIGINAL_COLUMNS =
        List.of("kwota oryginalna", "kwota w walucie", "kwota w walucie operacji");

    private static final List<String> PERIOD_LABELS = List.of("za okres:", "za okres");
    private static final List<String> OPENING_LABELS =
        List.of("saldo początkowe", "saldo poczatkowe");
    private static final List<String> CLOSING_LABELS =
        List.of("saldo końcowe", "saldo koncowe");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    /** „-10,50 EUR" — kwota oryginalna niesie własną walutę w tej samej komórce. */
    private static final Pattern ORIGINAL_WITH_CURRENCY =
        Pattern.compile("^(?<amount>.+?)\\s*(?<currency>[A-Za-z]{3})$");

    private final AmountParser amountParser;

    @Override
    public boolean supports(SourceFile file) {

        String text = file.text(ENCODING);
        return records(text).stream().anyMatch(this::isHeaderRow);
    }

    @Override
    public ParsedStatement parse(SourceFile file, int minorUnit) {

        String text = file.text(ENCODING);
        List<CSVRecord> records = records(text);
        Map<String, Integer> columns = findColumns(records);
        StatementMetadata metadata = readMetadata(records, minorUnit);
        List<RawRow> rows = readRows(records, columns, minorUnit);
        return new ParsedStatement(metadata.periodFrom(), metadata.periodTo(),
            metadata.openingBalanceMinor(), metadata.closingBalanceMinor(), rows);
    }

    /**
     * Pętla zamiast strumienia świadomie: rozpoznanie wiersza danych zależy od
     * tego, czy nagłówek tabeli już był, a stopka go zamyka. Ten stan przenosi
     * się między iteracjami i strumień musiałby go i tak gdzieś odłożyć.
     */
    private List<RawRow> readRows(List<CSVRecord> records, Map<String, Integer> columns,
                                  int minorUnit) {

        List<RawRow> rows = new ArrayList<>();
        boolean insideTable = false;
        for (CSVRecord record : records) {

            if (isHeaderRow(record)) {

                insideTable = true;
                continue;
            }
            if (isMetadataRow(record)) {

                insideTable = false;
                continue;
            }
            if (insideTable && hasDate(record, columns)) {

                rows.add(toRawRow(record, columns, rows.size(), minorUnit));
            }
        }
        return List.copyOf(rows);
    }

    private List<CSVRecord> records(String text) {

        try (CSVParser parser = CSVParser.parse(new StringReader(text), FORMAT)) {

            return parser.getRecords();
        } catch (IOException ex) {

            throw new UncheckedIOException(ex);
        }
    }

    /** Wiersz nagłówka tabeli: ma kolumnę z datą operacji i kolumnę z kwotą. */
    private boolean isHeaderRow(CSVRecord record) {

        List<String> cells = normalizedCells(record);
        return containsAny(cells, DATE_COLUMNS) && containsAny(cells, AMOUNT_COLUMNS);
    }

    private boolean isMetadataRow(CSVRecord record) {

        if (record.size() == 0) {

            return false;
        }
        return record.get(0).startsWith("#") && !isHeaderRow(record);
    }

    private Map<String, Integer> findColumns(List<CSVRecord> records) {

        CSVRecord header = records.stream()
            .filter(this::isHeaderRow)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Plik nie zawiera nagłówka tabeli operacji"));

        List<String> cells = normalizedCells(header);
        Map<String, Integer> columns = new HashMap<>();
        putColumn(columns, "date", cells, DATE_COLUMNS);
        putColumn(columns, "description", cells, DESCRIPTION_COLUMNS);
        putColumn(columns, "counterparty", cells, COUNTERPARTY_COLUMNS);
        putColumn(columns, "reference", cells, REFERENCE_COLUMNS);
        putColumn(columns, "amount", cells, AMOUNT_COLUMNS);
        putColumn(columns, "original", cells, ORIGINAL_COLUMNS);
        return Map.copyOf(columns);
    }

    private void putColumn(Map<String, Integer> columns, String key,
                           List<String> cells, List<String> candidates) {

        int index = indexOfAny(cells, candidates);
        if (index >= 0) {

            columns.put(key, index);
        }
    }

    /**
     * Okres i salda stoją w liniach z krzyżykiem, przemieszanych z pustymi,
     * przed tabelą i za nią. Czytamy je osobnym przebiegiem — plik jest już
     * w pamięci, a przeplatanie tego z wierszami danych mieszałoby dwie sprawy.
     */
    private StatementMetadata readMetadata(List<CSVRecord> records, int minorUnit) {

        List<CSVRecord> metadataRows = records.stream()
            .filter(this::isMetadataRow)
            .toList();
        CSVRecord period = findLabelled(metadataRows, PERIOD_LABELS);
        CSVRecord opening = findLabelled(metadataRows, OPENING_LABELS);
        CSVRecord closing = findLabelled(metadataRows, CLOSING_LABELS);
        return new StatementMetadata(
            dateFrom(period, 1),
            dateFrom(period, 2),
            balanceFrom(opening, minorUnit),
            balanceFrom(closing, minorUnit));
    }

    private CSVRecord findLabelled(List<CSVRecord> metadataRows, List<String> labels) {

        return metadataRows.stream()
            .filter(record -> labels.contains(normalize(record.get(0))))
            .findFirst()
            .orElse(null);
    }

    private LocalDate dateFrom(CSVRecord record, int index) {

        if (Objects.isNull(record)) {

            return null;
        }
        return parseDateOrNull(cell(record, index));
    }

    private Long balanceFrom(CSVRecord record, int minorUnit) {

        if (Objects.isNull(record)) {

            return null;
        }
        return amountParser.parseOptional(cell(record, 1), minorUnit);
    }

    private boolean hasDate(CSVRecord record, Map<String, Integer> columns) {

        String raw = cell(record, columns.get("date"));
        return Objects.nonNull(parseDateOrNull(raw));
    }

    private RawRow toRawRow(CSVRecord record, Map<String, Integer> columns,
                            int ordinal, int minorUnit) {

        LocalDate bookedOn = parseDateOrNull(cell(record, columns.get("date")));
        String amountCell = cell(record, columns.get("amount"));
        long amountMinor = amountParser.parse(amountCell, minorUnit);
        String currency = currencyOf(amountCell);

        OriginalAmount original = readOriginal(record, columns, minorUnit);
        return new RawRow(
            ordinal,
            bookedOn,
            amountMinor,
            currency,
            original.amountMinor(),
            original.currency(),
            trimToNull(cell(record, columns.get("description"))),
            trimToNull(cell(record, columns.get("counterparty"))),
            trimToNull(cell(record, columns.get("reference"))));
    }

    /**
     * Kwota oryginalna niesie walutę w tej samej komórce („-10,50 EUR"), więc
     * rozdzielamy je, zanim którakolwiek trafi do wiersza.
     */
    private OriginalAmount readOriginal(CSVRecord record, Map<String, Integer> columns,
                                        int minorUnit) {

        String raw = cell(record, columns.get("original"));
        if (Objects.isNull(raw) || raw.isBlank()) {

            return new OriginalAmount(null, null);
        }
        Matcher matcher = ORIGINAL_WITH_CURRENCY.matcher(raw.trim());
        if (!matcher.matches()) {

            throw new IllegalArgumentException(
                "Kwota oryginalna bez kodu waluty: " + raw);
        }
        String currency = matcher.group("currency").toUpperCase(Locale.ROOT);
        // liczba miejsc po przecinku waluty oryginalnej bywa inna niż waluty
        // konta, ale wyciąg jej nie podaje — bierzemy skalę konta i zaznaczamy
        // to jako świadome przybliżenie w podglądzie
        long amountMinor = amountParser.parse(matcher.group("amount"), minorUnit);
        return new OriginalAmount(amountMinor, currency);
    }

    /**
     * Kod waluty doklejony do kwoty („-45,00 PLN"). Gdy go nie ma, walutę
     * wiersza ustala konto — parser nie zgaduje.
     */
    private String currencyOf(String amountCell) {

        Matcher matcher = ORIGINAL_WITH_CURRENCY.matcher(amountCell.trim());
        if (!matcher.matches()) {

            return null;
        }
        return matcher.group("currency").toUpperCase(Locale.ROOT);
    }

    private LocalDate parseDateOrNull(String raw) {

        if (Objects.isNull(raw) || raw.isBlank()) {

            return null;
        }
        String trimmed = raw.trim();
        return DATE_FORMATS.stream()
            .map(format -> tryParse(trimmed, format))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private LocalDate tryParse(String raw, DateTimeFormatter format) {

        try {

            return LocalDate.parse(raw, format);
        } catch (DateTimeParseException ex) {

            return null;
        }
    }

    private String cell(CSVRecord record, Integer index) {

        if (Objects.isNull(index) || index >= record.size()) {

            return null;
        }
        return record.get(index);
    }

    private String trimToNull(String raw) {

        if (Objects.isNull(raw) || raw.isBlank()) {

            return null;
        }
        return raw.trim();
    }

    private List<String> normalizedCells(CSVRecord record) {

        List<String> cells = new ArrayList<>(record.size());
        record.forEach(cell -> cells.add(normalize(cell)));
        return cells;
    }

    /** Etykiety w pliku bywają z krzyżykiem i w różnej wielkości liter. */
    private String normalize(String cell) {

        if (Objects.isNull(cell)) {

            return "";
        }
        String withoutHash = cell.trim();
        if (withoutHash.startsWith("#")) {

            withoutHash = withoutHash.substring(1);
        }
        return withoutHash.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(List<String> cells, List<String> candidates) {

        return indexOfAny(cells, candidates) >= 0;
    }

    private int indexOfAny(List<String> cells, List<String> candidates) {

        return IntStream.range(0, cells.size())
            .filter(index -> candidates.contains(cells.get(index)))
            .findFirst()
            .orElse(-1);
    }

    private record OriginalAmount(Long amountMinor, String currency) {

    }

    private record StatementMetadata(
            LocalDate periodFrom,
            LocalDate periodTo,
            Long openingBalanceMinor,
            Long closingBalanceMinor) {

    }
}
