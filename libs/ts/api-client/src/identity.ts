// Typ DTO generowany z kontraktów. Nigdy nie pisz go ręcznie — regeneruj:
//
//   pnpm --filter @archon/api-client generate
//
// Punkt wejścia @archon/api-client/identity — wolny od nazwy domeny, bo
// endpoint /auth/whoami nie należy do żadnej. Wystawia go wspólny starter,
// więc oba kontrakty opisują go identycznie; typ bierzemy z jednego z nich,
// żeby nie było dwóch nazw na jeden kształt.

import type { components } from './music.generated'

export type WhoAmIResponse = components['schemas']['WhoAmIResponse']
