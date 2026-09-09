// Książka przepisów: lista od najnowszego. Wyszukiwarka z filtrami dochodzi
// osobno — tutaj jest to, co widać po wejściu do domeny.

import { useQuery } from '@tanstack/react-query'

import { api } from '@/features/kitchen/api'
import { formatMinutes } from '@/features/kitchen/format'
import { kitchenKeys } from '@/features/kitchen/state/queryKeys'
import { useKitchenWorkspace } from '@/features/kitchen/state/KitchenWorkspace'
import { useHashRoute } from '@/shared/hooks/useHashRoute'

export default function PrzepisyRoute() {

  const { navigate } = useHashRoute()
  const { error: dictionariesError } = useKitchenWorkspace()
  const recipes = useQuery({
    queryKey: kitchenKeys.recipeList(0),
    queryFn: () => api.listRecipes(0, 20),
  })

  if (dictionariesError !== null || recipes.error !== null) {
    return (
      <p className="error" role="alert">
        Nie udało się połączyć z serwisem kuchni. Sprawdź, czy kitchen-service działa.
      </p>
    )
  }

  if (recipes.isPending) {
    return <p className="muted">Wczytywanie…</p>
  }

  const items = recipes.data?.items ?? []

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Przepisy</h2>
        <button type="button" onClick={() => navigate('kitchen', 'edycja')}>
          Nowy przepis
        </button>
      </div>

      {items.length === 0 && (
        <p>
          Książka jest pusta — nie ma jeszcze ani jednego przepisu. Import z linku,
          z tekstu i ze zdjęć dochodzi w kolejnych krokach.
        </p>
      )}

      <ul className="recipe-list">
        {items.map((recipe) => (
          <li key={recipe.id}>
            <button
              type="button"
              className="recipe-card"
              onClick={() =>
                navigate('kitchen', 'przepis', new URLSearchParams({ id: String(recipe.id) }))
              }
            >
              <span className="recipe-title">{recipe.title}</span>
              <span className="muted">
                {[
                  recipe.cuisine,
                  recipe.category,
                  formatMinutes(recipe.totalMinutes),
                  `${recipe.ingredientCount ?? 0} skł.`,
                  `${recipe.stepCount ?? 0} kroków`,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
