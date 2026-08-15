package com.pgoogol.music.api;

import com.pgoogol.music.ingestion.FailedPlaylist;
import com.pgoogol.music.ingestion.IngestReport;
import com.pgoogol.music.ingestion.MetricsFileReport;
import com.pgoogol.music.ingestion.MyPlaylistsIngestReport;
import com.pgoogol.music.ingestion.PlaylistIngestReport;
import com.pgoogol.music.ingestion.RowError;
import com.pgoogol.music.ingestion.SkippedItem;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Raporty importu → DTO kontraktu.
 *
 * <p>Mapowania jeden do jednego generuje MapStruct. Zbiorczy raport metryk
 * zostaje metodą {@code default}: sumy po plikach trzeba policzyć, a kształt
 * wiersza zależy od wariantu typu zapieczętowanego — deklaratywnym mapowaniem
 * nie da się tego wyrazić i udawanie, że się da, kosztowałoby czytelność.</p>
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface IngestApiMapper {

    IngestFileResponse toResponse(IngestReport report);

    IngestFileResponse.FailedRowResponse toFailedRow(RowError error);

    IngestPlaylistResponse toResponse(PlaylistIngestReport report);

    IngestPlaylistResponse.SkippedItemResponse toSkippedItem(SkippedItem item);

    IngestMyPlaylistsResponse toResponse(MyPlaylistsIngestReport report);

    IngestMyPlaylistsResponse.FailedPlaylistResponse toFailedPlaylist(FailedPlaylist playlist);

    IngestMetricsResponse.RowErrorResponse toRowError(RowError error);

    List<IngestMetricsResponse.RowErrorResponse> toRowErrors(List<RowError> errors);

    default IngestMetricsResponse toResponse(List<MetricsFileReport> fileReports) {

        List<IngestMetricsResponse.FileReportResponse> files = fileReports.stream()
            .map(this::toFileResponse)
            .toList();
        return new IngestMetricsResponse(
            sum(files, IngestMetricsResponse.FileReportResponse::applied),
            sum(files, IngestMetricsResponse.FileReportResponse::matchedByIsrc),
            sum(files, file -> file.skipped().size()),
            sum(files, file -> file.failed().size()),
            files);
    }

    default IngestMetricsResponse.FileReportResponse toFileResponse(MetricsFileReport fileReport) {

        return switch (fileReport) {
            case MetricsFileReport.Imported imported -> new IngestMetricsResponse.FileReportResponse(
                imported.file(), imported.report().applied(), imported.report().matchedByIsrc(),
                toRowErrors(imported.report().skipped()), toRowErrors(imported.report().failed()),
                null, null);
            case MetricsFileReport.Failed failed -> new IngestMetricsResponse.FileReportResponse(
                failed.file(), 0, 0, List.of(), List.of(), failed.errorCode(), failed.reason());
        };
    }

    private int sum(List<IngestMetricsResponse.FileReportResponse> files,
                    ToIntFunction<IngestMetricsResponse.FileReportResponse> field) {

        return files.stream().mapToInt(field).sum();
    }
}
