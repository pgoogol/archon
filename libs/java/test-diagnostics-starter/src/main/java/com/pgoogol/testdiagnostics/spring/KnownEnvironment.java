package com.pgoogol.testdiagnostics.spring;

/** Środowisko uruchomione wcześniej w przebiegu: numer z raportu i opis konfiguracji. */
record KnownEnvironment(int number, ContextDescription description) {
}
