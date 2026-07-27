# CLAUDE.md

## Commands
- `pnpm dev` — Start development server
- `pnpm build` — Production build
- `pnpm lint-fix` — Lint and fix all packages

## Architecture
- `packages/client` — Vue 3 frontend
- `packages/server` — Express API
- `packages/shared` — Types, DTOs, utilities

## Coding Standards
- TypeScript strict mode, no `any` types
- Absolute imports only (no `../../`)
- API payloads: declare as typed constants

## PR Review Rules
1. **Type Safety** — Never use `any`. Typed constants for API payloads
2. **Dead Code** — Remove unused imports, variables, i18n keys
3. **Error Handling** — Wrap all API calls in try/catch

## Critical Gotchas
- Always use projection in DB queries — never fetch entire documents
- Server error messages MUST come from constant files