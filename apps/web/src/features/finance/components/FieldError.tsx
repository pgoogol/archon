// Komunikat walidacji pod polem formularza.
//
// `role="alert"` jest tu istotne, nie ozdobne: bez niego czytnik ekranu nie
// ogłosi błędu, który pojawił się po próbie wysłania, a użytkownik zostanie
// z formularzem, który „nic nie robi".

export default function FieldError({ message }: { message?: string }) {

  if (!message) return null
  return (
    <p className="field-error" role="alert">
      {message}
    </p>
  )
}
