package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Kontrakt z {@code contracts/openapi/kitchen.yaml} jest źródłem prawdy — ten test
 * pilnuje, żeby kod od niego nie odjechał.
 *
 * <p>Porównanie idzie po powierzchni API na czterech poziomach: operacje, nazwy
 * parametrów, nazwy pól schematów i wartości enumów. Co dokładnie porównujemy
 * i dlaczego akurat tyle — patrz {@link ApiSurface}.</p>
 *
 * <p>Test nie „naprawia się" nadpisaniem pliku kontraktu treścią wygenerowaną
 * z kodu: przy rozjeździe trzeba rozstrzygnąć, która strona jest błędna, i to
 * kontrakt zmienia się pierwszy.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "..", "contracts", "openapi", "kitchen.yaml");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("kod wystawia dokładnie te operacje, które opisuje kontrakt")
    void generatedSpec_exposesExactlyTheOperationsFromContract() throws Exception {

        // given
        ApiSurface fromContract = contractSurface();

        // when
        ApiSurface fromCode = generatedSurface();

        // then
        assertThat(fromCode.operations())
            .as("operacje w kodzie kontra kontrakt — zmiana API zaczyna się od contracts/openapi/")
            .containsExactlyInAnyOrderElementsOf(fromContract.operations());
    }

    @Test
    @DisplayName("każda operacja bierze parametry opisane w kontrakcie")
    void generatedSpec_matchesContractParameterNames() throws Exception {

        // given
        ApiSurface fromContract = contractSurface();

        // when
        ApiSurface fromCode = generatedSurface();

        // then
        assertThat(fromCode.parameters())
            .as("nazwy parametrów per operacja")
            .containsExactlyInAnyOrderEntriesOf(fromContract.parameters());
    }

    @Test
    @DisplayName("każdy schemat ma dokładnie te pola, które opisuje kontrakt")
    void generatedSpec_matchesContractSchemaFields() throws Exception {

        // given
        ApiSurface fromContract = contractSurface();

        // when
        ApiSurface fromCode = generatedSurface();

        // then
        assertThat(fromCode.schemaFields())
            .as("pola schematów — nowe pole w kodzie musi wejść do contracts/openapi/")
            .containsExactlyInAnyOrderEntriesOf(fromContract.schemaFields());
    }

    @Test
    @DisplayName("każdy enum ma dokładnie te wartości, które opisuje kontrakt")
    void generatedSpec_matchesContractEnumValues() throws Exception {

        // given
        ApiSurface fromContract = contractSurface();

        // when
        ApiSurface fromCode = generatedSurface();

        // then
        assertThat(fromCode.enumValues())
            .as("wartości enumów — nowa stała w kodzie musi wejść do contracts/openapi/")
            .containsExactlyInAnyOrderElementsOf(fromContract.enumValues());
    }

    private ApiSurface contractSurface() throws Exception {

        assertThat(CONTRACT).as("plik kontraktu").exists();
        ContractSurface contract = new ContractSurface(CONTRACT);
        return contract.read();
    }

    private ApiSurface generatedSurface() throws Exception {

        String body = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString();
        JsonNode spec = objectMapper.readTree(body);
        GeneratedSurface generated = new GeneratedSurface(spec);
        return generated.read();
    }
}
