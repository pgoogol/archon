import { describe, expect, it } from 'vitest'
import { applyParams, buildHash, parseHash } from '@/shared/hooks/useHashRoute'

describe('parseHash', () => {

  it('czyta domenę, ekran i parametry z adresu', () => {

    const route = parseHash('#/music/library?q=salsa&sort=BPM')

    expect(route.featureId).toBe('music')
    expect(route.path).toBe('library')
    expect(route.params.get('q')).toBe('salsa')
    expect(route.params.get('sort')).toBe('BPM')
  })

  it('sam identyfikator domeny znaczy jej ekran domyślny', () => {

    const route = parseHash('#/music')

    expect(route.featureId).toBe('music')
    expect(route.path).toBe('')
  })

  it('pusty adres nie wskazuje żadnej domeny — powłoka wybiera pierwszą z rejestru', () => {

    expect(parseHash('').featureId).toBe('')
    expect(parseHash('#/').featureId).toBe('')
  })

  it('zagnieżdżony ekran zostaje ścieżką względną wobec domeny', () => {

    const route = parseHash('#/music/sets/planer?set=7')

    expect(route.featureId).toBe('music')
    expect(route.path).toBe('sets/planer')
    expect(route.params.get('set')).toBe('7')
  })
})

describe('buildHash', () => {

  it('pomija znak zapytania, gdy nie ma parametrów', () => {
    expect(buildHash('music', 'sets', new URLSearchParams())).toBe('#/music/sets')
  })

  it('pusty ekran daje sam adres domeny', () => {
    expect(buildHash('music', '', new URLSearchParams())).toBe('#/music')
  })

  it('dokleja parametry do adresu ekranu', () => {
    expect(buildHash('music', 'library', new URLSearchParams({ q: 'salsa' })))
      .toBe('#/music/library?q=salsa')
  })
})

describe('applyParams', () => {

  it('nadpisuje wskazane parametry, resztę zostawia', () => {

    const next = applyParams(new URLSearchParams({ q: 'salsa', page: '2' }), { page: 0 })

    expect(next.get('q')).toBe('salsa')
    expect(next.get('page')).toBe('0')
  })

  it('puste i niezdefiniowane wartości usuwa z adresu', () => {

    const next = applyParams(new URLSearchParams({ q: 'salsa', genre: 'LATIN' }), {
      q: '',
      genre: undefined,
    })

    expect(next.toString()).toBe('')
  })
})
