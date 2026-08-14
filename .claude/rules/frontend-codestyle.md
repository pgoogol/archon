---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Frontend code style

## File naming

| Kind | Convention | Example |
|---|---|---|
| Component | PascalCase.tsx | `LibraryTable.tsx` |
| Route | PascalCase + `Route.tsx` | `LibraryRoute.tsx` |
| Hook | `use` + camelCase.ts | `useDebouncedParam.ts` |
| Non-component module | camelCase.ts | `setPlanner.ts` |
| Test | `<name>.test.ts(x)` next to the code | `setPlanner.test.ts` |
| Directory | lowercase, kebab-case when multi-word | `features/music/`, `shared/ui-kit/` |

Keep a test next to the file it tests, never in a separate `__tests__` tree.

## TypeScript

- `strict: true` in every `tsconfig.json`. Never disable a flag from `strict`.
- No `any` — `@typescript-eslint/no-explicit-any: error`. When a type is genuinely
  unknown, use `unknown` and narrow it.
- Never use `as` to silence a type error. `as const` and narrowing after validation
  are fine.
- No `@ts-ignore`; `@ts-expect-error` only with a comment explaining why.
- Prefer `type` for unions and `interface` for object shapes that get extended.

## Styling

- A domain never defines its own colours, spacing, radii or shadows. Take them from
  the tokens in `shared/`. A literal value in a domain (`#3b82f6`, `padding: 13px`)
  is an error.
- Add a new token to `shared/`, never to a domain.
- Take icons and UI primitives from the ui-kit. Never duplicate a button in a domain.

## Forms

- Build on `react-hook-form` with a `zod` schema wired through the resolver.
- Keep the schema next to the form, inside the domain. Validate on the schema,
  never with conditions scattered across `onChange`.
- Derive the form value type from the schema (`z.infer`) — never write it twice.
- Take error messages from i18n in `shared/`, never inline them in a component.
- Never gate the submit button on `isValid` alone — handle `isSubmitting` too.

## Performance

- Split the bundle by domain — `lazy()` on routes does this automatically.
- Add `memo`, `useMemo` and `useCallback` after measuring a problem, never
  preventively.
- Never define a component inside a parent's render — it remounts on every render.
- Virtualize lists beyond roughly 200 rows instead of rendering them whole.
- Never import a whole library for one symbol (`import _ from 'lodash'`).
