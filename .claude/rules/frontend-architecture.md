---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Frontend architecture

## Layers

| Layer | Responsibility | What is NOT there |
|---|---|---|
| `src/shell/` | layout, navigation, routing, auth, error boundary | the name of any domain |
| `src/shared/` | ui-kit, tokens, http client, hooks, i18n | domain logic |
| `src/features/<domain>/` | routes, components, api, state, tests | knowledge of other domains |
| `src/features/registry.ts` | list of manifests | anything but imports and an array |

Never write a domain name into `shell/` or `shared/`. The shell reads the registry
and the manifests, nothing else. If adding a domain requires a change in `shell/`,
stop and report it as a shell design defect — do not work around it locally.

## Module boundaries

Enforce them with `eslint-plugin-boundaries` at **`error`** level, configured in
`apps/web/eslint.config.js`:

```js
settings: {
  'boundaries/elements': [
    { type: 'registry', pattern: 'src/features/registry.ts', mode: 'file' },
    { type: 'shell',    pattern: 'src/shell/**' },
    { type: 'shared',   pattern: 'src/shared/**' },
    { type: 'feature',  pattern: 'src/features/*', capture: ['domain'] },
  ],
},
rules: {
  'boundaries/element-types': ['error', {
    default: 'disallow',
    rules: [
      { from: 'shell',    allow: ['shared', 'registry'] },
      { from: 'shared',   allow: ['shared'] },
      { from: 'feature',  allow: ['shared', ['feature', { domain: '${from.domain}' }]] },
      { from: 'registry', allow: ['feature'] },
    ],
  }],
  'boundaries/no-private': 'error',
}
```

That gives four prohibitions, each a build error: features never import each other ·
`shared/` never imports from `features/` or `shell/` · `shell/` never imports from
`features/` except through the registry · nothing reaches into another domain's
internals past its `index.ts`.

A boundary violation is an error, never a warning. Never add `eslint-disable` to an
import that crosses a boundary — it means the layering is wrong.

## Feature manifest

Every domain exports exactly one public thing — a manifest in `index.ts`. The rest
of the directory is private.

```ts
// src/features/music/index.ts
import { lazy } from 'react'
import type { FeatureManifest } from '@/shell/featureManifest'
import { DiscIcon } from '@/shared/ui/icons'

export const musicFeature: FeatureManifest = {
  id: 'music',
  title: 'Muzyka',
  icon: DiscIcon,
  basePath: '/music',
  routes: [
    { path: '', Component: lazy(() => import('./routes/OverviewRoute')) },
    { path: 'library', Component: lazy(() => import('./routes/LibraryRoute')) },
  ],
  nav: [
    { label: 'Przegląd', to: '' },
    { label: 'Biblioteka', to: 'library' },
  ],
}
```

```ts
// src/features/registry.ts — the only place that knows every domain
import { musicFeature } from './music'

export const features = [musicFeature] as const
```

- Load every route through `lazy()` — never a static import. A statically imported
  route pulls the whole domain into the main bundle and defeats the registry.
- Write paths in `routes` and `nav` **relative to `basePath`**, never absolutely.
  A domain that knows its absolute prefix stops being portable.
- Adding a domain is one new directory plus one line in the registry. Nothing else.
