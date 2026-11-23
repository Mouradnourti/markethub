# Database schema (ERD)

Source of truth: Flyway migrations in `backend/src/main/resources/db/migration`.

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : has
    USERS ||--o| SHOPS : owns
    USERS ||--o{ CART_ITEMS : fills
    USERS ||--o{ ORDERS : places
    USERS ||--o{ REVIEWS : writes
    USERS ||--o{ DISPUTES : opens
    CATEGORIES ||--o{ CATEGORIES : parent
    CATEGORIES ||--o{ PRODUCTS : classifies
    SHOPS ||--o{ PRODUCTS : sells
    SHOPS ||--o{ SHOP_ORDERS : fulfils
    PRODUCTS ||--o{ PRODUCT_IMAGES : shows
    PRODUCTS ||--o{ CART_ITEMS : "is in"
    PRODUCTS ||--o{ ORDER_ITEMS : "is sold as"
    PRODUCTS ||--o{ REVIEWS : receives
    ORDERS ||--|{ SHOP_ORDERS : "split into"
    ORDERS ||--o{ PAYMENTS : "paid by"
    SHOP_ORDERS ||--|{ ORDER_ITEMS : contains
    SHOP_ORDERS ||--o{ ORDER_STATUS_HISTORY : tracks
    SHOP_ORDERS ||--o{ DISPUTES : "subject of"

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar first_name
        varchar last_name
        varchar phone
        varchar role "CUSTOMER | SELLER | ADMIN"
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }
    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "SHA-256"
        timestamptz expires_at
        boolean revoked
        timestamptz created_at
    }
    SHOPS {
        bigint id PK
        bigint owner_id FK,UK
        varchar name
        varchar slug UK
        text description
        varchar status "PENDING | APPROVED | REJECTED | SUSPENDED"
        numeric commission_rate "nullable override, percent"
        timestamptz created_at
        timestamptz updated_at
    }
    CATEGORIES {
        bigint id PK
        bigint parent_id FK
        varchar name
        varchar slug UK
        text description
    }
    PRODUCTS {
        bigint id PK
        bigint shop_id FK
        bigint category_id FK
        varchar name
        text description
        numeric price
        int stock "CHECK >= 0"
        boolean active
        numeric avg_rating
        int review_count
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }
    PRODUCT_IMAGES {
        bigint id PK
        bigint product_id FK
        varchar url
        int position
    }
    CART_ITEMS {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        int quantity
        timestamptz added_at
    }
    ORDERS {
        bigint id PK
        varchar reference UK
        bigint customer_id FK
        varchar payment_method "COD | CARD"
        varchar payment_status "PENDING | PAID | FAILED | REFUNDED"
        numeric subtotal
        numeric shipping_fee
        numeric total
        varchar shipping_full_name
        varchar shipping_phone
        varchar shipping_address_line
        varchar shipping_city
        varchar shipping_postal_code
        varchar shipping_country
        numeric return_risk_score "0..1, COD only"
        timestamptz created_at
        timestamptz updated_at
    }
    SHOP_ORDERS {
        bigint id PK
        bigint order_id FK
        bigint shop_id FK
        varchar status
        numeric subtotal
        numeric commission_rate
        numeric commission_amount
        varchar tracking_number
        timestamptz created_at
        timestamptz updated_at
    }
    ORDER_ITEMS {
        bigint id PK
        bigint shop_order_id FK
        bigint product_id FK
        varchar product_name "snapshot"
        numeric unit_price "snapshot"
        int quantity
        numeric line_total
    }
    ORDER_STATUS_HISTORY {
        bigint id PK
        bigint shop_order_id FK
        varchar status
        varchar note
        bigint changed_by FK
        timestamptz changed_at
    }
    PAYMENTS {
        bigint id PK
        bigint order_id FK
        varchar provider "STRIPE | STRIPE_MOCK | COD"
        varchar provider_reference
        numeric amount
        varchar currency
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
    REVIEWS {
        bigint id PK
        bigint product_id FK
        bigint user_id FK
        smallint rating "1..5"
        text comment
        timestamptz created_at
    }
    DISPUTES {
        bigint id PK
        bigint shop_order_id FK
        bigint customer_id FK
        varchar type "RETURN | DISPUTE"
        varchar reason
        varchar status "OPEN | APPROVED | REJECTED"
        varchar admin_note
        timestamptz created_at
        timestamptz resolved_at
    }
    PLATFORM_SETTINGS {
        varchar key PK
        varchar value
    }
```

## Indexes (besides PK / UK)

| Table | Index | Purpose |
|---|---|---|
| products | `(category_id)`, `(shop_id)`, `(active, created_at)`, `(price)`, `lower(name)` | Catalog filters and sorting |
| cart_items | unique `(user_id, product_id)` | One line per product |
| orders | `(customer_id, created_at desc)` | Order history |
| shop_orders | `(shop_id, status)`, `(order_id)` | Seller inbox |
| order_items | `(shop_order_id)`, `(product_id)` | Joins, recommendations export |
| reviews | unique `(product_id, user_id)` | One review per customer per product |
| disputes | `(status)` | Admin inbox |
| refresh_tokens | `(user_id)` | Revoke all on logout/disable |
