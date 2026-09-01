# Phase 14 - Frontend build and route-guard verification

## Production build

Commands:

```powershell
cd client
npm ci
$env:VITE_API_BASE_URL='https://backend.example.invalid'
npm run build
```

Result on 2026-09-01: TypeScript validation and the Vite production build
completed successfully. The reserved `.invalid` origin was used only to verify
the build-time contract; deployment must inject the real backend origin.

Vite reported a non-blocking warning that the main JavaScript chunk exceeds
500 kB. This is a performance follow-up, not a route-guard or build failure.

## Route-guard smoke review

The active router at `src/app/router/AppRouter.tsx` and its two active guards
were checked against the following matrix:

| Route family | Required client-side gate | Denial behavior |
| --- | --- | --- |
| `/admin/**` | authenticated `ADMIN` | unauthenticated to `/login`; wrong role to `/403` |
| shared `/app/**` | authenticated `STUDENT` or `LAB_MANAGER` | unauthenticated to `/login`; wrong role to `/403` |
| student booking/penalty routes | authenticated `STUDENT` plus active membership | missing membership to `/app/labs` |
| research routes | authenticated `STUDENT` or `LAB_MANAGER`; active membership unless manager override applies | missing membership to `/app/labs` |
| manager lab operations | authenticated `LAB_MANAGER` | wrong role to `/403` |
| unknown route | no privileged fallback | redirect to `/404` |

The guards supplement server authorization; they are not treated as an
authorization boundary.

The login `returnUrl` previously accepted an arbitrary value from the query
string and passed it directly to navigation. It now accepts only same-origin
absolute paths beginning with one `/`, rejects protocol-relative and
backslash-based targets, and otherwise falls back to the role-derived home
path. This prevents the reviewed redirect input from bypassing the intended
route boundary.

## Dependency audit

`axios` was refreshed from 1.16.1 to 1.20.0 and its `form-data` dependency from
4.0.5 to 4.0.6. `react-router-dom` was refreshed within the existing major from
6.30.3 to 6.30.6. A subsequent `npm audit --omit=dev` reports:

- 0 critical vulnerabilities;
- 0 high vulnerabilities;
- 2 moderate advisories in React Router.

The remaining automated fix requires a breaking React Router 7 upgrade. The
current application is client-rendered and does not use the SSR hydration path
named by one advisory; the user-controlled login redirect path implicated by
the navigation advisory is constrained as described above. A major router
migration is intentionally left outside this focused verification task.

## Existing lint limitation

`npm run lint` cannot start because this repository declares ESLint 9 but does
not contain an `eslint.config.js`, `.mjs`, or `.cjs` file. No lint result is
claimed. The required Phase 14 production build itself passes.
