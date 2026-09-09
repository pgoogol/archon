package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.application.DictionaryService;
import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Słowniki są pierwszym wywołaniem, które front wykonuje po wejściu na domenę —
 * pusty słownik ma dać puste listy i status 200, a nie błąd.
 */
@WebMvcTest(DictionaryController.class)
class DictionaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DictionaryService dictionaryService;

    @Test
    @DisplayName("dictionaries_whenNoTerms_returnsEmptyListForEveryDictionary")
    void dictionaries_whenNoTerms_returnsEmptyListForEveryDictionary() throws Exception {

        // given
        given(dictionaryService.all()).willReturn(Dictionaries.empty());

        // when & then
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
