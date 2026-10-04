/**
 * Rdzeń diagnostyki testów: rejestrator przebiegu, jego migawka i pomiar pamięci.
 *
 * <p>Pakiet zna wyłącznie JDK. Słuchacze JUnit i Springa podają mu gotowe dane:
 * nazwy klas, wyniki, czasy. Dzięki temu raport działa też w module bez
 * {@code spring-test}, a rejestrator da się testować bez uruchamiania testów.
 * Pilnuje tego test architektury modułu.</p>
 */
package com.pgoogol.testdiagnostics.core;
