# UML diagrams

All diagrams are Mermaid (rendered by GitHub, GitLab and most Markdown viewers).

## 1. Use cases

Mermaid has no native use-case notation; actors are on the sides and use cases are the rounded nodes.

```mermaid
flowchart LR
    visitor([👤 Visitor])
    customer([👤 Customer])
    seller([👤 Seller])
    admin([👤 Admin])
    ai[[🤖 AI service]]
    mail[[✉️ Notification service]]

    subgraph MarketHub
        direction TB
        UC1(Browse and search the catalog)
        UC2(Register / sign in)
        UC3(Manage cart)
        UC4(Checkout: COD or card)
        UC5(Track orders, cancel, request a return)
        UC6(Review a received product)
        UC7(Get personalised recommendations)
        UC8(Manage shop profile)
        UC9(Manage products, images and stock)
        UC10(Process orders: confirm, ship, deliver, returned)
        UC11(View sales statistics and AI forecast)
        UC12(Validate / suspend shops)
        UC13(Manage users and categories)
        UC14(Set commissions and shipping rules)
        UC15(Resolve returns and disputes)
        UC16(View platform statistics)
    end

    visitor --- UC1
    visitor --- UC2
    customer --- UC3
    customer --- UC4
    customer --- UC5
    customer --- UC6
    customer --- UC7
    seller --- UC8
    seller --- UC9
    seller --- UC10
    seller --- UC11
    admin --- UC12
    admin --- UC13
    admin --- UC14
    admin --- UC15
    admin --- UC16
    UC7 -.-> ai
    UC11 -.-> ai
    UC4 -.->|risk score| ai
    UC4 -.->|confirmation + invoice| mail
    UC10 -.->|shipped / delivered e-mails| mail
```

## 2. Sequence — cash-on-delivery checkout

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant UI as Angular SPA
    participant API as Backend
    participant DB as PostgreSQL
    participant AI as AI service
    participant NS as Notification service

    C->>UI: Place order (COD)
    UI->>API: POST /api/orders/checkout
    API->>DB: load cart with products and shops
    loop each cart line
        API->>DB: UPDATE products SET stock = stock - qty WHERE stock >= qty
        DB-->>API: 1 row (or 0 → 422 "not enough stock", transaction rolled back)
    end
    API->>API: split into one shop order per seller, snapshot prices and commission
    API->>AI: POST /return-risk (features of the order and customer history)
    AI-->>API: { score: 0.23, level: "medium" }
    API->>DB: INSERT order, shop orders, items, history; DELETE cart
    API-->>UI: 201 { order }
    Note over API,NS: after commit, on a background thread
    API->>NS: POST /api/notifications/order-event (ORDER_PLACED)
    NS->>NS: render PDF invoice (dompdf)
    NS-->>C: confirmation e-mail with invoice
```

## 3. Sequence — card payment (Stripe test mode)

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant UI as Angular SPA
    participant API as Backend
    participant S as Stripe

    UI->>API: POST /api/orders/checkout (CARD)
    API->>S: create PaymentIntent (amount in cents)
    S-->>API: id + client_secret
    API-->>UI: 201 { order (AWAITING_PAYMENT), payment: { clientSecret, publishableKey } }
    C->>UI: enters card in Stripe Payment Element
    UI->>S: confirmPayment(clientSecret)
    S-->>UI: succeeded
    UI->>API: POST /api/payments/{orderId}/confirm
    API->>S: retrieve PaymentIntent
    S-->>API: status = succeeded
    API->>API: payment PAID, parcels AWAITING_PAYMENT → PENDING, ORDER_PLACED event
    API-->>UI: { paymentStatus: PAID }
    S--)API: webhook payment_intent.succeeded (signed, idempotent)
    Note over API: Unpaid card orders are cancelled after 30 min and their stock released.<br/>Without Stripe keys, a simulator replaces Stripe.
```

## 4. Sequence — seller processes a parcel

```mermaid
sequenceDiagram
    autonumber
    actor S as Seller
    participant API as Backend
    participant DB as PostgreSQL
    participant NS as Notification service
    actor C as Customer

    S->>API: PATCH /api/seller/orders/{id}/status { SHIPPED, trackingNumber }
    API->>API: check shop ownership and allowed transition (CONFIRMED → SHIPPED)
    API->>DB: update status, append history entry
    API-->>S: 200 updated parcel
    API--)NS: ORDER_SHIPPED (after commit, async)
    NS--)C: "Your parcel is on its way" + tracking number
    alt customer refuses the COD parcel
        S->>API: status RETURNED
        API->>DB: release stock, payment FAILED if nothing was delivered
    else delivered
        S->>API: status DELIVERED
        API->>DB: COD payment → PAID
    end
```

## 5. Sequence — AI training and recommendations

```mermaid
sequenceDiagram
    autonumber
    participant AI as AI service
    participant API as Backend
    actor C as Customer

    Note over AI: at startup: load saved models, else train on the synthetic dataset
    loop every hour (retry every 30 s until the backend is up)
        AI->>API: GET /api/internal/ml/dataset (X-Internal-Key)
        API-->>AI: products, interactions, labelled COD orders
        AI->>AI: fit recommender + return-risk model, save with joblib
    end
    C->>API: GET /api/recommendations
    API->>AI: POST /recommendations { userId, excludeProductIds }
    alt AI available
        AI-->>API: productIds (personalized / hybrid)
    else AI down or not trained
        API->>API: best sellers
    end
    API-->>C: visible, in-stock products
```

## 6. Sequence — token refresh

```mermaid
sequenceDiagram
    autonumber
    participant UI as Angular (authInterceptor)
    participant API as Backend

    UI->>API: GET /api/orders (expired access token)
    API-->>UI: 401
    UI->>API: POST /api/auth/refresh { refreshToken }
    Note over API: token hash looked up, revoked, new pair issued.<br/>Re-using a revoked token revokes every session of the user.
    API-->>UI: { accessToken, refreshToken }
    UI->>API: GET /api/orders (new token, replayed once)
    API-->>UI: 200
```
