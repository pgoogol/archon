package com.pgoogol.finance.imports.parser;

import com.pgoogol.finance.imports.statement.AmountParser;
import com.pgoogol.finance.imports.statement.ParsedStatement;
import com.pgoogol.finance.imports.statement.RawRow;
import com.pgoogol.finance.imports.statement.SourceFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wyciąg budowany w teście, nie wczytywany z pliku — kodowanie jest tu jedną
 * z testowanych rzeczy, więc musi być widoczne w kodzie, a nie ukryte
 * w binariach repozytorium.
 */
class MbankCsvStatementParserTest {

    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");

    /** Spacja niełamliwa w kwocie — dokładnie to, co wychodzi z eksportu. */
    private static final String WYCIAG = """
        mBank S.A. Bankowość Detaliczna;;;;;;
        Lista operacji;;;;;;
        ;;;;;;
        #Za okres:;2026-01-01;2026-01-31;;;;
        #Waluta;PLN;;;;;
        #Saldo początkowe;1 234,56;;;;;
        ;;;;;;
        #Data operacji;#Opis operacji;#Kontrahent;#Numer referencyjny;#Kwota;\
        #Kwota oryginalna;#Saldo po operacji
        2026-01-05;"ZAKUP PRZY UŻYCIU KARTY";"Żabka Kraków";"REF-001";-45,00;;1 189,56
        2026-01-07;"PŁATNOŚĆ KARTĄ";"Amazon EU";"REF-002";-1 234,56;-289,90 EUR;-44,99
        2026-01-10;"PRZELEW PRZYCHODZĄCY";"Pracodawca";"REF-003";+5 000,00;;4 955,01
        ;;;;;;
        #Saldo końcowe;4 955,01;;;;;
        """;

    private final MbankCsvStatementParser parser =
        new MbankCsvStatementParser(new AmountParser());

    @Test
    @DisplayName("supports gdy plik ma nagłówek tabeli operacji, rozpoznaje format")
    void supports_whenFileHasOperationsHeader_recognisesFormat() {

        // when & then
        assertThat(parser.supports(wyciag())).isTrue();
    }

    @Test
    @DisplayName("supports gdy plik jest z innego banku, nie rozpoznaje formatu")
    void supports_whenFileIsFromAnotherBank_doesNotRecogniseFormat() {

        // given
        SourceFile obcy = new SourceFile("inny.csv",
            "Date,Description,Amount\n2026-01-05,Coffee,-45.00\n".getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThat(parser.supports(obcy)).isFalse();
    }

    @Test
    @DisplayName("parse czyta okres i salda z nagłówka oraz stopki poza tabelą")
    void parse_readsPeriodAndBalancesFromHeaderAndFooter() {

        // when
        ParsedStatement statement = parser.parse(wyciag(), 2);

        // then
        assertThat(statement.periodFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(statement.periodTo()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(statement.openingBalanceMinor()).isEqualTo(123456L);
        assertThat(statement.closingBalanceMinor()).isEqualTo(495501L);
    }

    @Test
    @DisplayName("parse gdy plik jest w windows-1250, zachowuje polskie znaki")
    void parse_whenFileIsWindows1250_keepsPolishCharacters() {

        // when
        ParsedStatement statement = parser.parse(wyciag(), 2);

        // then
        RawRow pierwszy = statement.rows().getFirst();
        assertThat(pierwszy.description()).isEqualTo("ZAKUP PRZY UŻYCIU KARTY");
        assertThat(pierwszy.counterparty()).isEqualTo("Żabka Kraków");
    }

    @Test
    @DisplayName("parse czyta wyłącznie wiersze tabeli, pomijając nagłówek i stopkę")
    void parse_readsOnlyTableRows_skippingHeaderAndFooter() {

        // when
        ParsedStatement statement = parser.parse(wyciag(), 2);

        // then
        assertThat(statement.rows()).hasSize(3);
    }

    @Test
    @DisplayName("parse zachowuje znak kwoty i czyta separator tysięcy ze spacją niełamliwą")
    void parse_keepsAmountSignAndReadsNonBreakingThousandsSeparator() {

        // when
        List<RawRow> rows = parser.parse(wyciag(), 2).rows();

        // then
        assertThat(rows.get(0).amountMinor()).isEqualTo(-4500L);
        assertThat(rows.get(1).amountMinor()).isEqualTo(-123456L);
        assertThat(rows.get(2).amountMinor()).isEqualTo(500000L);
    }

    @Test
    @DisplayName("parse dla transakcji kartowej czyta obie kwoty i walutę oryginalną")
    void parse_forCardTransaction_readsBothAmountsAndOriginalCurrency() {

        // when
        RawRow kartowa = parser.parse(wyciag(), 2).rows().get(1);

        // then
        assertThat(kartowa.amountMinor()).isEqualTo(-123456L);
        assertThat(kartowa.originalAmountMinor()).isEqualTo(-28990L);
        assertThat(kartowa.originalCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("parse numeruje wiersze od zera w kolejności z pliku")
    void parse_numbersRowsFromZeroInFileOrder() {

        // when
        List<RawRow> rows = parser.parse(wyciag(), 2).rows();

        // then
        assertThat(rows).extracting(RawRow::ordinal).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("parse czyta referencję bankową, gdy plik ją podaje")
    void parse_readsBankReference_whenFileProvidesIt() {

        // when
        List<RawRow> rows = parser.parse(wyciag(), 2).rows();

        // then
        assertThat(rows).extracting(RawRow::bankReference)
            .containsExactly("REF-001", "REF-002", "REF-003");
    }

    @Test
    @DisplayName("parse gdy plik nie ma nagłówka tabeli, wywala się zamiast zwrócić pustkę")
    void parse_whenFileHasNoTableHeader_throwsInsteadOfReturningNothing() {

        // given
        SourceFile bezTabeli = new SourceFile("puste.csv",
            "#Saldo początkowe;100,00\n".getBytes(WINDOWS_1250));

        // when & then
        assertThatThrownBy(() -> parser.parse(bezTabeli, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nagłówka");
    }

    @Test
    @DisplayName("parse gdy kwota oryginalna nie ma kodu waluty, wywala się")
    void parse_whenOriginalAmountLacksCurrencyCode_throws() {

        // given
        String zepsuty = """
            #Data operacji;#Opis operacji;#Kwota;#Kwota oryginalna
            2026-01-07;"PŁATNOŚĆ";-100,00;-25,00
            """;
        SourceFile file = new SourceFile("zepsuty.csv", zepsuty.getBytes(WINDOWS_1250));

        // when & then
        assertThatThrownBy(() -> parser.parse(file, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kodu waluty");
    }

    @Test
    @DisplayName("parse gdy waluta nie ma części ułamkowej, czyta kwoty bez groszy")
    void parse_whenCurrencyHasNoFraction_readsAmountsWithoutMinorPart() {

        // given: JPY ma minor_unit = 0
        String jenowy = """
            #Data operacji;#Opis operacji;#Kwota
            2026-01-07;"SUSHI";-1200
            """;
        SourceFile file = new SourceFile("jpy.csv", jenowy.getBytes(WINDOWS_1250));

        // when
        RawRow row = parser.parse(file, 0).rows().getFirst();

        // then
        assertThat(row.amountMinor()).isEqualTo(-1200L);
    }

    @Test
    @DisplayName("parse gdy data jest w formacie z kropkami, i tak ją czyta")
    void parse_whenDateUsesDots_stillReadsIt() {

        // given: nie każdy eksport używa ISO
        String zKropkami = """
            #Data operacji;#Opis operacji;#Kwota
            07.01.2026;"SKLEP";-100,00
            07-01-2026;"SKLEP";-200,00
            """;
        SourceFile file = new SourceFile("kropki.csv", zKropkami.getBytes(WINDOWS_1250));

        // when
        List<RawRow> rows = parser.parse(file, 2).rows();

        // then
        assertThat(rows).extracting(RawRow::bookedOn)
            .containsOnly(LocalDate.of(2026, 1, 7));
    }

    @Test
    @DisplayName("parse gdy brakuje kolumn opcjonalnych, zostawia je puste")
    void parse_whenOptionalColumnsAreMissing_leavesThemEmpty() {

        // given: minimalny plik — sama data, opis i kwota
        String minimalny = """
            #Data operacji;#Opis operacji;#Kwota
            2026-01-07;"SKLEP";-100,00
            """;
        SourceFile file = new SourceFile("minimalny.csv", minimalny.getBytes(WINDOWS_1250));

        // when
        RawRow row = parser.parse(file, 2).rows().getFirst();

        // then
        assertThat(row.counterparty()).isNull();
        assertThat(row.bankReference()).isNull();
        assertThat(row.originalAmountMinor()).isNull();
    }

    @Test
    @DisplayName("parse gdy brakuje nagłówka z okresem i saldami, zostawia je puste")
    void parse_whenPeriodAndBalanceHeadersAreMissing_leavesThemEmpty() {

        // given
        String bezNaglowka = """
            #Data operacji;#Opis operacji;#Kwota
            2026-01-07;"SKLEP";-100,00
            """;
        SourceFile file = new SourceFile("bez.csv", bezNaglowka.getBytes(WINDOWS_1250));

        // when
        ParsedStatement statement = parser.parse(file, 2);

        // then
        assertThat(statement.periodFrom()).isNull();
        assertThat(statement.periodTo()).isNull();
        assertThat(statement.openingBalanceMinor()).isNull();
        assertThat(statement.closingBalanceMinor()).isNull();
    }

    @Test
    @DisplayName("parse pomija wiersze tabeli bez daty")
    void parse_skipsTableRowsWithoutDate() {

        // given: podsumowanie wewnątrz tabeli nie jest operacją
        String zPodsumowaniem = """
            #Data operacji;#Opis operacji;#Kwota
            2026-01-07;"SKLEP";-100,00
            ;"RAZEM";-100,00
            """;
        SourceFile file = new SourceFile("podsumowanie.csv", zPodsumowaniem.getBytes(WINDOWS_1250));

        // when
        ParsedStatement statement = parser.parse(file, 2);

        // then
        assertThat(statement.rows()).hasSize(1);
    }

    @Test
    @DisplayName("parse gdy kwota niesie kod waluty, czyta go jako walutę wiersza")
    void parse_whenAmountCarriesCurrencyCode_readsItAsRowCurrency() {

        // given: wyciąg konta walutowego potrafi dokleić kod do każdej kwoty
        String zWaluta = """
            #Data operacji;#Opis operacji;#Kwota
            2026-01-07;"SKLEP";-100,00 EUR
            """;
        SourceFile file = new SourceFile("waluta.csv", zWaluta.getBytes(WINDOWS_1250));

        // when
        RawRow row = parser.parse(file, 2).rows().getFirst();

        // then
        assertThat(row.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("parse gdy kolumna kwoty oryginalnej jest pusta, nie czyta z niej nic")
    void parse_whenOriginalAmountColumnIsBlank_readsNothingFromIt() {

        // given: kolumna jest, ale w tym wierszu stoją w niej same spacje
        String zPusta = """
            #Data operacji;#Opis operacji;#Kwota;#Kwota oryginalna
            2026-01-07;"SKLEP";-100,00;"   "
            """;
        SourceFile file = new SourceFile("pusta.csv", zPusta.getBytes(WINDOWS_1250));

        // when
        RawRow row = parser.parse(file, 2).rows().getFirst();

        // then
        assertThat(row.originalAmountMinor()).isNull();
    }

    @Test
    @DisplayName("parse gdy wiersz jest krótszy niż nagłówek, nie wywala się na brakującej kolumnie")
    void parse_whenRowIsShorterThanHeader_doesNotFailOnMissingColumn() {

        // given: bank potrafi uciąć końcowe puste kolumny
        String krotki = """
            #Data operacji;#Opis operacji;#Kwota;#Kwota oryginalna
            2026-01-07;"SKLEP";-100,00
            """;
        SourceFile file = new SourceFile("krotki.csv", krotki.getBytes(WINDOWS_1250));

        // when
        RawRow row = parser.parse(file, 2).rows().getFirst();

        // then
        assertThat(row.amountMinor()).isEqualTo(-10000L);
        assertThat(row.originalAmountMinor()).isNull();
    }

    @Test
    @DisplayName("supports gdy plik ma kolumnę daty, ale nie ma kwoty, nie rozpoznaje formatu")
    void supports_whenFileHasDateColumnButNoAmount_doesNotRecogniseFormat() {

        // given: sam nagłówek daty to za mało — to mógłby być dowolny raport
        String bezKwoty = """
            #Data operacji;#Opis operacji
            2026-01-07;"SKLEP"
            """;
        SourceFile file = new SourceFile("bez-kwoty.csv", bezKwoty.getBytes(WINDOWS_1250));

        // when & then
        assertThat(parser.supports(file)).isFalse();
    }

    private SourceFile wyciag() {

        return new SourceFile("lista_operacji.csv", WYCIAG.getBytes(WINDOWS_1250));
    }
}
