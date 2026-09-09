/**
 * Rachunek na dzienniku zmian: porównanie dwóch stanów przepisu i typy, którymi
 * opisujemy różnicę.
 *
 * <p>Pakiet celowo nie zna Springa, JPA ani Jacksona — cofanie zmian i liczenie
 * różnic sprawdza się przez podanie dwóch stanów, a nie przez postawienie
 * kontekstu i zapisanie czegokolwiek. Pilnuje tego ArchitectureTest.</p>
 */
package com.pgoogol.kitchen.revision.diff;
