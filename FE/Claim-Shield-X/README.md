# ClaimShield X — web app

The investigator's workspace for ClaimShield X: claims, their documents, live investigation runs, the deterministic checks, the earlier claims that relate to each claim, the evidence a run froze, the report with its review-priority score, and the recorded human decision. It talks to the ClaimShield X backend (Spring Boot) and shows only what that API really returns. Nothing in it decides anything about a claim.

## Stack

React 19, Vite, TypeScript (strict), React Router (data router), TanStack Query, zod (every response is validated), CSS Modules with design tokens, self-hosted fonts. Tests: Vitest, Testing Library and MSW for units; Playwright with real Chrome and axe for the browser.

## Run it locally

```bash
npm install
cp .env.example .env.local      # public settings only; never put a secret in a VITE_ variable
# start the backend (default http://localhost:8080), then:
npm run dev                      # http://localhost:5173, /api is forwarded to VITE_DEV_API_TARGET
```

The backend sets no CORS headers and its refresh cookie is `SameSite=Strict; Secure`, so the browser must see **one origin**: in development Vite forwards `/api`, in production `server/server.mjs` does.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with the `/api` forward |
| `npm run build` | Type-check and build to `dist/` |
| `npm run lint` / `npm run typecheck` | ESLint / TypeScript |
| `npm test` | Unit and component tests, then the production-server tests |
| `npm run test:e2e` | Browser tests against a **real backend** (see below) |
| `npm start` | Serve `dist/` and forward `/api` (needs `BACKEND_URL`) |

### Browser tests

They need a backend on `E2E_API_TARGET` (default `http://localhost:18080`) with four organizations named `Org A`, `Org B`, `Org E` and `Org H` and a database you can throw away. Run `e2e/seed-history.sh` once to load the synthetic claim history into `Org H` (it needs `psql` access to that database; the report tests skip with a clear message if it was not run). The daily investigation limit (20 per organization) is lower than a full run starts, so start the scratch backend with `--app.investigation.daily-limit-per-organization=1000`. Every test makes its own users and claims, with synthetic data only. Only the report tests call a model (the backend's own explanation steps, three calls for one shared investigation), and only on synthetic data.

```bash
npx playwright test                                   # the app through the dev server
npm run build && npx playwright test -c playwright.production.config.ts   # the built app, through the production server and its security policy
```

## Deploy to Google Cloud Run

One container serves the app and forwards `/api` to the backend, so the browser stays on one origin and the refresh cookie works. (Firebase Hosting is not suitable: it strips every cookie except `__session` and ends proxied requests at 60 seconds, while reading a document can take about three minutes.)

1. Deploy the backend first and note its URL. It must be reachable from this service; it protects itself with its own JWTs.
2. Deploy the web app (builds the `Dockerfile` with Cloud Build):

```bash
gcloud run deploy claimshield-web \
  --source . \
  --region <region> \
  --allow-unauthenticated \
  --set-env-vars BACKEND_URL=https://<backend-url> \
  --timeout 300 \
  --memory 256Mi \
  --max-instances 3
```

| Variable | Meaning |
|---|---|
| `BACKEND_URL` | Required. Origin of the backend, for example `https://claimshield-api-xxxx.a.run.app` |
| `PORT` | Set by Cloud Run (default 8080) |
| `PROXY_TIMEOUT_MS` | How long to wait for the backend (default 240000, a little over the longest document read) |

The server sends a strict Content-Security-Policy (everything same-origin; `blob:` only for image previews), `nosniff`, `no-referrer`, `frame-ancestors 'none'` and, behind HTTPS, HSTS. `GET /healthz` answers `ok`.

## What is real and what is not yet

Real, from the backend: sign-in and sessions, claims, documents and the stage checklist, document reading, investigation runs (stages, waiting for a missing document, retry, cancel), the eight deterministic rules, earlier-claim matching, the frozen evidence and relationships, findings (rule results, evidence gaps, model hypotheses that survived an independent review), the report with a transparent review-priority score, recorded human decisions and the audit history. Not in the app yet: the historical CSV import screen (the backend has it, for administrators only) and sign-up or password reset (the backend allows open self-registration, which this app deliberately does not advertise).
