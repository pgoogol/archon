// Zakładka „Import" (M3.1, przycięta w M3.2) — trzy wejścia: własne playlisty
// konta (tryb C, z raportem w modalu), playlista po linku (tryby B/D) oraz
// metryki wgrywane ręcznie z CSV (D24). Import biblioteki z pliku CSV zniknął
// z UI w M3.2 — endpoint został w API.

import ImportPanel from '@/features/music/components/ImportPanel'
import MetricsPanel from '@/features/music/components/MetricsPanel'
import SpotifyPanel from '@/features/music/components/SpotifyPanel'
import { useMusicWorkspace } from '@/features/music/state/MusicWorkspace'

export default function ImportRoute() {

  const { refresh: onImported } = useMusicWorkspace()

  return (
    <div className="panels">
      <SpotifyPanel onImported={onImported} />
      <ImportPanel onImported={onImported} />
      <MetricsPanel onImported={onImported} />
    </div>
  )
}
