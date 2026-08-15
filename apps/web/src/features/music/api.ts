// Klient API domeny muzycznej. Typy DTO NIE są tu pisane — pochodzą z kontraktu
// (contracts/openapi/music.yaml) przez @archon/api-client. Ten plik trzyma już
// tylko wywołania endpointów i kształty wejściowe samego frontu.

import type {
  CatalogRowResponse,
  CatalogSort,
  EnrichFailureResponse,
  EnrichJobResponse,
  EnrichmentEstimateResponse,
  IngestFileResponse,
  IngestMetricsResponse,
  IngestMyPlaylistsResponse,
  IngestPlaylistResponse,
  LibraryEntryResponse,
  LibraryOverviewResponse,
  MetricsCoverageResponse,
  MissingFieldsCount as MissingCountResponse,
  MissingGroup,
  PageResponse,
  PlaylistExportResponse,
  PlaylistRefreshStatusResponse,
  PlaylistResponse,
  PlaylistSummaryResponse,
  SetCurve,
  SetFillRequest,
  SetFillResponse,
  SetProposalRequest,
  SetProposalResponse,
  SetSuggestionRequest,
  SetSuggestionResponse,
  SortDirection,
  SpotifyAccountResponse,
  TrackMetricsResponse,
  UpdateLibraryEntryRequest,
} from '@archon/api-client'

export type {
  AddLibraryTrackRequest,
  AddPlaylistTrackRequest,
  BucketResponse,
  CatalogRowResponse,
  CatalogSort,
  EnrichFailureResponse,
  EnrichJobResponse,
  EnrichRequest,
  EnrichmentEstimateResponse,
  EnrichmentScope,
  ErrorResponse,
  FieldGroup,
  IngestFileResponse,
  IngestMetricsResponse,
  IngestMyPlaylistsResponse,
  IngestPlaylistRequest,
  IngestPlaylistResponse,
  LibraryEntryResponse,
  LibraryOverviewResponse,
  MatrixCellResponse,
  MetricResponse,
  MetricsCoverageResponse,
  MissingGroup,
  PageResponse,
  PlaylistExportResponse,
  PlaylistRefreshStatusResponse,
  PlaylistResponse,
  PlaylistSummaryResponse,
  PlaylistTrackResponse,
  ProposedTrackResponse,
  RecentTrackResponse,
  ReorderPlaylistRequest,
  RowErrorResponse,
  SavePlaylistRequest,
  SetCurve,
  SetFillRequest,
  SetFillResponse,
  SetProposalRequest,
  SetProposalResponse,
  SetSuggestionRequest,
  SetSuggestionResponse,
  SkippedItemResponse,
  SortDirection,
  SpotifyAccountResponse,
  SuggestedTrackResponse,
  TrackLibraryResponse,
  TrackMetricsResponse,
  TrackResponse,
  UpdateLibraryEntryRequest,
} from '@archon/api-client'

// nazwy, pod którymi front znał te DTO wcześniej
export type {
  MissingFieldsCount as MissingCountResponse,
  FileReportResponse as MetricsFileReportResponse,
  ScaleResponse as OverviewScaleResponse,
  QualityResponse as OverviewQualityResponse,
  SoundResponse as OverviewSoundResponse,
  TimelineResponse as OverviewTimelineResponse,
  TasteResponse as OverviewTasteResponse,
} from '@archon/api-client'

/** Dane prywatne DJ-a pokazywane w wierszu biblioteki (M5.6) — reszta w szufladzie. */
/**
 * Wiersz wyszukiwarki (M5.6): katalog i dane DJ-a jako dwa obiekty, bo rozdział
 * danych obowiązuje też w kontrakcie. `library === null` znaczy „utwór jest
 * w katalogu, ale nie w bibliotece" — co innego niż „w bibliotece bez oceny".
 */
/** Metryki wgrane ręcznie z CSV — surowe wartości z pliku, skala 0..1. */
/** Raport jednego pliku partii; `error` niepuste = plik odpadł w całości. */
/** Liczby na wierzchu są sumą partii; numery wierszy mają sens tylko przy pliku. */
/** Tryb C: playlista, która padła, nie przerywa importu — wraca w `failed`. */
/** Utwór pominięty przez job wzbogacania razem z powodem. */
/** Stan automatycznego odświeżania playlist w tle (M4.7). */
/** Jeden słupek rozkładu w przeglądzie biblioteki (M4.3). */
/** Komórka macierzy tempo × energia — dwa wymiary naraz (M5.4). */
/** Średnia cecha audio z metryk ręcznych, skala 0..1. */
/**
 * Skala zbioru — same utwory; playlisty i sety mają własne zakładki.
 * Średnie = null dla pustego katalogu, nie zero.
 */
/** Pięć grup = pięć stref czytania ekranu przeglądu (M5.4). */
/** Profil kształtu wieczoru dla generatora (M4.5) — udziały faz wieczoru. */

export const SET_CURVES: readonly SetCurve[] = ['STANDARD', 'WEDDING', 'CLUB', 'EVEN']

export const SET_CURVE_LABELS: Record<SetCurve, string> = {
  STANDARD: 'standardowy',
  WEDDING: 'wesele',
  CLUB: 'klub',
  EVEN: 'równy',
}

/** Filtry puli wspólne dla generatora i domykania setu (M4.2/M4.4). */
export interface SetPoolFilters {
  search?: string
  genreFamily?: string
  bpmMin?: number
  bpmMax?: number
  tempoClass?: string
  energy?: string
  inLibrary?: boolean
  ratingMin?: number
  tag?: string
  camelot?: string
  camelotCompatible?: boolean
}

/** Propozycja setu (M4.2) — generator niczego nie zapisuje. */
/** Uzupełnienie gotowego setu (M4.4) — `targetMinutes` liczy CAŁY wieczór. */
/** Dobranie utworu na jedno miejsce w secie (M4.4); brak `position` = na koniec. */
/** Pokrycie katalogu metrykami z pliku — kontekst filtrów metryk (M4.1). */
/** Szacunek zlecenia wzbogacania (M5.1) — nic nie uruchamia. */
/** Biała lista sortowania po stronie API (M3.1, dane DJ-a w M5.6; enum CatalogSort). */
export const CATALOG_SORTS = [
  'RELEVANCE',
  'TITLE',
  'ARTIST',
  'ALBUM',
  'YEAR',
  'BPM',
  'POPULARITY',
  'DURATION',
  'DANCEABILITY',
  'ENERGY',
  'RATING',
  'ADDED_AT',
] as const



/** Grupy braków w wersji filtra biblioteki — ANY = „do wzbogacenia" (M5.6). */
export const MISSING_GROUPS = ['ANY', 'METADATA', 'AUDIO', 'AI'] as const


/** Źródła BPM z kaskady — kryterium: pomiar czy estymata. */
export const BPM_SOURCES = ['MANUAL', 'ACOUSTICBRAINZ', 'DEEZER', 'LLM'] as const

export interface SearchParams {
  search?: string
  /** Filtry samego utworu (M5.6): gatunek, rocznik, długość, popularność, explicit. */
  genreFamily?: string
  yearMin?: number
  yearMax?: number
  durationMinSec?: number
  durationMaxSec?: number
  popularityMin?: number
  explicit?: boolean
  /** Filtry brzmienia: tempo i energia. */
  bpmMin?: number
  bpmMax?: number
  tempoClass?: string
  energy?: string
  /** Filtry biblioteki DJ-a (M3.2): przynależność, ocena minimalna, custom tag. */
  inLibrary?: boolean
  ratingMin?: number
  tag?: string
  /** Filtr harmoniczny (M4.1): pozycja koła + czy rozszerzyć do zgodnych. */
  camelot?: string
  camelotCompatible?: boolean
  /** Filtry metryk — odsiewają utwory bez metryk, stąd licznik pokrycia. */
  valenceMin?: number
  valenceMax?: number
  instrumentalMin?: number
  livenessMax?: number
  /** Filtry kompletności danych (M5.6): skąd tempo i czego brakuje. */
  bpmSource?: string
  missing?: MissingGroup
  sort?: CatalogSort
  direction?: SortDirection
  page?: number
  size?: number
}

import { jsonInit, request } from '@/shared/http/client'

export { ApiError } from '@/shared/http/client'

export const api = {
  searchTracks(params: SearchParams): Promise<PageResponse<CatalogRowResponse>> {
    const query = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        query.set(key, String(value))
      }
    })
    return request(`/api/catalog/tracks?${query}`)
  },

  libraryOverview(): Promise<LibraryOverviewResponse> {
    return request('/api/library/overview')
  },

  proposeSet(body: SetProposalRequest): Promise<SetProposalResponse> {
    return request('/api/sets/propose', jsonInit('POST', body))
  },

  /** Dalszy ciąg gotowego setu (M4.4) — nic nie zapisuje, tak jak generator. */
  fillSet(playlistId: number, body: SetFillRequest): Promise<SetFillResponse> {
    return request(`/api/sets/${playlistId}/fill`, jsonInit('POST', body))
  },

  /** Kandydaci na jedno miejsce w secie (M4.4) — bez losowania, uszeregowani. */
  suggestForSet(playlistId: number, body: SetSuggestionRequest): Promise<SetSuggestionResponse> {
    return request(`/api/sets/${playlistId}/suggest`, jsonInit('POST', body))
  },

  metricsCoverage(): Promise<MetricsCoverageResponse> {
    return request('/api/catalog/metrics-coverage')
  },

  /** Słownik custom tagów DJ-a — podpowiedzi filtra bibliotecznego (M3.2). */
  listTags(): Promise<string[]> {
    return request('/api/library/tags')
  },

  getLibraryEntry(spotifyId: string): Promise<LibraryEntryResponse> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`)
  },

  updateLibraryEntry(
    spotifyId: string,
    body: UpdateLibraryEntryRequest,
  ): Promise<LibraryEntryResponse> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`, jsonInit('PATCH', body))
  },

  deleteLibraryEntry(spotifyId: string): Promise<void> {
    return request(`/api/library/tracks/${encodeURIComponent(spotifyId)}`, { method: 'DELETE' })
  },

  ingestFile(file: File): Promise<IngestFileResponse> {
    const form = new FormData()
    form.append('file', file)
    return request('/api/ingest/file', { method: 'POST', body: form })
  },

  ingestMetrics(files: File[]): Promise<IngestMetricsResponse> {
    const form = new FormData()
    files.forEach((file) => form.append('file', file))
    return request('/api/ingest/metrics', { method: 'POST', body: form })
  },

  /** 204 z backendu (utwór bez metryk) wraca jako undefined — patrz `request`. */
  getTrackMetrics(spotifyId: string): Promise<TrackMetricsResponse | undefined> {
    return request(`/api/catalog/tracks/${encodeURIComponent(spotifyId)}/metrics`)
  },

  ingestPlaylist(url: string): Promise<IngestPlaylistResponse> {
    return request('/api/ingest/playlist', jsonInit('POST', { url }))
  },

  ingestMyPlaylists(): Promise<IngestMyPlaylistsResponse> {
    return request('/api/ingest/my-playlists', { method: 'POST' })
  },

  spotifyAccount(): Promise<SpotifyAccountResponse> {
    return request('/api/auth/spotify/status')
  },

  playlistRefreshStatus(): Promise<PlaylistRefreshStatusResponse> {
    return request('/api/ingest/my-playlists/refresh-status')
  },

  listPlaylists(): Promise<PlaylistSummaryResponse[]> {
    return request('/api/playlists')
  },

  getPlaylist(id: number): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}`)
  },

  createPlaylist(name: string): Promise<PlaylistSummaryResponse> {
    return request('/api/playlists', jsonInit('POST', { name }))
  },

  renamePlaylist(id: number, name: string, version: number): Promise<PlaylistSummaryResponse> {
    return request(`/api/playlists/${id}`, jsonInit('PATCH', { name, version }))
  },

  deletePlaylist(id: number): Promise<void> {
    return request(`/api/playlists/${id}`, { method: 'DELETE' })
  },

  addPlaylistTrack(id: number, spotifyId: string): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks`, jsonInit('POST', { spotifyId }))
  },

  removePlaylistTrack(id: number, spotifyId: string): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks/${encodeURIComponent(spotifyId)}`, {
      method: 'DELETE',
    })
  },

  reorderPlaylist(id: number, spotifyIds: string[], version: number): Promise<PlaylistResponse> {
    return request(`/api/playlists/${id}/tracks`, jsonInit('PUT', { spotifyIds, version }))
  },

  exportPlaylist(id: number): Promise<PlaylistExportResponse> {
    return request(`/api/playlists/${id}/export-to-spotify`, { method: 'POST' })
  },

  startEnrichment(scope: string, fields: string[], spotifyIds: string[]): Promise<{ executionId: number }> {
    return request('/api/enrich', jsonInit('POST', { scope, fields, spotifyIds }))
  },

  listJobs(limit = 10): Promise<EnrichJobResponse[]> {
    return request(`/api/enrich/jobs?limit=${limit}`)
  },

  jobStatus(executionId: number): Promise<EnrichJobResponse> {
    return request(`/api/enrich/jobs/${executionId}`)
  },

  /** Co dokładnie odpadło w danym przebiegu i dlaczego. */
  jobFailures(executionId: number, limit = 200): Promise<EnrichFailureResponse[]> {
    return request(`/api/enrich/jobs/${executionId}/failures?limit=${limit}`)
  },

  restartJob(executionId: number): Promise<{ executionId: number }> {
    return request(`/api/enrich/jobs/${executionId}/restart`, { method: 'POST' })
  },

  estimateEnrichment(
    scope: string,
    fields: string[],
    spotifyIds: string[],
  ): Promise<EnrichmentEstimateResponse> {
    return request('/api/enrich/estimate', jsonInit('POST', { scope, fields, spotifyIds }))
  },

  missingCount(): Promise<MissingCountResponse> {
    return request('/api/enrich/missing-count')
  },
}
