---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Frontend rules

`apps/web` is ONE React application serving many independent domains. Never split
it into separate applications, never stand a second frontend next to it.

| File | Scope |
|---|---|
| [frontend-architecture.md](frontend-architecture.md) | Layers, module boundaries, feature manifest, registry |
| [frontend-codestyle.md](frontend-codestyle.md) | File naming, TypeScript, styling, forms, performance |
| [frontend-data.md](frontend-data.md) | HTTP, server state, generated DTO types |
| [frontend-testing.md](frontend-testing.md) | Vitest, Testing Library, MSW |

## What enforces what

| Rule | Tool |
|---|---|
| module boundaries | `eslint-plugin-boundaries` (`error`) |
| no `any`, no `@ts-ignore` | `@typescript-eslint` (`error`) |
| `strict`, no type errors | `tsc --noEmit` in `pnpm build` |
| DTO types match the contract | regeneration + `git diff --exit-code` in CI |
| consistent formatting | Prettier (`pnpm format:check`) |

A faithful Polish copy of every rule file lives in `docs/rules-pl/`. When you
change a rule, change it in both places.
