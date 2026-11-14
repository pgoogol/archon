package com.pgoogol.diagnostics.it;

import org.springframework.data.repository.CrudRepository;

/** Repozytorium Spring Data JDBC. */
public interface NoteRepository extends CrudRepository<Note, Long> {

}
