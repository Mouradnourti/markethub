"""Generates a realistic synthetic marketplace dataset for offline training and tests.

Usage: python -m scripts.generate_dataset [--out data] [--seed 42]

Produces CSV files with the same columns as the backend export (/api/internal/ml/dataset):
  products.csv      id, name, category, description, price, shop_id
  interactions.csv  user_id, product_id, quantity, created_at
  cod_orders.csv    return-risk features + 'returned' label
  daily_sales.csv   shop_id, date, revenue (trend + weekly + yearly seasonality + noise)

The COD refusal probability follows the patterns observed in cash-on-delivery e-commerce: risky customers, first
orders, large baskets, late-night orders and some cities are refused more often.
"""

from __future__ import annotations

import argparse
from datetime import date, datetime, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

CATEGORIES = {
    "Electronics": ["Smartphone", "Tablet", "Laptop", "Headphones", "Speaker", "Smartwatch", "Charger", "Monitor"],
    "Home": ["Frying Pan", "Kettle", "Lamp", "Cushion", "Vase", "Blender", "Candle", "Mirror"],
    "Fashion": ["T-Shirt", "Hoodie", "Jeans", "Sneakers", "Backpack", "Jacket", "Wallet", "Boots"],
    "Beauty": ["Serum", "Moisturiser", "Cleanser", "Hair Oil", "Toothbrush", "Diffuser", "Brush Set", "Deodorant"],
    "Sports": ["Yoga Mat", "Dumbbells", "Water Bottle", "Tent", "Helmet", "Resistance Bands", "Foam Roller", "Rope"],
    "Books": ["Novel", "Cookbook", "Notebook", "Board Game", "Puzzle", "Comic", "Planner", "Paint Set"],
}
ADJECTIVES = ["Classic", "Pro", "Eco", "Compact", "Premium", "Smart", "Ultra", "Essential"]
CITIES = ["Paris", "Lyon", "Marseille", "Toulouse", "Lille", "Bordeaux", "Nantes", "Nice", "Strasbourg", "Rennes"]
RISKY_CITIES = {"Marseille", "Nice"}


def products_frame(rng: np.random.Generator, per_category: int = 20) -> pd.DataFrame:
    rows, pid = [], 1
    for shop_id, (category, nouns) in enumerate(CATEGORIES.items(), start=1):
        for i in range(per_category):
            noun = nouns[i % len(nouns)]
            name = f"{ADJECTIVES[(i // len(nouns) + i) % len(ADJECTIVES)]} {noun} {i + 1}"
            rows.append({"id": pid, "name": name, "category": category, "shop_id": shop_id,
                         "description": f"{name} from our {category.lower()} range. Quality {noun.lower()}.",
                         "price": round(float(rng.lognormal(3.4, 0.7)), 2)})
            pid += 1
    return pd.DataFrame(rows)


def interactions_and_orders(rng: np.random.Generator, products: pd.DataFrame, users: int = 400,
                            orders: int = 4000, days: int = 365) -> tuple[pd.DataFrame, pd.DataFrame]:
    today = date(2026, 11, 1)
    categories = list(CATEGORIES)
    profiles = {
        uid: {
            "favourites": rng.choice(categories, size=2, replace=False),
            "risky": rng.random() < 0.18,
            "city": CITIES[int(rng.integers(len(CITIES)))],
            "created": today - timedelta(days=int(rng.integers(10, days + 60))),
        }
        for uid in range(1, users + 1)
    }
    by_category = {c: products[products["category"] == c] for c in categories}
    history: dict[int, dict[str, int]] = {uid: {"orders": 0, "cod_final": 0, "returns": 0} for uid in profiles}
    interactions, cod_rows = [], []

    # Customer activity follows a long tail: weight ∝ 1 / rank^0.8.
    weights = 1 / np.arange(1, users + 1) ** 0.8
    weights /= weights.sum()
    events = []
    for _ in range(orders):
        uid = int(rng.choice(np.arange(1, users + 1), p=weights))
        night = rng.random() < (0.25 if profiles[uid]["risky"] else 0.07)
        minute = int(rng.integers(0, 360)) if night else int(rng.integers(540, 1380))
        placed = datetime.combine(today - timedelta(days=int(rng.integers(0, days))), datetime.min.time())
        events.append((placed + timedelta(minutes=minute), uid))

    # Chronological order, so 'prior_*' features only use the customer's past.
    for placed, uid in sorted(events):
        profile = profiles[uid]
        if placed.date() < profile["created"]:
            continue
        size = int(rng.choice([1, 2, 3], p=[0.58, 0.3, 0.12]))
        basket = []
        for _ in range(size):
            category = profile["favourites"][int(rng.integers(2))] if rng.random() < 0.75 else rng.choice(categories)
            pool = by_category[category]
            row = pool.iloc[min(len(pool) - 1, int(len(pool) * rng.random() ** 2))]
            basket.append((int(row["id"]), int(row["shop_id"]), float(row["price"]), 2 if rng.random() < 0.15 else 1))
        for product_id, _, _, qty in basket:
            interactions.append({"user_id": uid, "product_id": product_id, "quantity": qty, "created_at": placed})

        stats = history[uid]
        cod = rng.random() < (0.85 if profile["risky"] else 0.58)
        if cod:
            total = sum(p * q for _, _, p, q in basket)
            p = 0.04 + 0.32 * profile["risky"] + 0.09 * (total > 150) + 0.08 * (stats["orders"] == 0)
            p += 0.06 * (placed.hour >= 23 or placed.hour < 6) + 0.04 * (profile["city"] in RISKY_CITIES)
            returned = rng.random() < min(p, 0.8)
            cod_rows.append({
                "total": round(total, 2), "item_count": sum(q for *_, q in basket),
                "shop_count": len({s for _, s, _, _ in basket}), "prior_orders": stats["orders"],
                "prior_returns": stats["returns"],
                "prior_return_rate": round(stats["returns"] / stats["cod_final"], 4) if stats["cod_final"] else 0.0,
                "account_age_days": (placed.date() - profile["created"]).days, "hour": placed.hour,
                "is_weekend": int(placed.weekday() >= 5), "first_order": int(stats["orders"] == 0),
                "city": profile["city"], "returned": int(returned),
            })
            stats["cod_final"] += 1
            stats["returns"] += int(returned)
        stats["orders"] += 1

    return pd.DataFrame(interactions), pd.DataFrame(cod_rows)


def daily_sales_frame(rng: np.random.Generator, shops: int = 6, days: int = 365) -> pd.DataFrame:
    start = date(2026, 11, 1) - timedelta(days=days - 1)
    rows = []
    for shop_id in range(1, shops + 1):
        base = float(rng.uniform(80, 400))
        for i in range(days):
            day = start + timedelta(days=i)
            weekly = 1.35 if day.weekday() >= 5 else 0.85 if day.weekday() == 0 else 1.0
            yearly = 1 + 0.35 * np.cos((day.timetuple().tm_yday - 345) / 365.25 * 2 * np.pi)
            mean = base * (1 + 0.9 * i / days) * weekly * yearly
            rows.append({"shop_id": shop_id, "date": day.isoformat(),
                         "revenue": round(max(0.0, float(rng.normal(mean, mean * 0.25))), 2)})
    return pd.DataFrame(rows)


def generate(out: Path, seed: int = 42) -> dict[str, int]:
    rng = np.random.default_rng(seed)
    out.mkdir(parents=True, exist_ok=True)
    products = products_frame(rng)
    interactions, cod_orders = interactions_and_orders(rng, products)
    sales = daily_sales_frame(rng)
    products.to_csv(out / "products.csv", index=False)
    interactions.to_csv(out / "interactions.csv", index=False)
    cod_orders.to_csv(out / "cod_orders.csv", index=False)
    sales.to_csv(out / "daily_sales.csv", index=False)
    return {"products": len(products), "interactions": len(interactions), "cod_orders": len(cod_orders),
            "daily_sales": len(sales)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent.parent / "data")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    print(generate(args.out, args.seed))


if __name__ == "__main__":
    main()
