# CLAUDE.md — MarketHub (PFE)

Multi-vendor marketplace with order management and an AI module. Monorepo, one folder per service.
Architecture and decisions: `docs/architecture.md`. Schema: `docs/erd.md`.

## Layout

| Path | Service | Stack |
|---|---|---|
| `backend/` | Main API | Java 21, Spring Boot 3, Maven, PostgreSQL, Redis |
| `frontend/` | SPA | Angular (standalone, lazy routes), Angular Material, Chart.js |
| `ai-service/` | AI microservice | Python 3.11, FastAPI, scikit-learn, pandas |
| `notification-service/` | Emails + PDF invoices | PHP 8.3, Laravel 11, dompdf |
| `docs/` | ERD, UML, API contract, test report | Mermaid / Markdown |
| `scripts/` | Dev helpers (screenshots, OpenAPI export) | Node / shell |

## Commands

Whole stack: `cp .env.example .env && docker compose up --build` → http://localhost:4200

Backend (`cd backend`):
- Build + all tests + coverage: `mvn verify` (JaCoCo report in `target/site/jacoco/index.html`)
- Unit tests only: `mvn test`
- Run against docker Postgres/Redis: `mvn spring-boot:run`
- Run without Docker (embedded PostgreSQL + demo seed): `mvn spring-boot:test-run`

Frontend (`cd frontend`): `npm ci`, `npm start` (proxy to :8080), `npm test`, `npm run build`

AI (`cd ai-service`): `pip install -r requirements-dev.txt`, `python -m scripts.generate_dataset`,
`python -m scripts.train`, `uvicorn app.main:app --reload --port 8000`, `pytest`

Notifications (`cd notification-service`): `composer install`, `php artisan serve --port=8001`, `php artisan test`

## Conventions

- **Backend**: package by feature under `com.pfe.marketplace.<feature>` with `controller / service /
  repository / dto / mapper / entity` inside. Controllers contain no business logic. DTOs are Java
  records validated with `jakarta.validation`. Mapping via MapStruct. Errors are thrown as
  `ApiException` subclasses (`NotFoundException`, `BusinessException`, …) and rendered by
  `GlobalExceptionHandler` as `ProblemDetail`.
- **Money**: `BigDecimal` with scale 2 (`numeric(12,2)`), never `double`.
- **DB**: every schema change is a new Flyway file `V<n>__<snake_description>.sql`. Never edit an
  applied migration. Foreign keys + indexes declared in the migration.
- **Security**: secrets only through env vars (`.env`, never committed). BCrypt for passwords.
  Role checks via `@PreAuthorize` + URL rules in `SecurityConfig`. Service-to-service calls use the
  `X-Internal-Key` header.
- **Tests**: JUnit 5 + Mockito for services (`*ServiceTest`), Spring + PostgreSQL for integration
  (`*IT`, extend `IntegrationTest`). Target ≥ 70 % line coverage on `service` packages. pytest for AI.
  Jasmine-style specs for Angular guards, interceptors, cart and auth services.
- **Frontend**: standalone components, one lazy route tree per role (`store`, `seller`, `admin`),
  signals for state, `authInterceptor` adds the JWT and refreshes on 401, guards per role.
- **Commits**: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `ci:`), one per phase.
- Comments only where intent is not obvious. Short methods. Meaningful names.

## Demo accounts (seed, profile `demo`)

| Role | Email | Password |
|---|---|---|
| Admin | admin@markethub.test | Admin123! |
| Seller | seller1@markethub.test … seller8 | Seller123! |
| Customer | customer1@markethub.test … customer40 | Customer123! |
