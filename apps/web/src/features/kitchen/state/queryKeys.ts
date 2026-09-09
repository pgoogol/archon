// Klucze zapytań w jednym miejscu: unieważnienie po zapisie musi trafić w ten
// sam klucz, którym pobrano dane, a klucz wpisany z pamięci w dwóch plikach
// rozjeżdża się przy pierwszej zmianie.

export const kitchenKeys = {

  dictionaries: () => ['kitchen', 'dictionaries'] as const,

  recipes: () => ['kitchen', 'recipes'] as const,
  recipeList: (page: number) => ['kitchen', 'recipes', 'list', page] as const,
  recipe: (id: number) => ['kitchen', 'recipes', id] as const,
  notes: (recipeId: number) => ['kitchen', 'recipes', recipeId, 'notes'] as const,
}
