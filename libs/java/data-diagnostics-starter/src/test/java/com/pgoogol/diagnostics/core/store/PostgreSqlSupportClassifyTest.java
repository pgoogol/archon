package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.OperationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Rodzaj operacji: odczyt, zapis albo inna, także dla CTE zmieniających dane. */
class PostgreSqlSupportClassifyTest {

    private final PostgreSqlSupport support = new PostgreSqlSupport();

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
