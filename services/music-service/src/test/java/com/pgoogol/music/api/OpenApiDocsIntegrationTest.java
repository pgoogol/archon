package com.pgoogol.music.api;

import com.pgoogol.music.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DoD M1.7: wszystkie endpointy Etapu 1 widoczne w definicji OpenAPI
 * (wywoływalne ze Swagger UI pod /swagger-ui.html).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_whenFetched_containStageOneEndpoints() throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("music-view API"))
            .andExpect(jsonPath("$.paths['/music/api/v1/ingest/file'].post").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/ingest/metrics'].post").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/catalog/tracks'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/catalog/tracks/{spotifyId}'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/catalog/tracks/{spotifyId}/metrics'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/library/tracks'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/library/tracks'].post").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/library/tracks/{spotifyId}'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/library/tracks/{spotifyId}'].patch").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/library/tracks/{spotifyId}'].delete").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/enrich'].post").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/enrich/jobs'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/enrich/jobs/{executionId}'].get").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/enrich/jobs/{executionId}/restart'].post").exists())
            .andExpect(jsonPath("$.paths['/music/api/v1/enrich/missing-count'].get").exists());
    }

    @Test
    void swaggerUi_whenOpened_isAvailable() throws Exception {

        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }
}
