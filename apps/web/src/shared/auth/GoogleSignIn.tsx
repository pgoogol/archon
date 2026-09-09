import { useEffect, useRef, useState } from 'react'

import { loadGoogleIdentity, type GoogleCredentialResponse } from '@/shared/auth/google'

const BUTTON_WIDTH = 280

interface GoogleSignInProps {
  clientId: string
  onCredential: (idToken: string) => void
  onError: (message: string) => void
}

/**
 * Przycisk logowania Google. Renderuje go biblioteka Google, nie my — własny
 * przycisk wywołujący jej API łamie wytyczne marki i bywa blokowany.
 */
export default function GoogleSignIn({ clientId, onCredential, onError }: GoogleSignInProps) {

  const target = useRef<HTMLDivElement>(null)
  const [unavailable, setUnavailable] = useState(false)

  useEffect(() => {

    let active = true
    loadGoogleIdentity()
      .then((identity) => {

        if (!active || target.current === null) {

          return
        }
        identity.initialize({
          client_id: clientId,
          callback: (response: GoogleCredentialResponse) => {

            if (response.credential === undefined) {

              onError('Google nie zwróciło tokenu')
              return
            }
            onCredential(response.credential)
          },
        })
        identity.renderButton(target.current, {
          theme: 'filled_black',
          size: 'large',
          text: 'signin_with',
          shape: 'pill',
          width: BUTTON_WIDTH,
        })
      })
      .catch(() => {

        if (active) {

          setUnavailable(true)
        }
      })
    return () => {

      active = false
    }
  }, [clientId, onCredential, onError])

  if (unavailable) {

    return <p className="hud-note">Logowanie Google niedostępne</p>
  }
  return <div ref={target} />
}
