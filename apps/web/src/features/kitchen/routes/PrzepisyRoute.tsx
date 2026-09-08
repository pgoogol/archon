// Książka przepisów. Na tym etapie ekran ma jedno zadanie poza pustym stanem:
// pokazać, czy front dogaduje się z serwisem — słowniki są pierwszym
// wywołaniem, jakie domena wykonuje, więc ich błąd znaczy „backend nie odpowiada".

import { useKitchenWorkspace } from '@/features/kitchen/state/KitchenWorkspace'

export default function PrzepisyRoute() {

  const { dictionaries, loading, error } = useKitchenWorkspace()

  if (loading) {
    return <p className="muted">Wczytywanie…</p>
  }

  if (error !== null) {
    return (
      <p className="error" role="alert">
        Nie udało się połączyć z serwisem kuchni. Sprawdź, czy kitchen-service działa.
      </p>
    )
  }

  const units = dictionaries?.units ?? []

  return (
    <section className="panel">
      <h2>Przepisy</h2>
      <p>Książka jest pusta — nie ma jeszcze ani jednego przepisu.</p>
      <p className="muted">
        Import z linku, z wklejonego tekstu i ze zdjęć dochodzi w kolejnych krokach.
        Słowniki wczytane: {units.length} jednostek.
      </p>
    </section>
  )
}
