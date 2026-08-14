---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Data and state

## HTTP

- No `fetch` in components. All HTTP goes through the client in `shared/http` or
  through `libs/ts/api-client`.
- Never build a URL by string concatenation in a component.

## Server state

- Keep server state in **TanStack Query** — never in `useState` + `useEffect`.
- Build query keys from every parameter that affects the result.
- Invalidate explicitly after a mutation. Never refetch by remounting a component.

## Local state

- Keep UI state in the component. Never hoist it into a global store "for later".

## DTO types

- DTO types are generated from `contracts/openapi/`. Never hand-write an interface
  for an API response — the generated type is the only truth.
- Regenerate with `pnpm --filter @archon/api-client generate`. CI regenerates and
  fails on a diff, so a stale checked-in type breaks the build.
- The contract is written by hand and changes before the implementation; see
  [backend-api.md](backend-api.md).
