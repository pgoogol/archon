// Lista składników w formularzu. Każdy wiersz to osobne pola — ilość, jednostka,
// nazwa, obróbka — a nie jedna linijka tekstu do sparsowania później.

import { useFieldArray, useFormContext } from 'react-hook-form'

import type { UnitResponse } from '@/features/kitchen/api'
import { emptyIngredientRow } from '@/features/kitchen/draftOps'
import type { RecipeFormValues } from '@/features/kitchen/forms/schemas'

export default function IngredientRows({ units }: { units: UnitResponse[] }) {

  const { control, register, formState } = useFormContext<RecipeFormValues>()
  const rows = useFieldArray({ control, name: 'ingredients' })

  return (
    <fieldset className="panel-inset">
      <h3>Składniki</h3>

      {rows.fields.length === 0 && <p className="muted">Nie ma jeszcze żadnego składnika.</p>}

      {rows.fields.map((field, index) => (
        <div className="draft-row" key={field.id}>
          <input
            aria-label={`Grupa składnika ${index + 1}`}
            placeholder="grupa (na spód)"
            {...register(`ingredients.${index}.group`)}
          />

          <input
            aria-label={`Ilość składnika ${index + 1}`}
            inputMode="decimal"
            placeholder="ilość"
            {...register(`ingredients.${index}.quantity`)}
          />

          <select aria-label={`Jednostka składnika ${index + 1}`} {...register(`ingredients.${index}.unit`)}>
            <option value="">—</option>
            {units.map((unit) => (
              <option key={unit.code} value={unit.code ?? ''}>
                {unit.name}
              </option>
            ))}
          </select>

          <input
            aria-label={`Nazwa składnika ${index + 1}`}
            placeholder="składnik"
            {...register(`ingredients.${index}.displayName`)}
          />

          <input
            aria-label={`Przygotowanie składnika ${index + 1}`}
            placeholder="posiekana"
            {...register(`ingredients.${index}.preparation`)}
          />

          <label className="checkbox">
            <input type="checkbox" {...register(`ingredients.${index}.optional`)} />
            opcjonalny
          </label>

          <div className="row-actions">
            <button
              type="button"
              aria-label={`Wyżej składnik ${index + 1}`}
              onClick={() => rows.move(index, Math.max(index - 1, 0))}
            >
              ↑
            </button>
            <button
              type="button"
              aria-label={`Niżej składnik ${index + 1}`}
              onClick={() => rows.move(index, Math.min(index + 1, rows.fields.length - 1))}
            >
              ↓
            </button>
            <button
              type="button"
              aria-label={`Usuń składnik ${index + 1}`}
              onClick={() => rows.remove(index)}
            >
              ✕
            </button>
          </div>

          {formState.errors.ingredients?.[index]?.displayName && (
            <p className="error">{formState.errors.ingredients[index]?.displayName?.message}</p>
          )}
          {formState.errors.ingredients?.[index]?.quantity && (
            <p className="error">{formState.errors.ingredients[index]?.quantity?.message}</p>
          )}
        </div>
      ))}

      <button type="button" onClick={() => rows.append(emptyIngredientRow())}>
        Dodaj składnik
      </button>
    </fieldset>
  )
}
