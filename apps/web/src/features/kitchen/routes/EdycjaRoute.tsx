// Formularz przepisu: nowy albo edycja istniejącego. Numer rewizji, na której
// pracował ekran, wraca na serwer — dzięki temu zapis z drugiej karty nie
// nadpisze po cichu tego, co zmieniło się w międzyczasie.

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'

import { api } from '@/features/kitchen/api'
import RecipeDraftEditor from '@/features/kitchen/components/RecipeDraftEditor'
import { emptyFormValues, toDraft, toFormValues } from '@/features/kitchen/draftOps'
import {
  recipeFormSchema,
  type RecipeFormOutput,
  type RecipeFormValues,
} from '@/features/kitchen/forms/schemas'
import { kitchenKeys } from '@/features/kitchen/state/queryKeys'
import { useKitchenWorkspace } from '@/features/kitchen/state/KitchenWorkspace'
import { useHashRoute } from '@/shared/hooks/useHashRoute'
import { ApiError } from '@/shared/http/client'

export default function EdycjaRoute() {

  const { params, navigate } = useHashRoute()
  const queryClient = useQueryClient()
  const { dictionaries } = useKitchenWorkspace()
  const [problem, setProblem] = useState<string | null>(null)

  const rawId = params.get('id')
  const id = rawId ? Number(rawId) : 0
  const editing = Number.isFinite(id) && id > 0

  const existing = useQuery({
    queryKey: kitchenKeys.recipe(id),
    queryFn: () => api.getRecipe(id),
    enabled: editing,
  })

  const form = useForm<RecipeFormValues, unknown, RecipeFormOutput>({
    resolver: zodResolver(recipeFormSchema),
    defaultValues: emptyFormValues(),
  })

  useEffect(() => {
    if (existing.data) {
      form.reset(toFormValues(existing.data))
    }
  }, [existing.data, form])

  const save = useMutation({
    mutationFn: (values: RecipeFormOutput) => {
      const body = {
        draft: toDraft(values, existing.data),
        changeSummary: values.changeSummary || undefined,
        expectedRevisionNo: existing.data?.currentRevisionNo,
      }
      if (editing) {
        return api.updateRecipe(id, body)
      }
      return api.createRecipe(body)
    },
    onSuccess: (result) => {
      setProblem(null)
      queryClient.invalidateQueries({ queryKey: kitchenKeys.recipes() })
      const savedId = result.recipe?.id
      if (savedId) {
        navigate('kitchen', 'przepis', new URLSearchParams({ id: String(savedId) }))
      }
    },
    onError: (error) => {
      if (error instanceof ApiError && error.errorCode === 'RECIPE_MODIFIED') {
        setProblem('Przepis zmienił się w międzyczasie. Odśwież i nanieś zmiany jeszcze raz.')
        return
      }
      if (error instanceof ApiError) {
        setProblem(error.message)
        return
      }
      setProblem('Nie udało się zapisać przepisu.')
    },
  })

  if (editing && existing.isPending) {
    return <p className="muted">Wczytywanie…</p>
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>{editing ? 'Edycja przepisu' : 'Nowy przepis'}</h2>
        <button type="button" onClick={() => navigate('kitchen', '')}>
          Wróć do listy
        </button>
      </div>

      {problem && (
        <p className="error" role="alert">
          {problem}
        </p>
      )}

      <FormProvider {...form}>
        <form onSubmit={form.handleSubmit((values) => save.mutate(values))}>
          <RecipeDraftEditor dictionaries={dictionaries} />

          {editing && (
            <label className="grow">
              Co zmieniłeś?
              <input placeholder="np. mniej cukru, dłuższe pieczenie" {...form.register('changeSummary')} />
            </label>
          )}

          <button type="submit" disabled={form.formState.isSubmitting || save.isPending}>
            {save.isPending ? 'Zapisywanie…' : 'Zapisz'}
          </button>
        </form>
      </FormProvider>
    </section>
  )
}
