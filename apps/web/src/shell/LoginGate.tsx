// Bramka logowania — jedyne, co widać przed zalogowaniem.
//
// Powłoka odpowiada za uwierzytelnianie (tabela warstw w regułach frontu), więc
// bramka stoi tutaj, a nie w domenie. O żadnej domenie nadal nie wie: czyta
// wyłącznie stan z `shared/auth`.
//
// Bramka jest wygodą, nie zabezpieczeniem. Zabezpieczeniem są serwisy, które
// odpowiadają 401 na żądanie bez poświadczeń — obejście tego ekranu w
// przeglądarce niczego nie otwiera.

import { zodResolver } from '@hookform/resolvers/zod'
import { useCallback, useState } from 'react'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'

import GoogleSignIn from '@/shared/auth/GoogleSignIn'
import { useAuth } from '@/shared/auth/AuthContext'
import { loginFormSchema, type LoginFormValues } from '@/shell/loginFormSchema'

const BAD_CREDENTIALS = 'Nieprawidłowy login lub hasło'

const GOOGLE_REJECTED = 'Konto Google nie ma dostępu do tej aplikacji'

export default function LoginGate({ children }: { children: ReactNode }) {

  const { status, config, signInWithPassword, signInWithGoogleToken } = useAuth()
  const [error, setError] = useState<string | null>(null)

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginFormSchema),
    defaultValues: { username: '', password: '' },
  })

  const signInWithGoogle = useCallback(
    (idToken: string) => {

      setError(null)
      signInWithGoogleToken(idToken).catch(() => setError(GOOGLE_REJECTED))
    },
    [signInWithGoogleToken],
  )

  if (status === 'checking') {

    return <p className="loading">Wczytywanie…</p>
  }
  if (status === 'authenticated') {

    return <>{children}</>
  }

  const submit = form.handleSubmit(async (values) => {

    setError(null)
    try {
      await signInWithPassword(values.username, values.password)
    } catch {
      setError(BAD_CREDENTIALS)
    }
  })

  const googleClientId = config?.googleClientId ?? ''
  const localAccountEnabled = config?.localAccountEnabled ?? false

  return (
    <div className="login-gate">
      <HudRings />

      <section className="hud-core" aria-labelledby="login-title">
        <p className="hud-eyebrow">system dostępu</p>
        <h1 id="login-title" className="hud-title">archon</h1>

        {localAccountEnabled && (
          <form className="hud-form" onSubmit={submit} noValidate>
            <label className="hud-field">
              <span>Login</span>
              <input type="text" autoComplete="username" {...form.register('username')} />
              <FieldError message={form.formState.errors.username?.message} />
            </label>
            <label className="hud-field">
              <span>Hasło</span>
              <input type="password" autoComplete="current-password" {...form.register('password')} />
              <FieldError message={form.formState.errors.password?.message} />
            </label>
            <button type="submit" className="hud-submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Weryfikacja…' : 'Zaloguj'}
            </button>
          </form>
        )}

        {localAccountEnabled && googleClientId !== '' && (
          <p className="hud-divider"><span>albo</span></p>
        )}

        {googleClientId !== '' && (
          <GoogleSignIn
            clientId={googleClientId}
            onCredential={signInWithGoogle}
            onError={setError}
          />
        )}

        <p className="hud-status" role="status">{error}</p>
      </section>
    </div>
  )
}

/** Komunikat walidacji pod polem. Treść pochodzi ze słownika w `shared/`. */
function FieldError({ message }: { message?: string }) {

  if (message === undefined) {

    return null
  }
  return <span className="hud-field-error">{message}</span>
}

/**
 * Pierścienie rdzenia. Same z siebie nic nie znaczą, więc są ukryte przed
 * czytnikiem ekranu; ruch wyłącza się przy ograniczonym ruchu w systemie.
 */
function HudRings() {

  return (
    <svg className="hud-rings" viewBox="0 0 320 320" aria-hidden="true" focusable="false">
      <g filter="url(#crt-glow)">
        <circle className="hud-ring hud-ring-outer" cx="160" cy="160" r="150" />
        <circle className="hud-ring hud-ring-mid" cx="160" cy="160" r="122" />
        <circle className="hud-ring hud-ring-inner" cx="160" cy="160" r="96" />
      </g>
    </svg>
  )
}
