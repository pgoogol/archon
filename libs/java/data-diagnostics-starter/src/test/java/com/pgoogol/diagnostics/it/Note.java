package com.pgoogol.diagnostics.it;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Notatka testowej aplikacji, obsługiwana przez Spring Data JDBC, nie przez JPA.
 *
 * @param id   klucz
 * @param body treść
 */
@Table("note")
public record Note(@Id Long id, String body) {

}
