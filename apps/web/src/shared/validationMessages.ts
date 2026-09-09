// Teksty błędów walidacji — jedno miejsce dla całego frontu.
//
// Reguła frontowa każe brać je z i18n w `shared/`, a nie wpisywać w komponencie.
// Pełnego i18n w tym repozytorium nie ma (aplikacja jest jednojęzyczna), więc
// warstwą jest ten słownik: komunikat nie siedzi w schemacie ani w polu
// formularza, a zmiana brzmienia to jedno miejsce, nie grep po domenach.
// Gdy dojdzie druga wersja językowa, to jest plik do podmiany.

export const validationMessages = {
  required: 'Pole jest wymagane',
  requiredDate: 'Podaj datę',
  integer: 'Podaj liczbę całkowitą',
  number: 'Podaj liczbę',
  positiveAmount: 'Kwota musi być większa od zera',
  nonNegativeAmount: 'Kwota nie może być ujemna',
  currencyCode: 'Kod waluty ma trzy wielkie litery',
  dayOfMonth: 'Dzień miesiąca mieści się w zakresie 1–31',
  rangeReversed: 'Data początkowa jest późniejsza niż końcowa',
  selectOption: 'Wybierz wartość z listy',
} as const
