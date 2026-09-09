import { z } from 'zod'

import { validationMessages } from '@/shared/validationMessages'

export const loginFormSchema = z.object({
  username: z.string().min(1, validationMessages.required),
  password: z.string().min(1, validationMessages.required),
})

export type LoginFormValues = z.infer<typeof loginFormSchema>
