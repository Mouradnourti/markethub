# API contract

Generated from the live OpenAPI document (`http://localhost:8080/v3/api-docs`) by `scripts/export-api-contract.mjs`.
The full machine-readable spec is [`openapi.json`](openapi.json); interactive docs: http://localhost:8080/swagger-ui.html.

Conventions: JSON bodies, `Authorization: Bearer <access token>` for authenticated calls, errors as
[RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json` with `status`, `title`, `detail`
and, for validation errors (400), an `errors` map `field → message`. Pagination: `page` (0-based) and `size`;
paginated responses are `{ content, page, size, totalElements, totalPages }`.

## Backend (Spring Boot, port 8080)

### AI

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/internal/ml/dataset` | X-Internal-Key |  | object | Training dataset for the AI service (internal, X-Internal-Key) |
| GET | `/api/products/smart-search` | public |  | SmartSearchDto | Search by meaning (AI index), falling back to keyword search when the AI is offline |
| GET | `/api/recommendations` | CUSTOMER |  | ProductSummaryDto[] | Personalised product recommendations for the signed-in customer |
| GET | `/api/seller/forecast` | SELLER |  | SalesForecastDto | AI sales forecast of the seller's shop |
| POST | `/api/seller/products/describe` | SELLER | CopyDraftRequest | CopyDraft | Draft a tagline, description, key benefits and specs for a product (AI writer) |
| POST | `/api/seller/products/price-hint` | SELLER | PriceHintRequest | PriceHintDto | Suggested price range from the most similar products in the catalog |

### Admin

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| POST | `/api/admin/categories` | ADMIN | CategoryRequest | CategoryDto |  |
| PUT | `/api/admin/categories/{id}` | ADMIN | CategoryRequest | CategoryDto |  |
| DELETE | `/api/admin/categories/{id}` | ADMIN |  | — |  |
| GET | `/api/admin/disputes` | ADMIN |  | PageResponseDisputeDto |  |
| PATCH | `/api/admin/disputes/{id}` | ADMIN | ResolveDisputeRequest | DisputeDto | Approve or reject a return request / dispute |
| GET | `/api/admin/settings` | ADMIN |  | PlatformSettingsDto |  |
| PUT | `/api/admin/settings` | ADMIN | PlatformSettingsDto | PlatformSettingsDto |  |
| GET | `/api/admin/shops` | ADMIN |  | PageResponseShopAdminDto |  |
| PATCH | `/api/admin/shops/{id}/commission` | ADMIN | CommissionRequest | ShopAdminDto | Set a shop-specific commission rate (null = platform default) |
| PATCH | `/api/admin/shops/{id}/status` | ADMIN | ShopStatusRequest | ShopAdminDto | Validate, reject or suspend a seller's shop |
| GET | `/api/admin/stats` | ADMIN |  | AdminStatsDto |  |
| GET | `/api/admin/users` | ADMIN |  | PageResponseUserDto |  |
| PATCH | `/api/admin/users/{id}/enabled` | ADMIN | EnabledRequest | UserDto | Enable or disable a user account (disabling revokes its sessions) |

### Auth

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/auth/2fa` | authenticated |  | TwoFactorStatusDto |  |
| POST | `/api/auth/2fa/disable` | authenticated | TwoFactorCodeRequest | TwoFactorStatusDto |  |
| POST | `/api/auth/2fa/enable` | authenticated | TwoFactorCodeRequest | TwoFactorStatusDto | Turn two-factor sign-in on with a code from the authenticator app |
| POST | `/api/auth/2fa/setup` | authenticated |  | TwoFactorSetupDto | Start two-factor setup: returns the secret and otpauth URI to scan |
| POST | `/api/auth/login` | public | LoginRequest | AuthResponse | Exchange credentials for an access token and a refresh token |
| POST | `/api/auth/login/2fa` | authenticated | TwoFactorLoginRequest | AuthResponse | Second sign-in step for accounts with two-factor sign-in |
| POST | `/api/auth/logout` | authenticated | RefreshRequest | — | Revoke a refresh token |
| GET | `/api/auth/me` | authenticated |  | UserDto | Current user profile |
| POST | `/api/auth/refresh` | public | RefreshRequest | AuthResponse | Rotate a refresh token and get a new token pair |
| POST | `/api/auth/register` | public | RegisterRequest | AuthResponse | Create a customer or seller account (sellers start with a PENDING shop) |

### Cart

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/cart` | CUSTOMER |  | CartDto | The cart; pass a discount code to preview its effect (an invalid code returns 422) |
| DELETE | `/api/cart` | CUSTOMER |  | — |  |
| POST | `/api/cart/items` | CUSTOMER | AddCartItemRequest | CartDto |  |
| PUT | `/api/cart/items/{productId}` | CUSTOMER | UpdateCartItemRequest | CartDto |  |
| DELETE | `/api/cart/items/{productId}` | CUSTOMER |  | CartDto |  |

### Catalog (public)

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/categories` | public |  | CategoryDto[] | All categories (flat list with parent ids) |
| GET | `/api/config` | public |  | PublicConfigDto | Public client configuration (currency, payment provider, shipping rules) |
| GET | `/api/products` | public |  | PageResponseProductSummaryDto | Search the catalog with filters, sorting and pagination |
| GET | `/api/products/{id}` | public |  | ProductDto | Product details (counts a page view for the seller's analytics) |
| GET | `/api/products/{id}/bought-together` | public |  | ProductSummaryDto[] | Products frequently ordered together with this one (padded with similar products) |
| GET | `/api/products/{id}/similar` | public |  | ProductSummaryDto[] | Products similar to this one (same category, best sellers) |
| GET | `/api/products/facets` | public |  | FacetsDto | Filter counts for the same filters as the search |
| GET | `/api/products/suggest` | public |  | SuggestionsDto | Instant search suggestions (products, typos tolerated, and categories) |
| GET | `/api/shops/{slug}` | public |  | ShopDto | Public shop page |

### Notifications

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/notifications` | authenticated |  | PageResponseNotificationDto |  |
| POST | `/api/notifications/{id}/read` | authenticated |  | NotificationDto |  |
| POST | `/api/notifications/read-all` | authenticated |  | — |  |
| GET | `/api/notifications/stream` | authenticated |  | SseEmitter | Server-sent events: 'notification' events for the ticket's user |
| POST | `/api/notifications/stream-ticket` | authenticated |  | StreamTicketDto | One-time ticket (valid 60 s) to open the live event stream |
| GET | `/api/notifications/unread-count` | authenticated |  | UnreadCountDto |  |

### Orders (customer)

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/orders` | CUSTOMER |  | PageResponseOrderDto |  |
| GET | `/api/orders/{id}` | CUSTOMER |  | OrderDto |  |
| POST | `/api/orders/{id}/cancel` | CUSTOMER | CancelOrderRequest | OrderDto | Cancel an order that no seller has confirmed yet |
| GET | `/api/orders/{id}/invoice` | CUSTOMER |  | string | Download the PDF invoice of an order (rendered by the notification service) |
| POST | `/api/orders/checkout` | CUSTOMER | CheckoutRequest | CheckoutResponse | Place an order from the cart (COD or CARD) |
| POST | `/api/orders/shop-orders/{shopOrderId}/return-request` | CUSTOMER | OpenDisputeRequest | DisputeDto | Request a return (delivered parcel) or open a dispute on a shop order |

### Payments

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| POST | `/api/payments/{orderId}/confirm` | authenticated | ConfirmPaymentRequest | PaymentStatusDto | Confirm a card payment after the payment form completed (idempotent) |
| POST | `/api/payments/stripe/webhook` | public |  | — | Stripe webhook (payment_intent.succeeded / payment_intent.payment_failed) |

### Promotions

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/seller/coupons` | SELLER |  | CouponDto[] |  |
| POST | `/api/seller/coupons` | SELLER | CouponRequest | CouponDto | Create a discount code for the seller's shop |
| PUT | `/api/seller/coupons/{id}` | SELLER | CouponRequest | CouponDto |  |
| DELETE | `/api/seller/coupons/{id}` | SELLER |  | — |  |
| GET | `/api/shops/{shopId}/offers` | public |  | OfferDto[] | Featured discount codes of a shop that are valid right now |

### Reviews

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/products/{productId}/reviews` | public |  | PageResponseReviewDto | Reviews of a product, sortable and filterable by star rating |
| POST | `/api/products/{productId}/reviews` | authenticated | ReviewRequest | ReviewDto | Review a product the customer has received |
| POST | `/api/products/{productId}/reviews/{reviewId}/helpful` | authenticated |  | ReviewDto | Mark a review as helpful |
| DELETE | `/api/products/{productId}/reviews/{reviewId}/helpful` | authenticated |  | ReviewDto |  |
| POST | `/api/products/{productId}/reviews/{reviewId}/photos` | authenticated | multipart file | ReviewDto | Add a photo to your own review (JPEG, PNG, WebP, GIF, max 5 MB, 3 photos) |
| GET | `/api/products/{productId}/reviews/eligibility` | authenticated |  | ReviewEligibilityDto | Whether the current user may review this product |
| GET | `/api/products/{productId}/reviews/summary` | public |  | ReviewSummaryDto | Average, star breakdown and number of reviews with photos |

### Seller

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/seller/analytics` | SELLER |  | SellerAnalyticsDto | Views, conversion, revenue and returns per day and per product over the last N days |
| GET | `/api/seller/orders` | SELLER |  | PageResponseSellerOrderDto |  |
| GET | `/api/seller/orders/{id}` | SELLER |  | SellerOrderDto |  |
| PATCH | `/api/seller/orders/{id}/status` | SELLER | UpdateShopOrderStatusRequest | SellerOrderDto | Move an order to CONFIRMED, SHIPPED, DELIVERED, RETURNED or CANCELLED |
| GET | `/api/seller/products` | SELLER |  | PageResponseProductSummaryDto |  |
| POST | `/api/seller/products` | SELLER | ProductRequest | ProductDto |  |
| GET | `/api/seller/products/{id}` | SELLER |  | ProductDto |  |
| PUT | `/api/seller/products/{id}` | SELLER | ProductRequest | ProductDto |  |
| DELETE | `/api/seller/products/{id}` | SELLER |  | — |  |
| POST | `/api/seller/products/{id}/images` | SELLER | multipart file | ProductDto | Upload a product image (JPEG, PNG, WebP, GIF, max 5 MB) |
| PATCH | `/api/seller/products/{id}/stock` | SELLER | StockUpdateRequest | ProductDto |  |
| GET | `/api/seller/reviews` | SELLER |  | PageResponseSellerReviewDto | Reviews of the seller's products, newest first |
| PUT | `/api/seller/reviews/{id}/reply` | SELLER | ReplyRequest | SellerReviewDto | Answer a review publicly (the customer is notified the first time) |
| DELETE | `/api/seller/reviews/{id}/reply` | SELLER |  | SellerReviewDto |  |
| GET | `/api/seller/shop` | SELLER |  | ShopDto |  |
| PUT | `/api/seller/shop` | SELLER | UpdateShopRequest | ShopDto |  |
| GET | `/api/seller/stats` | SELLER |  | SellerStatsDto |  |

### Wishlist

| Method | Path | Access | Request | Response | Description |
|---|---|---|---|---|---|
| GET | `/api/wishlist` | authenticated |  | WishlistItemDto[] |  |
| PUT | `/api/wishlist/{productId}` | authenticated |  | — | Save a product; you are told when it is back in stock or its price drops |
| DELETE | `/api/wishlist/{productId}` | authenticated |  | — |  |
| GET | `/api/wishlist/ids` | authenticated |  | integer[] | Ids of the saved products (to draw the hearts on product cards) |

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
| POST | `/copywriter` | `{ name, category?, keywords[], tone: friendly\|premium\|technical, variant }` | `{ tagline, description, highlights[], specs[{label,value}], suggestedSpecLabels[], categoryProfile }` |
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
