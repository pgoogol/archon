---
paths:
  - "services/**"
  - "libs/java/**"
---

# API contract

- `contracts/openapi/` is the source of truth and is written by hand.
- Change the contract before implementing, not after.
- A contract test in the service compares the specification generated from the
  code against the file in `contracts/openapi/` and fails the build on drift.
- Never "fix" that test by overwriting the contract file to match the code —
  decide which side is wrong.
- Frontend DTO types are generated from the contract and never written by hand;
  see [frontend-data.md](frontend-data.md).
