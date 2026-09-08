// Klucze zapytań w jednym miejscu: unieważnienie po zapisie musi trafić w ten
// sam klucz, którym pobrano dane, a klucz wpisany z pamięci w dwóch plikach
// rozjeżdża się przy pierwszej zmianie.

export const kitchenKeys = {

  dictionaries: () => ['kitchen', 'dictionaries'] as const,
}
