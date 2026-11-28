## AI service (FastAPI, port 8000)

Interactive docs: http://localhost:8000/docs. Every endpoint except `/health` requires `X-Internal-Key`.
JSON uses camelCase.

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | — | `{ status, recommender, returnRisk, catalog, llmWriter }` (data source of each model; `llmWriter` is the Claude model or `off`) |
| GET | `/models` | — | training metrics (hit rate, CV ROC AUC, sample sizes) |
| POST | `/recommendations` | `{ userId, limit, excludeProductIds[] }` | `{ productIds[], strategy }` — 503 until trained on catalog data |
| POST | `/forecast` | `{ history: [{ date, revenue }], horizonDays }` | `{ model, mae, baselineMae, forecast: [{ date, revenue, lower, upper }] }` — 422 with < 28 days |
| POST | `/return-risk` | `{ total, itemCount, shopCount, priorOrders, priorReturns, priorReturnRate, accountAgeDays, hour, isWeekend, firstOrder, city }` | `{ score, level, reasons[], model }` |
| POST | `/copywriter` | `{ name, category?, keywords[], tone: friendly\|premium\|technical, variant, engine?: auto\|rules\|llm }` | `{ tagline, description, highlights[], specs[{label,value}], suggestedSpecLabels[], categoryProfile, engine }` (`engine` = `rules` or the Claude model used) |
| POST | `/search` | `{ query, limit }` | `{ hits: [{ productId, score }], model: "tfidf+lsa" }` — 503 until built from the live catalog |
| POST | `/price-hint` | `{ name, category?, description? }` | `{ low, typical, high, suggested, confidence, comparables: [{ productId, name, price, similarity }] }` — 404 without comparable products |
| POST | `/train` | — | retrains from the backend dataset, returns metrics |

The AI service itself calls `GET /api/internal/ml/dataset` on the backend (snake_case columns:
`products`, `interactions`, `codOrders`).

## Notification & invoice service (Laravel, port 8001)

Every endpoint except `/api/health` requires `X-Internal-Key`.

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/health` | — | `{ status: "UP" }` |
| POST | `/api/notifications/order-event` | `{ event, recipient: { name, email }, order: OrderDocument, parcelIndex? }` | 202 `{ status: "sent" }` |
| POST | `/api/invoices` | `{ customer: { name, email }, order: OrderDocument }` | 200 `application/pdf` |
| POST | `/api/notifications/product-alert` | `{ type: BACK_IN_STOCK\|PRICE_DROP, recipient, product: { name, shopName, currency, price, previousPrice?, url } }` | 202 `{ status: "sent" }` |
| POST | `/api/notifications/cart-reminder` | `{ recipient, cart: { currency, subtotal, url, items: [{ name, quantity, unitPrice, lineTotal }] } }` | 202 `{ status: "sent" }` |

`event` ∈ `ORDER_PLACED` (invoice attached), `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`, `ORDER_RETURNED`.

`OrderDocument`: `{ reference, createdAt, paymentMethod, paymentStatus, currency, subtotal, discountTotal?, shippingFee, total,
shippingAddress: { fullName, phone, addressLine, city, postalCode, country }, shopOrders: [{ shopName, status,
subtotal, discountAmount?, couponCode?, trackingNumber, items: [{ name, quantity, unitPrice, lineTotal }] }] }`.
A parcel's `subtotal` is what the customer pays for its items, after `discountAmount`.
