import { z } from 'zod'

// The login endpoint only needs a non-blank password of at most 128 characters; the password policy belongs to registration
// and a wrong password must read as wrong credentials (api-contract 7.1).
export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email address.')
    .max(255, 'The email address is too long.')
    .refine((value) => z.email().safeParse(value).success, 'Enter a valid email address, such as name@company.com.'),
  password: z.string().min(1, 'Enter your password.').max(128, 'Passwords are at most 128 characters.'),
})

export type LoginValues = z.infer<typeof loginSchema>

export type LoginFieldErrors = Partial<Record<keyof LoginValues, string>>

export function validateLogin(values: { email: string; password: string }): { ok: true; values: LoginValues } | { ok: false; errors: LoginFieldErrors } {
  const result = loginSchema.safeParse(values)
  if (result.success) return { ok: true, values: result.data }
  const errors: LoginFieldErrors = {}
  for (const issue of result.error.issues) {
    const key = issue.path[0] as keyof LoginValues
    if (!errors[key]) errors[key] = issue.message
  }
  return { ok: false, errors }
}
