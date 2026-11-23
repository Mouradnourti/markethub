# Test report

Run on 2026-11-23 on Windows 11 (JDK 21.0.13, Node 24, Python 3.15 locally / 3.11 in Docker and CI, PHP 8.3).
CI runs the same suites on every push (`.github/workflows/ci.yml`).

## Summary

| Suite | Tool | Tests | Result | Notes |
|---|---|---|---|---|
| Backend unit | JUnit 5 + Mockito | 77 | ✅ pass | services, order rules, JWT, refresh tokens |
| Backend integration | Spring Boot Test + PostgreSQL 16 | 22 | ✅ pass | Testcontainers in CI, embedded PostgreSQL locally |
| Frontend | Vitest (Angular unit-test builder) | 21 | ✅ pass | auth interceptor, guards, auth & cart services, pipes, risk badge |
| AI service | pytest | 17 | ✅ pass | models, synthetic data, API contract |
| Notification service | PHPUnit (Laravel) | 8 | ✅ pass | auth, validation, e-mail with PDF attachment, invoice |
| End-to-end | puppeteer-core + Chrome/Edge | 1 journey (9 steps) | ✅ pass | full customer → seller → admin journey in the real UI |
| **Total** | | **145 + E2E** | ✅ | |

## Backend coverage (JaCoCo, unit + integration)

| Scope | Line coverage | Target |
|---|---|---|
| `*.service` packages | **86.5 %** | ≥ 70 % (enforced by `mvn verify`) |
| Whole backend | 89.5 % | — |
| Branches (whole backend) | 77.3 % | — |

Report: `backend/target/site/jacoco/index.html` after `mvn verify`.

### What the integration tests cover

| Test class | Scenarios |
|---|---|
| `AuthControllerIT` | register customer/seller, login (case-insensitive email), validation errors, duplicate email, wrong password, refresh rotation and reuse detection, role checks, tampered JWT |
| `MarketplaceFlowIT` | COD order across two shops through its whole lifecycle (commission, free shipping, illegal transition, COD refused at the door, review rules, return approved by admin, stats); card payment success and decline; stock and cart rules (oversell refused, nothing reserved on failure, cancellation releases stock); pending shop cannot sell, suspended shop disappears from the catalog; filters, sorting and pagination; admin users, commissions and settings |
| `PaymentWebhookAndExpiryIT` | Stripe webhook refused without secret, unpaid card order expired and restocked, invoice returns 503 when the invoice service is down |
| `AiIntegrationIT` | contract with a fake AI server: risk score stored at COD checkout and shown to sellers, backfill job, recommendations (owned products excluded, best-seller padding), seller forecast, internal dataset secured by the internal key |
| `DemoDataSeederIT` | the demo seed is consistent, idempotent, loadable through JPA, and sequences are realigned |

## AI model evaluation

Metrics are produced at every training run and exposed by `GET /models` on the AI service.

| Model | Data | Metric | Model | Baseline |
|---|---|---|---|---|
| Return-risk classifier | live demo data, 443 labelled COD orders, 21 % refused/returned | 5-fold CV ROC AUC | **0.72** (logistic regression; gradient boosting 0.71) | 0.50 (random) |
| Return-risk classifier | synthetic dataset, 1 374 COD orders | 5-fold CV ROC AUC | **0.71** | 0.50 |
| Recommender | synthetic dataset, 400 users | leave-last-out hit rate @10 (new items only) | **0.23** | 0.21 (most popular) |
| Recommender | live demo data, 40 customers | leave-last-out hit rate @10 | 0.19 | 0.29 |
| Sales forecaster | TechNova shop, 365 days | MAE on the last 14 days | **558** | 652 (same weekday last week) |

Reading the numbers honestly:

- The return-risk model clearly separates risky COD orders (AUC ≈ 0.72) using only data available at checkout:
  the customer's past refusals, first order, basket size, order hour, account age and city.
- On the small demo marketplace (40 customers, only 21 evaluable users) the recommender does not beat "most popular"
  — popularity is very strong in such a small catalog and the sample is too small for a reliable comparison. On the
  larger synthetic dataset it is slightly better than the baseline. Personalisation needs more users and more
  co-purchases; the hybrid design (collaborative filtering + content similarity + popularity prior) degrades
  gracefully towards popular products when signal is missing.
- The forecaster beats the seasonal-naive baseline on real (lumpy) daily sales. It models log sales to capture
  multiplicative weekly and yearly seasonality and applies Duan's smearing correction to forecast expected revenue.

## End-to-end journey

`cd e2e && npm install && npm test` (stack running on http://localhost:4200):

```
MarketHub E2E journey on http://localhost:4200
  ✔ 1. Home page shows the catalog highlights
  ✔ 2. Catalog filters by category and sorts by popularity
  ✔ 3. Customer signs in and gets AI recommendations
  ✔ 4. Product page: "Nova X5 Smartphone 128 GB" added to the cart
  ✔ 5. Cash-on-delivery order MH-261123-… placed
  ✔ 6. Seller dashboard shows KPIs, charts and the AI sales forecast
  ✔ 7. Seller confirms, ships (with tracking number) and delivers the parcel
  ✔ 8. Customer sees the delivered parcel, its tracking number and can request a return
  ✔ 9. Admin dashboard and seller validation

E2E journey passed (9 steps).
```

The journey also produces the screenshots in `docs/screenshots/`.

## Manual cross-service checks

- Backend → Laravel: a COD order sent the confirmation e-mail (subject, recipient, PDF attachment verified in the
  mail log) and `GET /api/orders/{id}/invoice` returned a valid 25 KB PDF.
- AI service → backend: the AI service pulled `/api/internal/ml/dataset`, retrained both models on live data, and the
  backend then served recommendations, the seller forecast and a risk score on a new COD order.

## Known limitations

- Docker was not available on the development machine, so `docker compose up` itself was not run there; every image
  is built in CI (`docker` job) and each service was run and tested natively.
- Real Stripe test-mode payments need your own test keys (`STRIPE_*` in `.env`); without them the built-in simulator
  is used (also covered by the tests).
