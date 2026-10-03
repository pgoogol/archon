package com.pgoogol.diagnostics.core.store;

import com.pgoogol.diagnostics.core.DataStore;
import com.pgoogol.diagnostics.core.OperationKind;

import java.util.List;
import java.util.Objects;

/**
 * Strategia bazy dla PostgreSQL.
 *
 * <p>Kształt powstaje w dwóch fazach: lekser usuwa komentarze i zamienia literały oraz
 * parametry na {@code ?}, potem listy {@code IN (…)} i wiersze {@code VALUES (…), (…)}
 * zwijają się do jednego elementu. Białe znaki zwijają się do jednej spacji; wielkość
 * liter, identyfikatory i rzutowania {@code ::typ} zostają, bo są częścią kształtu.</p>
 *
 * <p>Bezstanowa, bezpieczna dla wielu wątków.</p>
 */
public class PostgreSqlSupport implements DataStoreSupport {

    public static final DataStore POSTGRESQL = new DataStore("postgresql");

    private final PostgreSqlLexer lexer = new PostgreSqlLexer();

    private final RepeatedListCollapser collapser = new RepeatedListCollapser();

    private final PostgreSqlStatementClassifier classifier = new PostgreSqlStatementClassifier();

    @Override
    public DataStore store() {

        return POSTGRESQL;
    }

    @Override
    public String shape(String statement) {

        Objects.requireNonNull(statement, "tekst zapytania jest wymagany");
        List<SqlToken> tokens = lexer.tokenize(statement);
        List<SqlToken> collapsed = collapser.collapse(tokens);
        return render(collapsed);
    }

    @Override
    public OperationKind classify(String statement) {

        Objects.requireNonNull(statement, "tekst zapytania jest wymagany");
        List<SqlToken> tokens = lexer.tokenize(statement);
        return classifier.classify(tokens);
    }

    private String render(List<SqlToken> tokens) {

        StringBuilder shape = new StringBuilder();
        tokens.forEach(token -> append(shape, token));
        return shape.toString();
    }

    private void append(StringBuilder shape, SqlToken token) {

        if (token.spaceBefore()) {

            shape.append(' ');
        }
        shape.append(token.text());
    }
}
