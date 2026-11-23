from datetime import date, timedelta

import numpy as np
import pandas as pd
import pytest

from app.ml.forecaster import NotEnoughHistory, forecast
from app.ml.recommender import Recommender, evaluate_hit_rate
from app.ml.return_risk import ReturnRiskModel, explain


# ---------------------------------------------------------------- recommender

def test_recommends_unseen_products_mostly_from_the_users_categories(products, interactions):
    model = Recommender().fit(products, interactions, source="test")
    counts = interactions["user_id"].value_counts()
    user = int(counts.index[len(counts) // 2])  # a typical customer, not the most active one
    owned = set(interactions.loc[interactions["user_id"] == user, "product_id"])
    category = products.set_index("id")["category"]

    picks, strategy = model.recommend(user, limit=8)

    assert len(picks) == 8 and strategy in {"personalized", "hybrid"}
    assert not owned & set(picks)
    favourites = set(category.loc[list(owned)].value_counts().index[:2])
    assert category.loc[picks].isin(favourites).mean() >= 0.5


def test_unknown_user_gets_popular_products_and_exclusions_are_respected(products, interactions):
    model = Recommender().fit(products, interactions, source="test")
    popular, strategy = model.recommend(999_999, limit=5)
    assert strategy == "popular" and len(popular) == 5

    filtered, _ = model.recommend(999_999, limit=5, exclude={popular[0]})
    assert popular[0] not in filtered


def test_similar_products_share_the_category(products, interactions):
    model = Recommender().fit(products, interactions, source="test")
    product_id = int(products["id"].iloc[0])
    similar = model.similar(product_id, limit=5)
    categories = products.set_index("id").loc[similar, "category"]
    assert product_id not in similar
    assert (categories == products.set_index("id").loc[product_id, "category"]).mean() >= 0.6
    assert model.similar(-1) == []


def test_recommender_beats_the_popularity_baseline(products, interactions):
    metrics = evaluate_hit_rate(products, interactions)
    assert metrics["evaluated_users"] > 100
    assert metrics["hit_rate_at_10"] >= metrics["popularity_baseline"]


# ---------------------------------------------------------------- forecaster

def _history(days: int, base: float = 200.0) -> list[tuple[date, float]]:
    rng = np.random.default_rng(3)
    start = date(2026, 1, 1)
    out = []
    for i in range(days):
        day = start + timedelta(days=i)
        weekly = 1.5 if day.weekday() >= 5 else 1.0
        out.append((day, max(0.0, base * (1 + i / 365) * weekly + float(rng.normal(0, 10)))))
    return out


def test_forecast_learns_trend_and_weekly_seasonality():
    result = forecast(_history(180), horizon=14)

    assert len(result.points) == 14
    weekend = [p.revenue for p in result.points if p.date.weekday() >= 5]
    weekdays = [p.revenue for p in result.points if p.date.weekday() < 5]
    assert np.mean(weekend) > 1.3 * np.mean(weekdays)
    assert all(p.lower <= p.revenue <= p.upper for p in result.points)
    assert result.mae < result.baseline_mae


def test_forecast_fills_missing_days_and_never_goes_negative():
    history = [(d, v) for d, v in _history(60, base=5) if d.day % 3]
    result = forecast(history, horizon=7)
    assert all(p.revenue >= 0 and p.lower >= 0 for p in result.points)


def test_forecast_requires_four_weeks_of_history():
    with pytest.raises(NotEnoughHistory):
        forecast(_history(10))


# ---------------------------------------------------------------- return risk

def test_return_risk_model_ranks_risky_orders_higher(cod_orders):
    model = ReturnRiskModel().fit(cod_orders, source="test")
    assert model.metrics["cv_roc_auc"][model.model_name] > 0.65

    safe = {"total": 40, "item_count": 1, "shop_count": 1, "prior_orders": 12, "prior_returns": 0,
            "prior_return_rate": 0.0, "account_age_days": 300, "hour": 14, "is_weekend": 0, "first_order": 0,
            "city": "Lyon"}
    risky = {**safe, "total": 320, "prior_orders": 6, "prior_returns": 4, "prior_return_rate": 0.66,
             "hour": 2, "city": "Marseille"}
    assert 0 <= model.score(safe) < model.score(risky) <= 1


def test_return_risk_needs_enough_labelled_data(cod_orders):
    with pytest.raises(ValueError):
        ReturnRiskModel().fit(cod_orders.head(20), source="test")
    with pytest.raises(RuntimeError):
        ReturnRiskModel().score({})


def test_explanations_are_human_readable():
    reasons = explain({"first_order": 1, "total": 200, "hour": 1, "account_age_days": 3})
    assert reasons == ["First order from this customer", "Large basket", "Order placed late at night",
                       "Very recent account"]
    assert explain({"prior_orders": 5, "prior_return_rate": 0.5, "hour": 12}) == [
        "Customer refused or returned previous parcels"]


def test_generated_dataset_has_expected_shape(products, interactions, cod_orders):
    assert len(products) == 120
    assert {"user_id", "product_id", "quantity", "created_at"} <= set(interactions.columns)
    assert 0.05 < cod_orders["returned"].mean() < 0.4
    assert isinstance(cod_orders, pd.DataFrame)
