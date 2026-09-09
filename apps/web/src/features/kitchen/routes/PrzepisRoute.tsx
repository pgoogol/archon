// Widok przepisu: składniki w grupach, kroki z czasem i temperaturą, uwagi
// z gotowania. Oryginalne linie ze źródła są pod przełącznikiem — przydają się
// przy przepisach tłumaczonych, gdzie warto sprawdzić, co było w oryginale.

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { api } from '@/features/kitchen/api'
import { formatMinutes, formatQuantity, groupBy } from '@/features/kitchen/format'
import { kitchenKeys } from '@/features/kitchen/state/queryKeys'
import { useHashRoute } from '@/shared/hooks/useHashRoute'

export default function PrzepisRoute() {

  const { params, navigate } = useHashRoute()
  const queryClient = useQueryClient()
  const [showSource, setShowSource] = useState(false)
  const [noteBody, setNoteBody] = useState('')

  const id = Number(params.get('id'))
  const recipe = useQuery({
    queryKey: kitchenKeys.recipe(id),
    queryFn: () => api.getRecipe(id),
    enabled: Number.isFinite(id) && id > 0,
  })
  const notes = useQuery({
    queryKey: kitchenKeys.notes(id),
    queryFn: () => api.listNotes(id),
    enabled: Number.isFinite(id) && id > 0,
  })

  const addNote = useMutation({
    mutationFn: (body: string) => api.addNote(id, { body }),
    onSuccess: () => {
      setNoteBody('')
      queryClient.invalidateQueries({ queryKey: kitchenKeys.notes(id) })
    },
  })

  if (!Number.isFinite(id) || id <= 0) {
    return <p className="error">Brak przepisu w adresie.</p>
  }

  if (recipe.isPending) {
    return <p className="muted">Wczytywanie…</p>
  }

  if (recipe.error !== null || !recipe.data) {
    return (
      <p className="error" role="alert">
        Nie udało się wczytać przepisu.
      </p>
    )
  }

  const data = recipe.data
  const ingredientGroups = groupBy(data.ingredients ?? [])
  const stepGroups = groupBy(data.steps ?? [])

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>{data.title}</h2>
        <div className="row-actions">
          <button
            type="button"
            onClick={() =>
              navigate('kitchen', 'edycja', new URLSearchParams({ id: String(id) }))
            }
          >
            Edytuj
          </button>
          <button type="button" onClick={() => setShowSource((shown) => !shown)}>
            {showSource ? 'Ukryj oryginał' : 'Pokaż oryginał'}
          </button>
        </div>
      </div>

      {data.description && <p>{data.description}</p>}

      <p className="muted">
        {[
          data.cuisine,
          data.category,
          data.servingsAmount ? `${data.servingsAmount} ${data.servingsUnit ?? 'porcje'}` : '',
          formatMinutes(data.totalMinutes),
          `rewizja ${data.currentRevisionNo ?? 0}`,
        ]
          .filter(Boolean)
          .join(' · ')}
      </p>

      <h3>Składniki</h3>
      {ingredientGroups.map(([group, lines]) => (
        <div key={group || 'bez-grupy'}>
          {group && <h4>{group}</h4>}
          <ul className="ingredient-list">
            {lines.map((line) => (
              <li key={line.id}>
                <span className="quantity">{formatQuantity(line)}</span> {line.displayName}
                {line.preparation && <span className="muted">, {line.preparation}</span>}
                {line.optional && <span className="muted"> (opcjonalnie)</span>}
                {showSource && line.sourceText && (
                  <div className="muted source-line">{line.sourceText}</div>
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}

      <h3>Przygotowanie</h3>
      {stepGroups.map(([group, steps]) => (
        <div key={group || 'bez-grupy'}>
          {group && <h4>{group}</h4>}
          <ol className="step-list">
            {steps.map((step) => (
              <li key={step.id}>
                {step.text}
                {(step.durationMinutes || step.temperatureC) && (
                  <span className="muted">
                    {' '}
                    ({[formatMinutes(step.durationMinutes), step.temperatureC ? `${step.temperatureC}°C` : '']
                      .filter(Boolean)
                      .join(', ')}
                    )
                  </span>
                )}
                {showSource && step.sourceText && (
                  <div className="muted source-line">{step.sourceText}</div>
                )}
              </li>
            ))}
          </ol>
        </div>
      ))}

      <h3>Uwagi</h3>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (noteBody.trim()) {
            addNote.mutate(noteBody.trim())
          }
        }}
      >
        <label className="grow">
          <span className="sr-only">Nowa uwaga</span>
          <input
            aria-label="Nowa uwaga"
            placeholder="za słone, następnym razem połowa cukru…"
            value={noteBody}
            onChange={(event) => setNoteBody(event.target.value)}
          />
        </label>
        <button type="submit">Dodaj uwagę</button>
      </form>

      <ul className="note-list">
        {(notes.data ?? []).map((note) => (
          <li key={note.id}>{note.body}</li>
        ))}
      </ul>
    </section>
  )
}
