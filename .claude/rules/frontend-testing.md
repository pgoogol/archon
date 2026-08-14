---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Frontend testing

- Vitest + Testing Library, next to the code under test.
- Route HTTP through **MSW** always. Never mock `fetch` by hand and never stub the
  `api` module — test what will actually go over the wire.
- Query by role and accessible name (`getByRole`), never by `data-testid` or a CSS
  class.
- Use `findBy*` instead of `waitFor` with a hand-written condition.
- Use `userEvent`, not `fireEvent`, for user interactions.
- A domain's tests never import from another domain — the same boundary applies to
  test code as to production code.
- Test behaviour through the rendered UI, not by calling a hook's internals.
