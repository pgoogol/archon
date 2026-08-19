/**
 * Warstwa wejściowa HTTP: kontrolery REST, DTO żądań i odpowiedzi, mappery
 * encja → DTO oraz jedyny w serwisie handler wyjątków.
 *
 * <p>Zależność idzie stąd w głąb domeny i nigdy odwrotnie — pilnuje tego
 * {@code ArchitectureTest}.</p>
 */
package com.pgoogol.finance.api;
