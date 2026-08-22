// Typy DTO generowane z contracts/openapi/music.yaml. Nigdy nie pisz ich ręcznie
// i nigdy nie edytuj music.generated.ts — regeneruj:
//
//   pnpm --filter @archon/api-client generate
//
// CI regeneruje i wywala się na diffie, więc nieaktualny plik w repo psuje build.
//
// Ten plik jest jedyną warstwą pisaną ręcznie: nadaje schematom czytelne nazwy,
// żeby front nie musiał wszędzie pisać components['schemas'][...].
//
// Punkt wejścia @archon/api-client/music — każda domena ma własny, bo obydwa
// kontrakty mają swój ErrorResponse i eksport pod jedną nazwą by się zderzył.

import type { components } from './music.generated'

type Schemas = components['schemas']

export type TrackResponse = Schemas['TrackResponse']
export type TrackLibraryResponse = Schemas['TrackLibraryResponse']
export type CatalogRowResponse = Schemas['CatalogRowResponse']
export type TrackMetricsResponse = Schemas['TrackMetricsResponse']
export type MetricsCoverageResponse = Schemas['MetricsCoverageResponse']

export type LibraryEntryResponse = Schemas['LibraryEntryResponse']
export type AddLibraryTrackRequest = Schemas['AddLibraryTrackRequest']
export type UpdateLibraryEntryRequest = Schemas['UpdateLibraryEntryRequest']

export type LibraryOverviewResponse = Schemas['LibraryOverviewResponse']
export type BucketResponse = Schemas['BucketResponse']
export type MatrixCellResponse = Schemas['MatrixCellResponse']
export type MetricResponse = Schemas['MetricResponse']
export type RecentTrackResponse = Schemas['RecentTrackResponse']
export type ScaleResponse = Schemas['ScaleResponse']
export type QualityResponse = Schemas['QualityResponse']
export type SoundResponse = Schemas['SoundResponse']
export type TimelineResponse = Schemas['TimelineResponse']
export type TasteResponse = Schemas['TasteResponse']
export type FileReportResponse = Schemas['FileReportResponse']


export type PlaylistSummaryResponse = Schemas['PlaylistSummaryResponse']
export type PlaylistResponse = Schemas['PlaylistResponse']
export type PlaylistTrackResponse = Schemas['PlaylistTrackResponse']
export type PlaylistExportResponse = Schemas['PlaylistExportResponse']
export type SavePlaylistRequest = Schemas['SavePlaylistRequest']
export type AddPlaylistTrackRequest = Schemas['AddPlaylistTrackRequest']
export type ReorderPlaylistRequest = Schemas['ReorderPlaylistRequest']

export type SetProposalRequest = Schemas['SetProposalRequest']
export type SetProposalResponse = Schemas['SetProposalResponse']
export type ProposedTrackResponse = Schemas['ProposedTrackResponse']
export type SetFillRequest = Schemas['SetFillRequest']
export type SetFillResponse = Schemas['SetFillResponse']
export type SetSuggestionRequest = Schemas['SetSuggestionRequest']
export type SetSuggestionResponse = Schemas['SetSuggestionResponse']
export type SuggestedTrackResponse = Schemas['SuggestedTrackResponse']

export type EnrichRequest = Schemas['EnrichRequest']
export type EnrichmentEstimateResponse = Schemas['EnrichmentEstimateResponse']
export type EnrichJobResponse = Schemas['EnrichJobResponse']
export type EnrichFailureResponse = Schemas['EnrichFailureResponse']
export type MissingFieldsCount = Schemas['MissingFieldsCount']
export type ExecutionIdResponse = Schemas['ExecutionIdResponse']

export type IngestFileResponse = Schemas['IngestFileResponse']
export type IngestMetricsResponse = Schemas['IngestMetricsResponse']
export type IngestPlaylistRequest = Schemas['IngestPlaylistRequest']
export type IngestPlaylistResponse = Schemas['IngestPlaylistResponse']
export type IngestMyPlaylistsResponse = Schemas['IngestMyPlaylistsResponse']
export type PlaylistRefreshStatusResponse = Schemas['PlaylistRefreshStatusResponse']
export type RowErrorResponse = Schemas['RowErrorResponse']
export type SkippedItemResponse = Schemas['SkippedItemResponse']

export type SpotifyAccountResponse = Schemas['SpotifyAccountResponse']
export type ErrorResponse = Schemas['ErrorResponse']

export type GenreFamily = Schemas['GenreFamily']
export type TempoClass = Schemas['TempoClass']
export type BpmSource = Schemas['BpmSource']
export type MissingGroup = Schemas['MissingGroup']
export type CatalogSort = Schemas['CatalogSort']
export type SortDirection = Schemas['SortDirection']
export type EnrichmentScope = Schemas['EnrichmentScope']
export type FieldGroup = Schemas['FieldGroup']
export type SetCurve = Schemas['SetCurve']
export type DjSlot = Schemas['DjSlot']
export type LibrarySource = Schemas['LibrarySource']

/** Strona wyniku — backend zwraca ten sam kształt dla każdej listy stronicowanej. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type { components, paths } from './music.generated'
