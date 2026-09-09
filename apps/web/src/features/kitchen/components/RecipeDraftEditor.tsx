// Formularz przepisu — jeden dla trzech zastosowań: nowy przepis, edycja
// istniejącego i (od importu) poprawka szkicu. Trzy osobne formularze
// rozjechałyby się w walidacji przy pierwszej zmianie modelu.
//
// Komponent jest tylko widokiem: stan formularza trzyma react-hook-form
// w kontekście, a walidację schemat zod z `forms/schemas.ts`.

import { useFormContext } from 'react-hook-form'

import type { DictionariesResponse } from '@/features/kitchen/api'
import IngredientRows from '@/features/kitchen/components/IngredientRows'
import StepRows from '@/features/kitchen/components/StepRows'
import type { RecipeFormValues } from '@/features/kitchen/forms/schemas'

const DIFFICULTIES = [
  { value: 'EASY', label: 'łatwy' },
  { value: 'MEDIUM', label: 'średni' },
  { value: 'HARD', label: 'trudny' },
] as const

export default function RecipeDraftEditor({
  dictionaries,
}: {
  dictionaries: DictionariesResponse | null
}) {

  const { register, formState } = useFormContext<RecipeFormValues>()
  const units = dictionaries?.units ?? []
  const cuisines = dictionaries?.cuisines ?? []
  const categories = dictionaries?.categories ?? []
  const diets = dictionaries?.diets ?? []

  return (
    <div className="recipe-editor">
      <fieldset className="panel-inset">
        <h3>Przepis</h3>

        <label className="grow">
          Tytuł
          <input {...register('title')} />
        </label>
        {formState.errors.title && <p className="error">{formState.errors.title.message}</p>}

        <label className="grow">
          Opis
          <textarea rows={2} {...register('description')} />
        </label>

        <div className="draft-row">
          <label>
            Porcje
            <input inputMode="decimal" {...register('servingsAmount')} />
          </label>

          <label>
            Czego
            <input placeholder="porcje" {...register('servingsUnit')} />
          </label>

          <label>
            Przygotowanie (min)
            <input inputMode="numeric" {...register('prepMinutes')} />
          </label>

          <label>
            Gotowanie (min)
            <input inputMode="numeric" {...register('cookMinutes')} />
          </label>

          <label>
            Razem (min)
            <input inputMode="numeric" {...register('totalMinutes')} />
          </label>
        </div>

        <div className="draft-row">
          <label>
            Kuchnia
            <select {...register('cuisine')}>
              <option value="">—</option>
              {cuisines.map((entry) => (
                <option key={entry.id} value={entry.name ?? ''}>
                  {entry.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Kategoria
            <select {...register('category')}>
              <option value="">—</option>
              {categories.map((entry) => (
                <option key={entry.id} value={entry.name ?? ''}>
                  {entry.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Trudność
            <select {...register('difficulty')}>
              <option value="">—</option>
              {DIFFICULTIES.map((level) => (
                <option key={level.value} value={level.value}>
                  {level.label}
                </option>
              ))}
            </select>
          </label>

          <label className="grow">
            Tagi (po przecinku)
            <input {...register('tags')} />
          </label>
        </div>

        {diets.length > 0 && (
          <div className="draft-row" role="group" aria-label="Diety">
            {diets.map((diet) => (
              <label className="checkbox" key={diet.id}>
                <input type="checkbox" value={diet.name ?? ''} {...register('diets')} />
                {diet.name}
              </label>
            ))}
          </div>
        )}
      </fieldset>

      <IngredientRows units={units} />

      <StepRows />
    </div>
  )
}
