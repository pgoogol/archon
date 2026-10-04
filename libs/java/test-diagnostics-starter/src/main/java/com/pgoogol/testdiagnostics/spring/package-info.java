/**
 * Słuchacz Spring TestContext: wykrywa, kiedy klasa testów uruchamia nowe środowisko
 * (kontekst Springa), mierzy jego start i wznowienia wstrzymanych kontekstów.
 *
 * <p>Spring ładuje słuchacza przez {@code META-INF/spring.factories}, więc działa
 * tylko w modułach, które same mają {@code spring-test}; zależność startera od Springa
 * jest opcjonalna.</p>
 */
package com.pgoogol.testdiagnostics.spring;
