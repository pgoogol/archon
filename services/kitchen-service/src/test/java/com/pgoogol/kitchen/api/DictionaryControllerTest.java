package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.application.DictionaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Słowniki są pierwszym wywołaniem, które front wykonuje po wejściu na domenę —
 * dopóki tabele słownikowe nie powstaną, ma dostać puste listy i status 200,
 * a nie błąd.
 */
@WebMvcTest(DictionaryController.class)
@Import(DictionaryService.class)
class DictionaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("słowniki wracają kompletem pustych list, nie błędem")
    void dictionaries_returnEmptyListsForEveryDictionary() throws Exception {

        mockMvc.perform(get("/kitchen/api/v1/dictionaries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.units").isEmpty())
            .andExpect(jsonPath("$.cuisines").isEmpty())
            .andExpect(jsonPath("$.categories").isEmpty())
            .andExpect(jsonPath("$.diets").isEmpty())
            .andExpect(jsonPath("$.tags").isEmpty())
            .andExpect(jsonPath("$.equipment").isEmpty());
    }
}
