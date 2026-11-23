## AI service (FastAPI, port 8000)

Interactive docs: http://localhost:8000/docs. Every endpoint except `/health` requires `X-Internal-Key`.
JSON uses camelCase.

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | — | `{ status, recommender, returnRisk }` (data source of each model) |
| GET | `/models` | — | training metrics (hit rate, CV ROC AUC, sample sizes) |
| POST | `/recommendations` | `{ userId, limit, excludeProductIds[] }` | `{ productIds[], strategy }` — 503 until trained on catalog data |
| POST | `/forecast` | `{ history: [{ date, revenue }], horizonDays }` | `{ model, mae, baselineMae, forecast: [{ date, revenue, lower, upper }] }` — 422 with < 28 days |
| POST | `/return-risk` | `{ total, itemCount, shopCount, priorOrders, priorReturns, priorReturnRate, accountAgeDays, hour, isWeekend, firstOrder, city }` | `{ score, level, reasons[], model }` |
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

`event` ∈ `ORDER_PLACED` (invoice attached), `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`, `ORDER_RETURNED`.

`OrderDocument`: `{ reference, createdAt, paymentMethod, paymentStatus, currency, subtotal, shippingFee, total,
shippingAddress: { fullName, phone, addressLine, city, postalCode, country }, shopOrders: [{ shopName, status,
subtotal, trackingNumber, items: [{ name, quantity, unitPrice, lineTotal }] }] }`.
