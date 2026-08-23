// Most między błędem zapytania a paskiem powiadomień.
//
// TanStack Query trzyma błąd w stanie zapytania i wystawia go przy każdym
// renderze; toast ma się pokazać raz na błąd, nie raz na render. Efekt zależny
// od samego obiektu błędu daje dokładnie to: dopóki zapytanie nie zmieni wyniku,
// referencja jest ta sama i powiadomienie nie wraca.

import { useEffect } from 'react'

import { useToast } from '@/shared/ui/Toasts'

export function useQueryErrorToast(error: unknown, fallbackText: string): void {

  const { reportError } = useToast()

  useEffect(() => {
    if (!error) return
    reportError(error, fallbackText)
  }, [error, fallbackText, reportError])
}
