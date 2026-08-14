// Granice modułów są tu wymuszane jako BŁĄD, nie ostrzeżenie: warstwy z
// .claude/rules/frontend-architecture.md są warte tyle, ile narzędzie, które
// ich pilnuje. Naruszenie granicy oznacza, że podział jest zły — nie że trzeba
// dopisać eslint-disable.

import js from '@eslint/js'
import boundaries from 'eslint-plugin-boundaries'
import tseslint from 'typescript-eslint'

const element = (type, captured) => ({ element: captured ? { type, captured } : { type } })

export default tseslint.config(
  { ignores: ['dist/**', 'coverage/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'boundaries/include': ['src/**/*.{ts,tsx}'],
      // bez resolvera alias @/ nie zostaje rozwiązany, importy nie są
      // klasyfikowane i CAŁA reguła granic milczy — sprawdzone naruszeniem
      'import/resolver': {
        typescript: { project: './tsconfig.json' },
      },
      'boundaries/elements': [
        { type: 'registry', pattern: 'src/registry/**' },
        { type: 'shell', pattern: 'src/shell/**' },
        { type: 'shared', pattern: 'src/shared/**' },
        { type: 'feature', pattern: 'src/features/*', capture: ['domain'] },
        { type: 'root', pattern: 'src/*.{ts,tsx}', partialMatch: false },
      ],
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': [
        'error',
        { 'ts-ignore': true, 'ts-expect-error': 'allow-with-description' },
      ],
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            {
              // powłoka zna rejestr i wspólne klocki — nigdy pojedynczej domeny
              from: [element('shell')],
              allow: [
                { to: element('shared') },
                { to: element('registry') },
                { to: element('shell') },
              ],
            },
            {
              // wspólne nie zna ani domen, ani powłoki
              from: [element('shared')],
              allow: [{ to: element('shared') }],
            },
            {
              // domena zna wspólne (w tym kontrakt manifestu) i samą siebie
              from: [element('feature')],
              allow: [
                { to: element('shared') },
                { to: element('feature', { domain: '{{from.domain}}' }) },
              ],
            },
            {
              // rejestr istnieje po to, żeby znać domeny
              from: [element('registry')],
              allow: [{ to: element('feature') }, { to: element('shared') }],
            },
            {
              from: [element('root')],
              allow: [{ to: element('shell') }, { to: element('shared') }],
            },
          ],
        },
      ],
    },
  },
)
