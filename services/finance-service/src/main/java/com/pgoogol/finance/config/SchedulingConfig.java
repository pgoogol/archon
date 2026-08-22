package com.pgoogol.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Włącza harmonogram zadań w tle. Jedyne takie zadanie to nocne uzupełnianie
 * terminarza rachunków cyklicznych; jego odpowiednik na żądanie
 * ({@code POST /recurring-rules/generate}) robi dokładnie to samo, więc awaria
 * przebiegu nocnego nie blokuje niczego na stałe.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
