package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.CallSite;
import com.pgoogol.diagnostics.core.DataAccessEventFixtures;
import com.pgoogol.diagnostics.core.OperationKind;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;
import com.pgoogol.diagnostics.core.UnitOfWorkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static com.pgoogol.diagnostics.core.report.FindingFixtures.END;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.START;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.caller;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shape;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.shapeFinding;
import static com.pgoogol.diagnostics.core.report.FindingFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FindingJsonWriterTest {

    private static final String ITEMS = "select * from order_item where order_id = ?";

    private final FindingFingerprint fingerprint = new FindingFingerprint();

    private final FindingJsonWriter writer = new FindingJsonWriter(fingerprint);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("wniosek o jednym kształcie daje jedną linię ze wszystkimi polami formatu v1")
    void write_whenShapeFinding_writesEveryFieldOnOneLine() {

        // given
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));
        UnitOfWorkSummary unit = unit("GET /orders");

        // when
        String line = writer.write(finding, unit);

        // then
        JsonNode json = mapper.readTree(line);
        String expectedFingerprint = fingerprint.of(finding, unit);
        assertAll(
            () -> assertThat(line).doesNotContain("\n"),
            () -> assertThat(json.get("schema").asString()).isEqualTo("data-diagnostics.finding/v1"),
            () -> assertThat(json.get("code").asString()).isEqualTo("N_PLUS_ONE"),
            () -> assertThat(json.get("fingerprint").asString()).isEqualTo(expectedFingerprint),
            () -> assertThat(json.get("severity").asString()).isEqualTo("WARN"),
            () -> assertThat(json.get("title").asString()).isEqualTo("Ten sam odczyt wykonany 37 razy"),
            () -> assertThat(json.get("detectedAt").asString()).isEqualTo(END.toString()),
            () -> assertThat(json.get("measured").asLong()).isEqualTo(37),
            () -> assertThat(json.get("threshold").asLong()).isEqualTo(5),
            () -> assertThat(json.has("shapes")).isFalse());
    }

    @Test
    @DisplayName("jednostka trafia do wniosku jako obiekt z nazwą, typem, śladem i czasami")
    void write_whenShapeFinding_writesUnitObject() {

        // given
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));

        // when
        String line = writer.write(finding, unit("GET /orders"));

        // then
        JsonNode unit = mapper.readTree(line).get("unit");
        assertAll(
            () -> assertThat(unit.get("id").asString()).isEqualTo("a1b2c3d4"),
            () -> assertThat(unit.get("name").asString()).isEqualTo("GET /orders"),
            () -> assertThat(unit.get("type").asString()).isEqualTo("http"),
            () -> assertThat(unit.get("traceId").asString()).isEqualTo("6e1b9f"),
            () -> assertThat(unit.get("operations").asLong()).isEqualTo(52),
            () -> assertThat(unit.get("databaseMs").asLong()).isEqualTo(612),
            () -> assertThat(unit.get("durationMs").asLong()).isEqualTo(890));
    }

    @Test
    @DisplayName("dane jedynego kształtu i miejsca wywołania leżą na najwyższym poziomie, żeby po nich filtrować")
    void write_whenShapeFinding_writesShapeFieldsAtTopLevel() {

        // given
        Finding finding = shapeFinding("N_PLUS_ONE", shape(ITEMS, caller("list", 42)));

        // when
        String line = writer.write(finding, unit("GET /orders"));

        // then
        JsonNode json = mapper.readTree(line);
        JsonNode firstCaller = json.get("callers").get(0);
        assertAll(
            () -> assertThat(json.get("store").asString()).isEqualTo("postgresql"),
            () -> assertThat(json.get("kind").asString()).isEqualTo("READ"),
            () -> assertThat(json.get("shape").asString()).isEqualTo(ITEMS),
            () -> assertThat(json.get("sample").asString()).isEqualTo(ITEMS),
            () -> assertThat(json.get("count").asLong()).isEqualTo(37),
            () -> assertThat(json.get("failures").asLong()).isZero(),
            () -> assertThat(json.get("totalMs").asLong()).isEqualTo(412),
            () -> assertThat(json.get("maxMs").asLong()).isEqualTo(30),
            () -> assertThat(firstCaller.get("class").asString()).isEqualTo("com.example.OrderService"),
            () -> assertThat(firstCaller.get("method").asString()).isEqualTo("list"),
            () -> assertThat(firstCaller.get("line").asLong()).isEqualTo(42),
            () -> assertThat(firstCaller.get("repositoryMethod").asString())
                .isEqualTo("OrderRepository.findAllByStatus"));
    }

    @Test
    @DisplayName("cudzysłowy, ukośniki, nowe linie i znaki sterujące w SQL przechodzą przez parser bez zmian")
    void write_whenSqlHasSpecialCharacters_roundTripsThroughParser() {

        // given
        String sql = "select \"Zamówienie\" from t where note = 'a\\b'\n\tand x = 'ą\u0001 '";
        Finding finding = shapeFinding("SLOW_OPERATION", shape(sql));

        // when
        String line = writer.write(finding, unit("GET /orders"));

        // then
        JsonNode json = mapper.readTree(line);
        assertAll(
            () -> assertThat(line).doesNotContain("\n", "\t", " "),
            () -> assertThat(json.get("shape").asString()).isEqualTo(sql),
            () -> assertThat(json.get("sample").asString()).isEqualTo(sql));
    }

    @Test
    @DisplayName("wniosek o kilku kształtach ma je w tablicy shapes, nie na najwyższym poziomie")
    void write_whenSeveralShapes_writesShapesArray() {

        // given
        List<ShapeSummary> shapes = List.of(shape("select 1"), shape("select 2"));
        Finding finding = new Finding("OPERATION_COUNT", Severity.CRITICAL, "Operacji: 260", 260, 50, shapes);

        // when
        String line = writer.write(finding, unit("GET /orders"));

        // then
        JsonNode json = mapper.readTree(line);
        assertAll(
            () -> assertThat(json.get("shapes")).hasSize(2),
            () -> assertThat(json.get("shapes").get(1).get("shape").asString()).isEqualTo("select 2"),
            () -> assertThat(json.has("shape")).isFalse());
    }

    @Test
    @DisplayName("pola bez wartości znikają z linii: ślad, przykład w prod, metoda repozytorium")
    void write_whenValuesMissing_omitsFields() {

        // given
        UnitOfWorkSummary withoutTrace = new UnitOfWorkSummary("a1b2c3d4", "nightly", UnitOfWorkType.SCHEDULED,
            null, 1, Duration.ofMillis(2), START, END);
        CallSite direct = new CallSite("com.example.ReportJob", "run", 12, null);
        ShapeSummary prodShape = new ShapeSummary(DataAccessEventFixtures.POSTGRESQL, OperationKind.READ, ITEMS,
            false, null, 1, 0, Duration.ofMillis(900), Duration.ofMillis(900), List.of(direct));
        Finding finding = shapeFinding("SLOW_OPERATION", prodShape);

        // when
        String line = writer.write(finding, withoutTrace);

        // then
        JsonNode json = mapper.readTree(line);
        assertAll(
            () -> assertThat(json.get("unit").has("traceId")).isFalse(),
            () -> assertThat(json.has("sample")).isFalse(),
            () -> assertThat(json.get("callers").get(0).has("repositoryMethod")).isFalse());
    }
}
