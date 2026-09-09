// Schemat formularza przepisu.
//
// Walidacja siedzi w schemacie, nigdy w warunkach rozsypanych po `onChange`,
// a typ wartości bierze się z `z.infer` — nie pisze się go drugi raz.
//
// Pola liczbowe wchodzą jako tekst (bo tym jest `<input type="number">`), więc
// schemat robi jedyną konwersję w domenie: pusty tekst znaczy „nie podano”,
// a nie zero. Przepis bez podanego czasu i przepis na zero minut to dwie różne
// rzeczy.

import { z } from 'zod'

import { validationMessages } from '@/shared/validationMessages'

const optionalText = z.string().trim()

const requiredText = z.string().trim().min(1, validationMessages.required)

/** Tekst z pola liczbowego na liczbę; pusty zostaje pustką, nie zerem. */
function optionalNumberFromInput(message: string) {

  return z
    .string()
    .trim()
    .refine((text) => text === '' || /^\d+([.,]\d+)?$/.test(text), message)
    .transform((text) => {
      if (text === '') {
        return undefined
      }
      return Number(text.replace(',', '.'))
    })
}

export const ingredientRowSchema = z.object({
  id: z.number().optional(),
  group: optionalText,
  quantity: optionalNumberFromInput(validationMessages.number),
  unit: optionalText,
  displayName: requiredText,
  preparation: optionalText,
  optional: z.boolean(),
})

export const stepRowSchema = z.object({
  id: z.number().optional(),
  text: requiredText,
  durationMinutes: optionalNumberFromInput(validationMessages.number),
  temperatureC: optionalNumberFromInput(validationMessages.number),
})

export const recipeFormSchema = z.object({
  title: requiredText,
  description: optionalText,
  servingsAmount: optionalNumberFromInput(validationMessages.number),
  servingsUnit: optionalText,
  prepMinutes: optionalNumberFromInput(validationMessages.number),
  cookMinutes: optionalNumberFromInput(validationMessages.number),
  totalMinutes: optionalNumberFromInput(validationMessages.number),
  cuisine: optionalText,
  category: optionalText,
  difficulty: z.enum(['', 'EASY', 'MEDIUM', 'HARD']),
  tags: optionalText,
  diets: z.array(z.string()),
  changeSummary: optionalText,
  ingredients: z.array(ingredientRowSchema),
  steps: z.array(stepRowSchema),
})

export type RecipeFormValues = z.input<typeof recipeFormSchema>
export type RecipeFormOutput = z.output<typeof recipeFormSchema>
