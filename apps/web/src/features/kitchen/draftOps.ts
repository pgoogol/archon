// Przekład między przepisem z serwera a wartościami formularza.
//
// Dwie rzeczy dzieją się tu celowo:
//
// 1. Identyfikatory wierszy jadą tam i z powrotem. To po nich zapis rozpoznaje,
//    że to ten sam składnik z inną ilością, a nie nowy wiersz — bez nich każda
//    edycja czytałaby się w historii jako skasowanie wszystkiego i dodanie od nowa.
// 2. Formularz nie pokazuje wszystkiego, co przepis ma (zamienniki, sprzęt,
//    powiązania kroków ze składnikami). Te części dokleja się przy zapisie
//    z wczytanego przepisu, żeby edycja tytułu nie kasowała po cichu danych
//    z importu.

import type { RecipeDraft, RecipeResponse } from '@/features/kitchen/api'
import type { RecipeFormOutput, RecipeFormValues } from '@/features/kitchen/forms/schemas'

export const emptyFormValues = (): RecipeFormValues => ({
  title: '',
  description: '',
  servingsAmount: '',
  servingsUnit: '',
  prepMinutes: '',
  cookMinutes: '',
  totalMinutes: '',
  cuisine: '',
  category: '',
  difficulty: '',
  tags: '',
  diets: [],
  changeSummary: '',
  ingredients: [],
  steps: [],
})

export const emptyIngredientRow = () => ({
  group: '',
  quantity: '',
  unit: '',
  displayName: '',
  preparation: '',
  optional: false,
})

export const emptyStepRow = () => ({ text: '', durationMinutes: '', temperatureC: '' })

const text = (value: string | undefined | null): string => value ?? ''

const numberText = (value: number | undefined | null): string => {
  if (value === undefined || value === null) {
    return ''
  }
  return String(value)
}

export function toFormValues(recipe: RecipeResponse): RecipeFormValues {

  return {
    title: text(recipe.title),
    description: text(recipe.description),
    servingsAmount: numberText(recipe.servingsAmount),
    servingsUnit: text(recipe.servingsUnit),
    prepMinutes: numberText(recipe.prepMinutes),
    cookMinutes: numberText(recipe.cookMinutes),
    totalMinutes: numberText(recipe.totalMinutes),
    cuisine: text(recipe.cuisine),
    category: text(recipe.category),
    difficulty: recipe.difficulty ?? '',
    tags: (recipe.tags ?? []).join(', '),
    diets: recipe.diets ?? [],
    changeSummary: '',
    ingredients: (recipe.ingredients ?? []).map((line) => ({
      id: line.id,
      group: text(line.group),
      quantity: numberText(line.quantityMin),
      unit: text(line.unit),
      displayName: text(line.displayName),
      preparation: text(line.preparation),
      optional: line.optional ?? false,
    })),
    steps: (recipe.steps ?? []).map((step) => ({
      id: step.id,
      text: text(step.text),
      durationMinutes: numberText(step.durationMinutes),
      temperatureC: numberText(step.temperatureC),
    })),
  }
}

/**
 * Wartości formularza na szkic do zapisania. `source` to przepis wczytany do
 * edycji — z niego dokleja się to, czego formularz nie pokazuje.
 */
export function toDraft(values: RecipeFormOutput, source?: RecipeResponse): RecipeDraft {

  const sourceIngredients = new Map(
    (source?.ingredients ?? []).filter((line) => line.id !== undefined).map((line) => [line.id, line]),
  )
  const sourceSteps = new Map(
    (source?.steps ?? []).filter((step) => step.id !== undefined).map((step) => [step.id, step]),
  )
  const positionOfIngredientId = new Map<number, number>()
  values.ingredients.forEach((line, index) => {
    if (line.id !== undefined) {
      positionOfIngredientId.set(line.id, index)
    }
  })

  return {
    title: values.title,
    description: values.description || undefined,
    servingsAmount: values.servingsAmount,
    servingsUnit: values.servingsUnit || undefined,
    prepMinutes: values.prepMinutes,
    cookMinutes: values.cookMinutes,
    totalMinutes: values.totalMinutes,
    cuisine: values.cuisine || undefined,
    category: values.category || undefined,
    difficulty: values.difficulty === '' ? undefined : values.difficulty,
    tags: values.tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
    diets: values.diets,
    ingredients: values.ingredients.map((line) => {
      const original = line.id === undefined ? undefined : sourceIngredients.get(line.id)
      return {
        id: line.id,
        group: line.group || undefined,
        displayName: line.displayName,
        sourceText: original?.sourceText,
        quantityMin: line.quantity,
        quantityMax: line.quantity,
        unit: line.unit || undefined,
        quantityText: original?.quantityText,
        preparation: line.preparation || undefined,
        optional: line.optional,
        note: original?.note,
        alternatives: (original?.alternatives ?? []).map((alternative) => ({
          id: alternative.id,
          displayName: alternative.displayName ?? '',
          quantityMin: alternative.quantityMin,
          quantityMax: alternative.quantityMax,
          unit: alternative.unit,
          quantityText: alternative.quantityText,
          note: alternative.note,
        })),
      }
    }),
    steps: values.steps.map((step) => {
      const original = step.id === undefined ? undefined : sourceSteps.get(step.id)
      // powiązania liczymy z identyfikatorów w KOŃCOWEJ kolejności składników —
      // przestawienie wiersza w formularzu nie ma prawa przepiąć kroku na sąsiedni
      const linked = (original?.ingredientIds ?? [])
        .map((ingredientId) => positionOfIngredientId.get(ingredientId))
        .filter((index): index is number => index !== undefined)
      return {
        id: step.id,
        group: original?.group,
        text: step.text,
        sourceText: original?.sourceText,
        durationMinutes: step.durationMinutes,
        temperatureC: step.temperatureC,
        temperatureNote: original?.temperatureNote,
        ingredientIndexes: linked,
        equipment: original?.equipment ?? [],
      }
    }),
  }
}
