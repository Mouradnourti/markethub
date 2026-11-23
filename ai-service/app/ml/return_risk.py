"""Cash-on-delivery return-risk classifier.

Predicts the probability that a COD parcel is refused at the door or returned. Two candidate models (logistic
regression and gradient boosting) are compared with stratified 5-fold cross-validated ROC AUC; the best one is
refitted on all data.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold, cross_val_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

NUMERIC_FEATURES = ["total", "item_count", "shop_count", "prior_orders", "prior_returns", "prior_return_rate",
                    "account_age_days", "hour", "is_weekend", "first_order"]
CATEGORICAL_FEATURES = ["city"]
FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES
TARGET = "returned"
MIN_SAMPLES = 150


def _preprocessor() -> ColumnTransformer:
    return ColumnTransformer([
        ("num", StandardScaler(), NUMERIC_FEATURES),
        ("cat", OneHotEncoder(handle_unknown="ignore", min_frequency=5, sparse_output=False), CATEGORICAL_FEATURES),
    ])


def _candidates() -> dict[str, Pipeline]:
    return {
        "logistic_regression": Pipeline([("prep", _preprocessor()),
                                         ("clf", LogisticRegression(max_iter=1000, class_weight="balanced"))]),
        "gradient_boosting": Pipeline([("prep", _preprocessor()),
                                       ("clf", GradientBoostingClassifier(n_estimators=150, max_depth=3,
                                                                          learning_rate=0.05, random_state=42))]),
    }


@dataclass
class ReturnRiskModel:
    pipeline: Pipeline | None = None
    model_name: str = "untrained"
    source: str = "untrained"
    metrics: dict = field(default_factory=dict)

    @property
    def is_trained(self) -> bool:
        return self.pipeline is not None

    def fit(self, data: pd.DataFrame, source: str) -> "ReturnRiskModel":
        if len(data) < MIN_SAMPLES or data[TARGET].nunique() < 2:
            raise ValueError(f"Need at least {MIN_SAMPLES} labelled orders with both outcomes")
        x, y = prepare(data), data[TARGET].astype(int)
        folds = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
        scores = {name: float(np.mean(cross_val_score(model, x, y, cv=folds, scoring="roc_auc")))
                  for name, model in _candidates().items()}
        best = max(scores, key=scores.get)
        self.pipeline = _candidates()[best].fit(x, y)
        self.model_name, self.source = best, source
        self.metrics = {"cv_roc_auc": {k: round(v, 4) for k, v in scores.items()}, "selected": best,
                        "samples": int(len(data)), "positive_rate": round(float(y.mean()), 4)}
        return self

    def score(self, features: dict) -> float:
        if not self.is_trained:
            raise RuntimeError("Return-risk model is not trained")
        return float(self.pipeline.predict_proba(prepare(pd.DataFrame([features])))[0, 1])


def prepare(data: pd.DataFrame) -> pd.DataFrame:
    frame = data.copy()
    for column in NUMERIC_FEATURES:
        frame[column] = pd.to_numeric(frame.get(column, 0), errors="coerce").fillna(0.0).astype(float)
    frame["city"] = frame.get("city", "unknown").fillna("unknown").astype(str).str.strip().str.title()
    return frame[FEATURES]


def explain(features: dict) -> list[str]:
    """Human-readable risk factors shown to sellers next to the score."""
    reasons = []
    if features.get("first_order"):
        reasons.append("First order from this customer")
    if float(features.get("prior_return_rate", 0)) >= 0.3 and int(features.get("prior_orders", 0)) > 0:
        reasons.append("Customer refused or returned previous parcels")
    if float(features.get("total", 0)) > 150:
        reasons.append("Large basket")
    hour = int(features.get("hour", 12))
    if hour >= 23 or hour < 6:
        reasons.append("Order placed late at night")
    if int(features.get("account_age_days", 365)) < 14:
        reasons.append("Very recent account")
    return reasons
