import { describe, expect, it } from 'vitest'

import { DASH, amountTone, formatMinor } from './format'

const NBSP = '\u00a0'

describe('formatMinor', () => {

  it('rozdziela grosze od złotych przecinkiem', () => {

    expect(formatMinor(123_456, 'PLN', 2)).toBe(`1${NBSP}234,56${NBSP}PLN`)
  })

  it('grupuje tysiące niezależnie od danych lokalizacyjnych środowiska', () => {

    // Intl bez pełnych danych ICU cicho gubi separator; ta sama kwota musi
    // wyglądać tak samo w przeglądarce i w teście
    expect(formatMinor(1_234_567_890, 'PLN', 2)).toBe(`12${NBSP}345${NBSP}678,90${NBSP}PLN`)
  })

  it('waluta bez części ułamkowej nie dostaje przecinka', () => {

    // JPY ma minor_unit = 0 — „1234,00 JPY" byłoby po prostu nieprawdą
    expect(formatMinor(1_234, 'JPY', 0)).toBe(`1${NBSP}234${NBSP}JPY`)
  })

  it('kwota ujemna zachowuje znak przed grupowaniem', () => {

    expect(formatMinor(-4_500, 'PLN', 2)).toBe(`-45,00${NBSP}PLN`)
  })

  it('grosze poniżej złotówki dostają zero przed przecinkiem', () => {

    expect(formatMinor(5, 'PLN', 2)).toBe(`0,05${NBSP}PLN`)
  })

  it('trzyma dokładność do końca bezpiecznego zakresu liczb', () => {

    // Number.MAX_SAFE_INTEGER groszy to jakieś 90 bilionów złotych — daleko
    // poza jakikolwiek budżet domowy. Powyżej tej granicy liczba traci
    // dokładność już przy parsowaniu JSON-a, więc formater nie ma czego
    // ratować; tutaj sprawdzamy, że do samej granicy nic nie gubi
    expect(formatMinor(Number.MAX_SAFE_INTEGER, 'PLN', 2)).toBe(
      `90${NBSP}071${NBSP}992${NBSP}547${NBSP}409,91${NBSP}PLN`,
    )
  })

  it('brak kwoty pokazuje myślnik, nie zero', () => {

    expect(formatMinor(null, 'PLN', 2)).toBe(DASH)
    expect(formatMinor(undefined, 'PLN', 2)).toBe(DASH)
  })
})

describe('amountTone', () => {

  it('rozróżnia znak kwoty', () => {

    expect(amountTone(1)).toBe('positive')
    expect(amountTone(-1)).toBe('negative')
    expect(amountTone(0)).toBe('zero')
  })
})
