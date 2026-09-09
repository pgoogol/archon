import { describe, expect, it } from 'vitest'

import { toDraft, toFormValues } from './draftOps'
import type { RecipeResponse } from '@/features/kitchen/api'
import type { RecipeFormOutput } from '@/features/kitchen/forms/schemas'
import { SERNIK } from '@/features/kitchen/test/server'

/** Wartości formularza po walidacji, czyli to, co dostaje `toDraft`. */
function output(overrides: Partial<RecipeFormOutput> = {}): RecipeFormOutput {

  return {
    title: 'Sernik',
    description: '',
    servingsAmount: undefined,
    servingsUnit: '',
    prepMinutes: undefined,
    cookMinutes: undefined,
    totalMinutes: undefined,
    cuisine: '',
    category: '',
    difficulty: '',
    tags: '',
    diets: [],
    changeSummary: '',
    ingredients: [],
    steps: [],
    ...overrides,
  }
}

describe('toFormValues', () => {

  it('przepisuje liczby na tekst, bo tym jest pole formularza', () => {

    const values = toFormValues(SERNIK)

    expect(values.title).toBe('Sernik')
    expect(values.servingsAmount).toBe('12')
    expect(values.ingredients[0]).toMatchObject({ id: 100, quantity: '250', unit: 'g' })
  })

  it('zwija tagi do jednego pola po przecinku', () => {

    const values = toFormValues(SERNIK)

    expect(values.tags).toBe('na święta')
  })
})

describe('toDraft', () => {

  it('zachowuje identyfikatory wierszy, po których zapis rozpoznaje ten sam składnik', () => {

    const values = output({
      ingredients: [
        { id: 100, group: 'na spód', quantity: 300, unit: 'g', displayName: 'mąka', preparation: '', optional: false },
      ],
    })

    const draft = toDraft(values, SERNIK)

    expect(draft.ingredients?.[0]).toMatchObject({ id: 100, quantityMin: 300, quantityMax: 300 })
  })

  it('dokleja to, czego formularz nie pokazuje, zamiast kasować', () => {

    const values = output({
      ingredients: [
        { id: 100, group: '', quantity: 250, unit: 'g', displayName: 'mąka', preparation: '', optional: false },
      ],
      steps: [{ id: 200, text: 'Zagnieć spód', durationMinutes: 15, temperatureC: undefined }],
    })

    const draft = toDraft(values, SERNIK)

    // sprzęt nie ma pola w formularzu — ma przeżyć edycję tytułu
    expect(draft.steps?.[0]?.equipment).toEqual(['piekarnik'])
  })

  it('przelicza powiązania kroku na nowe pozycje po przestawieniu składników', () => {

    // given: „mąka" (id 100) ląduje na drugiej pozycji
    const values = output({
      ingredients: [
        { id: 101, group: '', quantity: 1, unit: 'kg', displayName: 'twaróg', preparation: '', optional: false },
        { id: 100, group: '', quantity: 250, unit: 'g', displayName: 'mąka', preparation: '', optional: false },
      ],
      steps: [{ id: 200, text: 'Zagnieć spód', durationMinutes: 15, temperatureC: undefined }],
    })

    const draft = toDraft(values, SERNIK)

    // then: krok nadal wskazuje mąkę, czyli teraz pozycję 1, a nie 0
    expect(draft.steps?.[0]?.ingredientIndexes).toEqual([1])
  })

  it('gubi powiązanie ze składnikiem, którego już nie ma', () => {

    const values = output({
      ingredients: [
        { id: 101, group: '', quantity: 1, unit: 'kg', displayName: 'twaróg', preparation: '', optional: false },
      ],
      steps: [{ id: 200, text: 'Zagnieć spód', durationMinutes: 15, temperatureC: undefined }],
    })

    const draft = toDraft(values, SERNIK)

    expect(draft.steps?.[0]?.ingredientIndexes).toEqual([])
  })

  it('rozbija tagi z jednego pola na listę', () => {

    const draft = toDraft(output({ tags: 'na święta, szybkie ,' }), undefined as unknown as RecipeResponse)

    expect(draft.tags).toEqual(['na święta', 'szybkie'])
  })
})
