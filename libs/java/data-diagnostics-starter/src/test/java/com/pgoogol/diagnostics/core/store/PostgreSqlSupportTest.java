package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.OperationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class PostgreSqlSupportTest {

    private final PostgreSqlSupport support = new PostgreSqlSupport();

    @Test
    @DisplayName("literał tekstowy, także z podwojonym apostrofem, zamienia się na ?")
    void shape_whenStringLiteral_replacesWithPlaceholder() {

        // given
        String statement = "select * from customer where name = 'O''Brien'";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from customer where name = ?");
    }

    @Test
    @DisplayName("literał E'…' kończy się dopiero na apostrofie bez ukośnika przed nim")
    void shape_whenEscapeStringLiteral_replacesWholeLiteral() {

        // given
        String statement = "select * from note where body = E'it\\'s' and id = 7";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from note where body = ? and id = ?");
    }

    @Test
    @DisplayName("literały bitowe i szesnastkowe zamieniają się na ? razem z przedrostkiem")
    void shape_whenBitStringLiterals_replacesWithPrefix() {

        // given
        String statement = "select * from flags where bits = B'0101' or mask = X'1F'";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from flags where bits = ? or mask = ?");
    }

    @Test
    @DisplayName("literał w dolarach, także z etykietą i apostrofami w środku, zamienia się na ?")
    void shape_whenDollarQuotedLiterals_replacesWholeLiteral() {

        // given
        String statement = "select $$it's$$, $body$ a $$ b $body$ from dual";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select ?, ? from dual");
    }

    @Test
    @DisplayName("liczby całkowite, dziesiętne i z wykładnikiem zamieniają się na ?")
    void shape_whenNumericLiterals_replacesEach() {

        // given
        String statement = "select * from item where a = 42 and b > 3.14 and c < .5 and d = 1e-3";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from item where a = ? and b > ? and c < ? and d = ?");
    }

    @Test
    @DisplayName("parametry pozycyjne $1, $2 wyglądają w kształcie jak ? z JDBC")
    void shape_whenPositionalParameters_replacesWithPlaceholder() {

        // given
        String statement = "select * from orders where id = $1 and status = $2";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from orders where id = ? and status = ?");
    }

    @Test
    @DisplayName("lista IN z samych wartości zwija się do jednego elementu")
    void shape_whenInListOfValues_collapsesToOneElement() {

        // given
        String statement = "select * from orders where id in (1, 2, 3) or ref IN (?,?)";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from orders where id in (?) or ref IN (?)");
    }

    @Test
    @DisplayName("listy IN różnej długości dają ten sam kształt, więc analiza widzi jedno zapytanie")
    void shape_whenInListsDifferInLength_givesSameShape() {

        // given
        String shorter = "select * from orders where id in (?, ?)";
        String longer = "select * from orders where id in (?, ?, ?, ?, ?)";

        // when
        String shorterShape = support.shape(shorter);
        String longerShape = support.shape(longer);

        // then
        assertThat(shorterShape).isEqualTo(longerShape);
    }

    @Test
    @DisplayName("lista IN z różnymi kolumnami zostaje bez zmian")
    void shape_whenInListHasDifferentItems_keepsList() {

        // given
        String statement = "select * from orders where ? in (billing_city, shipping_city)";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo(statement);
    }

    @Test
    @DisplayName("podzapytanie w IN zostaje, a lista w jego środku się zwija")
    void shape_whenInHasSubquery_collapsesOnlyNestedList() {

        // given
        String statement = "select * from orders where id in (select order_id from item where sku in (1, 2))";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from orders where id in (select order_id from item where sku in (?))");
    }

    @Test
    @DisplayName("wielowierszowe VALUES zwija się do jednego wiersza")
    void shape_whenMultiRowValues_collapsesToOneRow() {

        // given
        String statement = "insert into item (order_id, sku) values (1, 'a'), (1, 'b'), (2, 'c')";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("insert into item (order_id, sku) values (?, ?)");
    }

    @Test
    @DisplayName("= ANY(?) z tablicą w parametrze zostaje bez zmian")
    void shape_whenAnyWithArrayParameter_keepsAsIs() {

        // given
        String statement = "select * from orders where id = ANY($1)";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from orders where id = ANY(?)");
    }

    @Test
    @DisplayName("komentarze blokowe, także zagnieżdżone, i liniowe znikają z kształtu")
    void shape_whenComments_removesThem() {

        // given
        String statement = """
            /* outer /* inner */ still outer */ select a, -- koniec linii
            b/*x*/from t""";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select a, b from t");
    }

    @Test
    @DisplayName("ciągi białych znaków zwijają się do jednej spacji")
    void shape_whenWhitespaceRuns_collapsesToSingleSpace() {

        // given
        String statement = "  select\n\t a ,  b\r\n from   t  ";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select a , b from t");
    }

    @Test
    @DisplayName("rzutowanie ::typ zostaje w kształcie, znika tylko wartość")
    void shape_whenCast_keepsType() {

        // given
        String statement = "select * from orders where created > '2026-01-01'::date and id = $1::bigint";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from orders where created > ?::date and id = ?::bigint");
    }

    @Test
    @DisplayName("identyfikator w cudzysłowie przechodzi bez zmian, nawet z -- albo apostrofem w nazwie")
    void shape_whenQuotedIdentifier_keepsItVerbatim() {

        // given
        String statement = "select \"Order--Id\", \"it's\" from \"My Table\"";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo(statement);
    }

    @Test
    @DisplayName("zapytanie Hibernate zachowuje aliasy o1_0, a zwija tylko listę IN")
    void shape_whenHibernateSelect_keepsAliases() {

        // given
        String statement = """
            select o1_0.id,o1_0.status from orders o1_0 \
            where o1_0.customer_id=? and o1_0.status in (?,?,?)""";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("""
            select o1_0.id,o1_0.status from orders o1_0 \
            where o1_0.customer_id=? and o1_0.status in (?)""");
    }

    @Test
    @DisplayName("insert Hibernate z parametrami JDBC nie zmienia się")
    void shape_whenHibernateInsert_keepsStatement() {

        // given
        String statement = "insert into order_item (order_id,product,quantity,id) values (?,?,?,?)";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo(statement);
    }

    @Test
    @DisplayName("niedomknięty literał nie przerywa liczenia kształtu")
    void shape_whenLiteralUnterminated_masksRestOfStatement() {

        // given
        String statement = "select * from note where body = 'unterminated";

        // when
        String shape = support.shape(statement);

        // then
        assertThat(shape).isEqualTo("select * from note where body = ?");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "'", "E'", "E'\\", "$", "$$", "$a", "$a$ never closed", "$1", "\"", "/*", "/* /* */", "--",
        ".", "1e", "1e-", "(", ")", "in", "in (", "in ((", "values", "values (", "values (?), (", "x in (?, ?"})
    @DisplayName("ucięty albo dziwny tekst daje kształt, nigdy wyjątek")
    void shape_whenStatementTruncated_doesNotFail(String statement) {

        // when & then
        assertThatNoException().isThrownBy(() -> support.shape(statement));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        select * from orders                                                     | READ
        (select 1) union (select 2)                                              | READ
        SHOW search_path                                                         | READ
        /* raport */ select count(*) from orders                                 | READ
        select * from orders where id = ? for update                             | READ
        with recent as (select * from orders) select * from recent               | READ
        WITH RECURSIVE t(n) AS (VALUES (1) UNION ALL SELECT n + 1 FROM t) SELECT n FROM t | READ
        insert into orders (id) values (?)                                       | WRITE
        UPDATE orders SET status = ? WHERE id = ?                                | WRITE
        delete from orders where id = ?                                          | WRITE
        merge into orders o using incoming i on o.id = i.id when matched then do nothing | WRITE
        with stale as (select id from orders) delete from orders where id in (select id from stale) | WRITE
        with gone as (delete from orders returning *) select * from gone         | WRITE
        create table archive (id bigint)                                         | OTHER
        set search_path = archive                                                | OTHER
        call refresh_totals()                                                    | OTHER
        begin                                                                    | OTHER
        """)
    @DisplayName("rodzaj operacji wynika z głównego polecenia i z CTE, które zmieniają dane")
    void classify_whenStatementGiven_returnsKind(String statement, OperationKind expected) {

        // when
        OperationKind kind = support.classify(statement);

        // then
        assertThat(kind).isEqualTo(expected);
    }

    @Test
    @DisplayName("pusty tekst to operacja innego rodzaju, nie błąd")
    void classify_whenBlank_returnsOther() {

        // when
        OperationKind kind = support.classify("  ");

        // then
        assertThat(kind).isEqualTo(OperationKind.OTHER);
    }
}
