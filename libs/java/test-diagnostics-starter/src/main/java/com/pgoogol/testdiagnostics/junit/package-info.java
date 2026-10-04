/**
 * Słuchacze JUnit Platform: sesja testów otwiera i raportuje przebieg, a wykonanie
 * zapisuje klasy, testy i wyniki.
 *
 * <p>JUnit ładuje oba słuchacze przez {@code META-INF/services}, więc starter działa
 * po samym dodaniu zależności. Pakiet nie zna Springa: moduł bez {@code spring-test}
 * też dostaje raport.</p>
 */
package com.pgoogol.testdiagnostics.junit;
