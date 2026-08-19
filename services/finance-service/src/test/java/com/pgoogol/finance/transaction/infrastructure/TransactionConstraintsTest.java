package com.pgoogol.finance.transaction.infrastructure;

import com.pgoogol.finance.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Więzy z migracji są ostatnią linią obrony kształtu transakcji — działają nawet
 * wtedy, gdy ktoś pisze do bazy z pominięciem warstwy aplikacji. Dlatego test
 * wstawia wiersze natywnym SQL-em, celowo omijając walidację domenową.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class TransactionConstraintsTest {

    @PersistenceContext
    private EntityManager entityManager;

    private long accountId;
    private long otherAccountId;
    private long categoryId;

    @BeforeEach
    void insertReferencedRows() {

        // Testy @SpringBootTest w tym module commitują dane i dzielą bazę
        // z tą klasą. Bez czyszczenia unikalna nazwa kategorii zderza się
        // z wierszem zostawionym przez inną klasę — a Testcontainers daje
        // jeden kontener na cały przebieg, nie na klasę.
        clearFinanceTables();
        accountId = insertAccount("Bieżące", "PLN");
        otherAccountId = insertAccount("Oszczędnościowe", "PLN");
        categoryId = insertCategory("Jedzenie");
    }

    private void clearFinanceTables() {

        entityManager.createNativeQuery("""
            truncate table finance.transaction, finance.account, finance.category \
            restart identity cascade""")
            .executeUpdate();
    }

    @Test
    @DisplayName("ck_tx_shape odrzuca transfer z przypisaną kategorią")
    void insert_whenTransferHasCategory_violatesShapeConstraint() {

        assertThatThrownBy(() -> insertTransaction(
            "TRANSFER", otherAccountId, categoryId, 10_000L))
            .hasMessageContaining("ck_tx_shape");
    }

    @Test
    @DisplayName("ck_tx_shape odrzuca wydatek bez kategorii")
    void insert_whenExpenseHasNoCategory_violatesShapeConstraint() {

        assertThatThrownBy(() -> insertTransaction("EXPENSE", null, null, 10_000L))
            .hasMessageContaining("ck_tx_shape");
    }

    @Test
    @DisplayName("ck_tx_shape odrzuca transfer na to samo konto")
    void insert_whenTransferTargetsSourceAccount_violatesShapeConstraint() {

        assertThatThrownBy(() -> insertTransaction("TRANSFER", accountId, null, 10_000L))
            .hasMessageContaining("ck_tx_shape");
    }

    @Test
    @DisplayName("więz kwoty odrzuca zapis z liczbą ujemną — kierunek wynika z typu")
    void insert_whenAmountIsNegative_violatesAmountConstraint() {

        assertThatThrownBy(() -> insertTransaction("EXPENSE", null, categoryId, -10_000L))
            .hasMessageContaining("amount_minor");
    }

    @Test
    @DisplayName("poprawny wydatek z kategorią przechodzi wszystkie więzy")
    void insert_whenExpenseIsWellFormed_passesConstraints() {

        assertThatCode(() -> insertTransaction("EXPENSE", null, categoryId, 10_000L))
            .doesNotThrowAnyException();
    }

    private long insertAccount(String name, String currency) {

        return ((Number) entityManager.createNativeQuery("""
            insert into finance.account \
            (name, type, currency, opening_balance_minor, opening_balance_on) \
            values (:name, 'BANK', :currency, 0, date '2026-01-01') \
            returning id""")
            .setParameter("name", name)
            .setParameter("currency", currency)
            .getSingleResult()).longValue();
    }

    private long insertCategory(String name) {

        return ((Number) entityManager.createNativeQuery("""
            insert into finance.category (name, direction) \
            values (:name, 'EXPENSE') returning id""")
            .setParameter("name", name)
            .getSingleResult()).longValue();
    }

    private void insertTransaction(String type, Long toAccountId, Long categoryId,
                                   long amountMinor) {

        entityManager.createNativeQuery("""
            insert into finance.transaction \
            (type, booked_on, amount_minor, currency, base_amount_minor, fx_rate, fx_rate_date, \
             account_id, to_account_id, category_id) \
            values (:type, date '2026-08-19', :amount, 'PLN', :amount, 1, date '2026-08-19', \
                    :accountId, :toAccountId, :categoryId)""")
            .setParameter("type", type)
            .setParameter("amount", amountMinor)
            .setParameter("accountId", accountId)
            .setParameter("toAccountId", toAccountId)
            .setParameter("categoryId", categoryId)
            .executeUpdate();
        entityManager.flush();
    }
}
