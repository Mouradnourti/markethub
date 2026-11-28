# Test report

Run on 2026-11-28 on Windows 11 (JDK 21.0.13, Node 24, Python 3.15 locally / 3.11 in Docker and CI, PHP 8.3).
CI runs the same suites on every push (`.github/workflows/ci.yml`) and builds every Docker image; the latest run
on `main` is green.

## Summary

| Suite | Tool | Tests | Result | Notes |
|---|---|---|---|---|
| Backend unit | JUnit 5 + Mockito | 84 | ✅ pass | services, order rules, JWT, refresh tokens, AI writer client, demo content, auth rate limit |
| Backend integration | Spring Boot Test + PostgreSQL 16 | 44 | ✅ pass | Testcontainers in CI, embedded PostgreSQL locally |
| Frontend | Vitest (Angular unit-test builder) | 47 | ✅ pass | auth (incl. two-step sign-in), interceptor, guards, cart and discount codes, add-to-cart, wishlist, translations, recently viewed, merchandising helpers, pipes |
| AI service | pytest | 35 | ✅ pass | models, synthetic data, description writer, Claude writer (fake client), catalog index (smart search, price hints), API contract |
| Notification service | PHPUnit (Laravel) | 11 | ✅ pass | auth, validation, order e-mail with PDF, invoice, product alerts, cart reminder |
| End-to-end | puppeteer-core + Chrome/Edge | 1 journey (17 steps) | ✅ pass | full customer → seller → admin journey in the real UI, plus Arabic |
| **Total** | | **221 + E2E** | ✅ | |

## Backend coverage (JaCoCo, unit + integration)

| Scope | Line coverage | Target |
|---|---|---|
| `*.service` packages | **89.9 %** | ≥ 70 % (enforced by `mvn verify`) |
| Whole backend | 91.7 % | — |
| Branches (whole backend) | 79.7 % | — |

Report: `backend/target/site/jacoco/index.html` after `mvn verify`.

### What the integration tests cover

| Test class | Scenarios |
|---|---|
| `AuthControllerIT` | register customer/seller, login (case-insensitive email), validation errors, duplicate email, wrong password, refresh rotation and reuse detection, role checks, tampered JWT |
| `MarketplaceFlowIT` | COD order across two shops through its whole lifecycle (commission, free shipping, illegal transition, COD refused at the door, review rules, return approved by admin, stats); card payment success and decline; stock and cart rules (oversell refused, nothing reserved on failure, cancellation releases stock); pending shop cannot sell, suspended shop disappears from the catalog; filters, sorting and pagination; admin users, commissions and settings |
| `PaymentWebhookAndExpiryIT` | Stripe webhook refused without secret, unpaid card order expired and restocked, invoice returns 503 when the invoice service is down |
| `AiIntegrationIT` | contract with a fake AI server: risk score stored at COD checkout and shown to sellers, backfill job, recommendations (owned products excluded, best-seller padding), seller forecast, AI description writer (category path, keywords, tone, validation, seller-only), internal dataset secured by the internal key |
| `MerchandisingIT` | sale (compare-at) price, tagline, benefits and specs saved and served; validation (compare-at above price, list sizes, blank specs); deals filter; ending a sale; "frequently bought together" from order history with similar-product padding |
| `DemoDataSeederIT` | the demo seed is consistent, idempotent, loadable through JPA, sequences are realigned, every demo product has complete copy (about half on sale), and engagement data is realistic (one featured code per shop, conversion between 1 and 8 %, replies and votes) |
| `CouponIT` | a code discounts only its shop; commission and payout follow the net price; refusals explain themselves (unknown, minimum spend, expired, other shop); unique codes, percentage cap; once per customer and usage limit under the atomic redemption; cancellation gives the use back; featured offers and seller ownership |
| `EngagementIT` | wishlist save/list/remove; back-in-stock and price-drop alerts fired once; "new order" for sellers and "parcel shipped" for customers in the bell; read / read-all and ownership; SSE stream only with a fresh single-use ticket; abandoned-cart reminder sent once until the cart changes |
| `ReviewEngagementIT` | helpful votes (idempotent, not on one's own review), sort by helpful/lowest, star filter, star breakdown, seller replies with one notification, ownership; review photos (author only, max 3) |
| `SearchAndAnalyticsIT` | typo tolerance ("hedphones"), relevance sort, suggestions, facet counts that ignore the selected category, smart-search fallback to keyword search; seller analytics joining page views and orders (conversion, wishlist saves, daily series) |
| `AccountSecurityIT` | five wrong passwords lock the account (429 + Retry-After), unlock after the delay; two-step setup (secret encrypted at rest), enable with a valid code, challenge token cannot call the API, wrong/forged codes refused, disable |
| `MetricsIT` | `/actuator/prometheus` serves HTTP latency histograms, JVM metrics and the marketplace counters |

## AI model evaluation

Metrics are produced at every training run and exposed by `GET /models` on the AI service.

| Model | Data | Metric | Model | Baseline |
|---|---|---|---|---|
| Return-risk classifier | live demo data, 435 labelled COD orders, 20 % refused/returned | 5-fold CV ROC AUC | **0.70** (logistic regression; gradient boosting 0.69) | 0.50 (random) |
| Return-risk classifier | synthetic dataset, 1 374 COD orders | 5-fold CV ROC AUC | **0.71** | 0.50 |
| Recommender | synthetic dataset, 400 users | leave-last-out hit rate @10 (new items only) | **0.23** | 0.21 (most popular) |
| Recommender | live demo data, 40 customers (19 evaluable) | leave-last-out hit rate @10 (new items only) | **0.16** | 0.05 (most popular) |
| Sales forecaster | TechNova shop, 365 days | MAE on the last 14 days | **220** | 450 (same weekday last week) |

Reading the numbers honestly:

- The return-risk model clearly separates risky COD orders (AUC ≈ 0.70) using only data available at checkout:
  the customer's past refusals, first order, basket size, order hour, account age and city.
- Since the demo order history contains realistic baskets (a phone with its case and charger, a pan with a knife…),
  the recommender beats "most popular" on live data as well as on the synthetic dataset. With only 19 evaluable
  customers the live figure is noisy (it moved between 0.16 and 0.22 across retrains); the hybrid design
  (collaborative filtering + content similarity + popularity prior) degrades gracefully towards popular products
  when signal is missing.
- The forecaster beats the seasonal-naive baseline on real (lumpy) daily sales, with half its error. It models log sales to capture
  multiplicative weekly and yearly seasonality and applies Duan's smearing correction to forecast expected revenue.

## End-to-end journey

`cd e2e && npm install && npm test` (stack running on http://localhost:4200):

```
MarketHub E2E journey on http://localhost:4200
  ✔ 1. Home page shows the catalog highlights
  ✔ 2. Catalog filters by category, sorts by popularity and shows filter counts
  ✔ 3. Instant search suggestions tolerate typos ("hedphones" finds headphones)
  ✔ 4. Customer signs in and gets AI recommendations
  ✔ 5. Product page shows the shop discount code; the product is saved to the wishlist
  ✔ 6. Product page: "Nova X5 Smartphone 128 GB" added; the cart drawer shows free-shipping progress
  ✔ 7. Wishlist lists saved products and promises stock and price alerts
  ✔ 8. Discount code TECHNOVA5OFF applied in the cart
  ✔ 9. Cash-on-delivery order MH-261128-… placed
  ✔ 10. Seller dashboard shows KPIs, charts and the AI sales forecast
  ✔ 11. Seller notification bell announces the new order MH-261128-…
  ✔ 12. Seller analytics (views, conversion, returns) and discount codes
  ✔ 13. Seller drafts the product page with the AI writer and sees the listing quality score
  ✔ 14. Seller confirms, ships (with tracking number) and delivers the parcel
  ✔ 15. Customer sees the delivered parcel, its tracking number and can request a return
  ✔ 16. Admin dashboard and seller validation
  ✔ 17. Arabic interface switches the whole layout to right-to-left

E2E journey passed (17 steps).
```

The journey also produces the screenshots in `docs/screenshots/`.

## Manual cross-service checks

- Backend → Laravel: a COD order sent the confirmation e-mail (subject, recipient, PDF attachment verified in the
  mail log) and `GET /api/orders/{id}/invoice` returned a valid 25 KB PDF.
- AI service → backend: the AI service pulled `/api/internal/ml/dataset`, retrained both models on live data, and the
  backend then served recommendations, the seller forecast and a risk score on a new COD order.

## Known limitations

- Docker was not available on the development machine, so `docker compose up` itself was not run there; every image
  is built in CI (`docker` job, green) and each service was run and tested natively.
- Without `ANTHROPIC_API_KEY` the description writer is rule-based (templates + attribute extraction): varied but
  generic wording. The Claude path is covered by tests with a fake client; it was not called with a real key during
  development, so its real output quality is not measured here.
- Docker Compose monitoring (Prometheus, Grafana) and the Render Blueprint were written but not run on this machine
  (no Docker, no Render account); the metrics endpoint itself is tested (`MetricsIT`).
- Live notifications, stream tickets and the sign-in rate limit are kept in memory: the backend runs as one instance.
- Seller and admin back-office pages translate their navigation; their detailed forms stay in English.
- Real Stripe test-mode payments need your own test keys (`STRIPE_*` in `.env`); without them the built-in simulator
  is used (also covered by the tests).
