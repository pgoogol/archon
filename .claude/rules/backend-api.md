---
paths:
  - "services/**"
  - "libs/java/**"
---

# API contract

- `contracts/openapi/` is the source of truth and is written by hand.
- Change the contract before implementing, not after.
- **Every path starts with `/<service>/api/v<n>`** — `/music/api/v1/library`,
  `/finance/api/v1/accounts`. The service name up front gives the proxy a prefix
  that is not contained in its neighbour's: adding a service is one rule, not a
  review of the order of the others. The version is part of the path from the
  first endpoint, not something added once a breaking change comes up —
  introducing it later means touching every client at once.
- Bump the version only for a breaking change: a removed field, a narrowed type,
  a changed meaning of a response. A new optional field and a new endpoint fit in
  the version that already exists.
- `/actuator` and `/v3/api-docs` stay outside that prefix — they are not domain API.
- A contract test in the service compares the specification generated from the
  code against the file in `contracts/openapi/` and fails the build on drift.
- Never "fix" that test by overwriting the contract file to match the code —
  decide which side is wrong.
- Frontend DTO types are generated from the contract and never written by hand;
  see [frontend-data.md](frontend-data.md).
