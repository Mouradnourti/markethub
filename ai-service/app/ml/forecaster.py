"""Sales forecasting per seller: time-series regression on calendar features.

Ridge regression on a linear trend, day-of-week one-hot columns and yearly Fourier terms. The model is fitted on
each request from the seller's own history (stateless), validated on the last 14 days against a seasonal-naive
baseline (same weekday last week), then refitted on the full history.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error
from sklearn.pipeline import Pipeline, make_pipeline
from sklearn.preprocessing import StandardScaler

MIN_HISTORY_DAYS = 28
YEARLY_TERMS_FROM_DAYS = 365  # yearly seasonality is only identifiable with a full year
HOLDOUT_DAYS = 14
Z_80 = 1.2816  # two-sided 80 % prediction interval


def _model() -> Pipeline:
    # Features are standardised so the ridge penalty does not shrink the trend more than the seasonality.
    return make_pipeline(StandardScaler(), Ridge(alpha=1.0))


def _smearing(model: Pipeline, x: np.ndarray, log_y: np.ndarray) -> float:
    """Duan's smearing factor: back-transforms log predictions to the expected value, not the median."""
    return float(np.mean(np.exp(log_y - model.predict(x))))


def _expected(log_prediction: np.ndarray, smear: float) -> np.ndarray:
    return np.clip(np.exp(log_prediction) * smear - 1, 0, None)


class NotEnoughHistory(ValueError):
    pass


@dataclass
class ForecastPoint:
    date: date
    revenue: float
    lower: float
    upper: float


@dataclass
class ForecastResult:
    points: list[ForecastPoint]
    mae: float
    baseline_mae: float
    model: str


def _features(days: pd.DatetimeIndex, origin: pd.Timestamp, yearly: bool) -> np.ndarray:
    t = ((days - origin).days.to_numpy() / 365.0).reshape(-1, 1)
    dow = np.eye(7)[days.dayofweek.to_numpy()][:, 1:]
    columns = [t, dow]
    if yearly:
        day_of_year = days.dayofyear.to_numpy() / 365.25 * 2 * np.pi
        columns.append(np.column_stack([np.sin(day_of_year), np.cos(day_of_year)]))
    return np.hstack(columns)


def _series(history: list[tuple[date, float]]) -> pd.Series:
    series = pd.Series({pd.Timestamp(d): float(v) for d, v in history}).sort_index()
    full_range = pd.date_range(series.index.min(), series.index.max(), freq="D")
    return series.reindex(full_range, fill_value=0.0)


def forecast(history: list[tuple[date, float]], horizon: int = 30) -> ForecastResult:
    series = _series(history)
    if len(series) < MIN_HISTORY_DAYS:
        raise NotEnoughHistory(f"At least {MIN_HISTORY_DAYS} days of sales history are needed")
    origin = series.index[0]
    yearly = len(series) >= YEARLY_TERMS_FROM_DAYS
    # Retail seasonality is multiplicative (week-ends grow with the trend): fit on log1p(revenue).
    x, y = _features(series.index, origin, yearly), series.to_numpy()
    log_y = np.log1p(y)

    # Validation on the most recent two weeks.
    validation_model = _model().fit(x[:-HOLDOUT_DAYS], log_y[:-HOLDOUT_DAYS])
    smear = _smearing(validation_model, x[:-HOLDOUT_DAYS], log_y[:-HOLDOUT_DAYS])
    mae = mean_absolute_error(y[-HOLDOUT_DAYS:], _expected(validation_model.predict(x[-HOLDOUT_DAYS:]), smear))
    baseline_mae = mean_absolute_error(y[-HOLDOUT_DAYS:], y[-HOLDOUT_DAYS - 7:-7])

    model = _model().fit(x, log_y)
    log_std = float(np.std(log_y - model.predict(x)))
    smear = _smearing(model, x, log_y)
    future = pd.date_range(series.index[-1] + timedelta(days=1), periods=horizon, freq="D")
    log_pred = model.predict(_features(future, origin, yearly))
    points = [ForecastPoint(day.date(), round(float(_expected(np.array([v]), smear)[0]), 2),
                            round(max(float(np.expm1(v - Z_80 * log_std)), 0.0), 2),
                            round(max(float(np.expm1(v + Z_80 * log_std)), 0.0), 2))
              for day, v in zip(future, log_pred)]
    seasonality = "weekly + yearly" if yearly else "weekly"
    return ForecastResult(points, round(float(mae), 2), round(float(baseline_mae), 2),
                          f"Ridge regression on log sales (trend + {seasonality} seasonality)")
