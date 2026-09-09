// Kroki przygotowania. Czas i temperatura mają własne pola, ale tekst kroku
// zostaje pełny — „piecz 40 minut w 180°C" ma się dać przeczytać w całości.

import { useFieldArray, useFormContext } from 'react-hook-form'

import { emptyStepRow } from '@/features/kitchen/draftOps'
import type { RecipeFormValues } from '@/features/kitchen/forms/schemas'

export default function StepRows() {

  const { control, register, formState } = useFormContext<RecipeFormValues>()
  const rows = useFieldArray({ control, name: 'steps' })

  return (
    <fieldset className="panel-inset">
      <h3>Kroki</h3>

      {rows.fields.length === 0 && <p className="muted">Nie ma jeszcze żadnego kroku.</p>}

      {rows.fields.map((field, index) => (
        <div className="draft-row draft-step" key={field.id}>
          <span className="step-number">{index + 1}.</span>

          <textarea
            aria-label={`Treść kroku ${index + 1}`}
            rows={2}
            placeholder="co zrobić"
            {...register(`steps.${index}.text`)}
          />

          <input
            aria-label={`Czas kroku ${index + 1}`}
            inputMode="numeric"
            placeholder="min"
            {...register(`steps.${index}.durationMinutes`)}
          />

          <input
            aria-label={`Temperatura kroku ${index + 1}`}
            inputMode="numeric"
            placeholder="°C"
            {...register(`steps.${index}.temperatureC`)}
          />

          <div className="row-actions">
            <button
              type="button"
              aria-label={`Wyżej krok ${index + 1}`}
              onClick={() => rows.move(index, Math.max(index - 1, 0))}
            >
              ↑
            </button>
            <button
              type="button"
              aria-label={`Niżej krok ${index + 1}`}
              onClick={() => rows.move(index, Math.min(index + 1, rows.fields.length - 1))}
            >
              ↓
            </button>
            <button type="button" aria-label={`Usuń krok ${index + 1}`} onClick={() => rows.remove(index)}>
              ✕
            </button>
          </div>

          {formState.errors.steps?.[index]?.text && (
            <p className="error">{formState.errors.steps[index]?.text?.message}</p>
          )}
        </div>
      ))}

      <button type="button" onClick={() => rows.append(emptyStepRow())}>
        Dodaj krok
      </button>
    </fieldset>
  )
}
