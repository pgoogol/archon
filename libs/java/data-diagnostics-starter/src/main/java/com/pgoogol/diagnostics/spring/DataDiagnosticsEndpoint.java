package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.report.FindingChatExporter;
import com.pgoogol.diagnostics.core.report.FindingJsonWriter;
import com.pgoogol.diagnostics.core.report.FindingQuery;
import com.pgoogol.diagnostics.core.report.RecentFindings;
import com.pgoogol.diagnostics.core.report.ReportedFinding;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Endpoint actuatora z ostatnimi wnioskami ({@code /actuator/datadiagnostics}).
 * Podlega zwykłym regułom wystawiania, więc jest niedostępny, dopóki aplikacja nie doda
 * go do {@code management.endpoints.web.exposure.include}.
 *
 * <p>Bez selektora zwraca tablicę JSON w tym samym formacie co plik JSONL
 * ({@code data-diagnostics.finding/v1}). Z selektorem {@code chat} zwraca te same wnioski
 * jako tekst do wklejenia. Oba warianty filtrują po {@code code}, {@code fingerprint}
 * i {@code unit}.</p>
 */
@Endpoint(id = "datadiagnostics")
public class DataDiagnosticsEndpoint {

    static final String CHAT_FORMAT = "chat";

    private final RecentFindings recent;

    private final FindingJsonWriter jsonWriter;

    private final FindingChatExporter chatExporter;

    public DataDiagnosticsEndpoint(RecentFindings recent, FindingJsonWriter jsonWriter,
                                   FindingChatExporter chatExporter) {

        this.recent = Objects.requireNonNull(recent, "bufor wniosków jest wymagany");
        this.jsonWriter = Objects.requireNonNull(jsonWriter, "zapis JSON jest wymagany");
        this.chatExporter = Objects.requireNonNull(chatExporter, "eksport do czatu jest wymagany");
    }

    @ReadOperation(produces = "application/json")
    public String findings(@Nullable String code, @Nullable String fingerprint, @Nullable String unit) {

        FindingQuery query = new FindingQuery(code, fingerprint, unit);
        return recent.find(query).stream()
            .map(reported -> jsonWriter.write(reported.finding(), reported.unit()))
            .collect(Collectors.joining(",", "[", "]"));
    }

    @ReadOperation(produces = "text/markdown;charset=UTF-8")
    public WebEndpointResponse<String> export(@Selector String format, @Nullable String code,
                                              @Nullable String fingerprint, @Nullable String unit) {

        if (!Objects.equals(format, CHAT_FORMAT)) {

            return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NOT_FOUND);
        }
        FindingQuery query = new FindingQuery(code, fingerprint, unit);
        List<ReportedFinding> findings = recent.find(query);
        String text = chatExporter.export(findings);
        return new WebEndpointResponse<>(text, WebEndpointResponse.STATUS_OK);
    }
}
