---
paths:
  - "apps/web/**"
  - "libs/ts/**"
---

# Frontend architecture

## Layers

| Layer | Responsibility | What is NOT there |
|---|---|---|
| `src/shell/` | layout, navigation, routing host, auth, error boundary | the name of any domain |
| `src/shared/` | ui-kit, tokens, http client, hooks, i18n, manifest contract | domain logic |
| `src/features/<domain>/` | routes, components, api, state, tests | knowledge of other domains |
| `src/registry/` | list of manifests | anything but imports and an array |

Never write a domain name into `shell/` or `shared/`. The shell reads the registry
and the manifests, nothing else. If adding a domain requires a change in `shell/`,
stop and report it as a shell design defect — do not work around it locally.

The registry sits at the top level, not inside `features/`: it is the one module
allowed to know every domain, so it must not look like a domain itself.

`FeatureManifest` lives in `shared/`, not in `shell/`. Both the shell and every
domain depend on it, and `shared/` is the layer everyone may depend on — putting
it in `shell/` would force a `feature → shell` edge that nothing else needs.

## Module boundaries

Enforce them with `eslint-plugin-boundaries` at **`error`** level, configured in
`apps/web/eslint.config.js`:

```js
const element = (type, captured) => ({ element: captured ? { type, captured } : { type } })

settings: {
  'boundaries/include': ['src/**/*.{ts,tsx}'],
  // without a resolver the @/ alias is never resolved, no import gets
  // classified, and the whole rule silently passes everything
  'import/resolver': { typescript: { project: './tsconfig.json' } },
  'boundaries/elements': [
    { type: 'registry', pattern: 'src/registry/**' },
    { type: 'shell',    pattern: 'src/shell/**' },
    { type: 'shared',   pattern: 'src/shared/**' },
    { type: 'feature',  pattern: 'src/features/*', capture: ['domain'] },
    { type: 'root',     pattern: 'src/*.{ts,tsx}', partialMatch: false },
  ],
},
rules: {
  'boundaries/dependencies': ['error', {
    default: 'disallow',
    policies: [
      { from: [element('shell')],
        allow: [{ to: element('shared') }, { to: element('registry') }, { to: element('shell') }] },
      { from: [element('shared')],
        allow: [{ to: element('shared') }] },
      { from: [element('feature')],
        allow: [{ to: element('shared') },
                { to: element('feature', { domain: '{{from.domain}}' }) }] },
      { from: [element('registry')],
        allow: [{ to: element('feature') }, { to: element('shared') }] },
      { from: [element('root')],
        allow: [{ to: element('shell') }, { to: element('shared') }] },
    ],
  }],
}
```

That gives four prohibitions, each a build error: features never import each other ·
`shared/` never imports from `features/` or `shell/` · `shell/` never imports from
`features/` except through the registry · nothing reaches into another domain past
its `index.ts` (a deep path still classifies as that domain, so the rule catches it).

**Elements are matched as folders.** A `pattern` naming a single file does not
classify that file — every layer must be a directory, which is why the registry
and the manifest contract live where they do.

**A boundaries config that resolves nothing reports nothing.** After changing it,
prove it still bites: add an import that must fail, run the lint, delete it. A rule
you have never seen fail is decoration, not enforcement.

A boundary violation is an error, never a warning. Never add `eslint-disable` to an
import that crosses a boundary — it means the layering is wrong.

## Feature manifest

Every domain exports exactly one public thing — a manifest in `index.ts`. The rest
of the directory is private.

```ts
// src/features/music/index.ts
import { lazy } from 'react'
import type { FeatureManifest } from '@/shared/featureManifest'
import { MusicWorkspaceProvider } from '@/features/music/state/MusicWorkspace'

export const musicFeature: FeatureManifest = {
  id: 'music',
  title: 'music-view',
  basePath: '/music',
  Provider: MusicWorkspaceProvider,
  routes: [
    { path: '', Component: lazy(() => import('./routes/LibraryRoute')) },
    { path: 'sets', Component: lazy(() => import('./routes/SetsRoute')) },
  ],
  nav: [
    { label: 'Biblioteka', to: '' },
    { label: 'Sety', to: 'sets' },
  ],
}
```

```ts
// src/registry/index.ts — the only place that knows every domain
import { musicFeature } from '@/features/music'

export const features: FeatureManifest[] = [musicFeature]
```

- Load every route through `lazy()` — never a static import. A statically imported
  route pulls the whole domain into the main bundle and defeats the registry.
- Write paths in `routes` and `nav` **relative to `basePath`**, never absolutely.
  A domain that knows its absolute prefix stops being portable.
- State shared between a domain's own screens goes in its `Provider`, never in the
  shell. The shell renders the provider without knowing what is inside it.
- Adding a domain is one new directory plus one line in the registry. Nothing else.

## Routing

The address carries the domain and the screen: `#/<domain>/<screen>?<filters>`.
The shell resolves the domain from the registry and the screen from that domain's
manifest; an unknown domain falls back to the first entry in the registry.

The routing hook lives in `shared/hooks/`, not in `shell/` — domains read and write
their own filter state through it, and a domain may not import from the shell.
