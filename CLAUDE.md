# CLAUDE.md

> Note: `finsight-frontend/` (the legacy React app) was removed from this repository. It was reference-only and the Vaadin UI in `finsight-backend/` is the target application.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Two independent projects, no root build:

- `finsight-backend/` — Spring Boot 3.5 / Java 21 / Maven app. Serves the REST API **and** a server-side Vaadin 24 UI from the same process.
- `finsight-frontend/` — React 18 + Vite + TypeScript SPA (Tailwind, React Query, Zustand, axios) that talks to the same REST API.

The two UIs are parallel clients of the same backend: the Vaadin views (`com.finsight.ui`) call Spring services directly, while the React app goes through `/api/v1`. Many Vaadin views are still `FeaturePlaceholderView` stubs.

## Commands

Backend (run from `finsight-backend/`; there is no Maven wrapper checked in, so use a local `mvn`):

```sh
mvn spring-boot:run                          # run on :8080 (profile defaults to dev)
mvn package -DskipTests                      # build jar
mvn test                                     # all tests
mvn test -Dtest=TransactionServiceTest       # single test class
mvn test -Dtest=TransactionServiceTest#methodName   # single test method
```

- Requires PostgreSQL at `localhost:5432/finsight` (override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Other env vars: `SPRING_PROFILES_ACTIVE` (`dev`/`prod`), `FINSIGHT_JWT_SECRET`, `FINSIGHT_MODEL_DIR`.
- `TransactionApiIntegrationTest` uses Testcontainers (`postgres:16-alpine`) and is skipped automatically when Docker isn't available.
- Swagger UI is at `/swagger-ui.html` (springdoc).
- `src/main/frontend/generated/` is Vaadin-generated output; don't edit it by hand.

Frontend (run from `finsight-frontend/`):

```sh
npm run dev       # Vite on :5173, proxies /api -> http://localhost:8080
npm run build     # tsc type-check + vite build
npm run lint
```

`@` is aliased to `src/`. No frontend test runner is configured.

## Backend architecture

Layering under `com.finsight`: `api` (controllers, request/response DTOs as records, exceptions + `GlobalExceptionHandler`) → `service` (by domain: `auth`, `category`, `transaction`, `privacy`) → `repository` (Spring Data JPA) → `domain` (entities, enums). `ui` holds Vaadin views/layout.

**Two security filter chains** in `config/SecurityConfig.java`:
1. `/api/**` — stateless, CSRF disabled, JWT via `security/JwtAuthenticationFilter`; only `/api/v1/auth/**` is public. Access tokens 15 min, refresh tokens 7 days (stored in `refresh_token` table). Controllers obtain the user with `@AuthenticationPrincipal AppUser`.
2. Everything else — Vaadin session-based login (`LoginView`). Views need `@PermitAll` / `@AnonymousAllowed` annotations.

The React client (`src/api/client.ts`) attaches the bearer token from the Zustand `authStore` and transparently refreshes on 401 via `/api/v1/auth/refresh`, queueing concurrent requests.

**Database**: schema is owned by Flyway (`src/main/resources/db/migration/V*__*.sql`) and Hibernate runs with `ddl-auto: validate`, so any entity change needs a new migration file; never edit an already-applied migration.

**Transaction domain rules** (enforced in `TransactionService`, keep them when adding features):
- All queries are scoped by owner (`findByIdAndUserIdAndDeletedAtIsNull`); missing or foreign records raise `ResourceNotFoundException`.
- Deletion is soft (`deletedAt`), and also clears `includedInReports`.
- New transactions start `PENDING` and excluded from reports. Only `APPROVED` transactions may be included in reports (409 otherwise); moving away from `APPROVED` forces exclusion.
- Page size is capped at 100; ordering is fixed to `transactionDate desc, ingestedAt desc`.
- Every mutation records an event through `service/privacy/AuditLogger`.

**Privacy contract**: the app stores `redactedDescription`, not raw descriptions. Audit event descriptions and logs must contain only operational/structural info (event type, entity type, id), never amounts, account numbers, descriptions, tokens or other personal data. Error responses never include messages/stack traces (`server.error.*: never`).

`application.yml` already has config for planned features not yet implemented (`finsight.ml.*` confidence thresholds/retraining, `ConfidenceTier`, `AnomalyFlag`, `IngestionSource` enums, `IngestionException`); build on these rather than inventing parallel settings.
