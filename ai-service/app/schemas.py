"""API contracts of the AI service (camelCase on the wire, like the Java backend)."""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RecommendationRequest(ApiModel):
    user_id: int
    limit: int = Field(default=8, ge=1, le=50)
    exclude_product_ids: list[int] = Field(default_factory=list)


class RecommendationResponse(ApiModel):
    product_ids: list[int]
    strategy: str


class SalesPoint(ApiModel):
    date: date
    revenue: float = Field(ge=0)


class ForecastRequest(ApiModel):
    history: list[SalesPoint] = Field(min_length=1)
    horizon_days: int = Field(default=30, ge=1, le=90)


class ForecastPointOut(ApiModel):
    date: date
    revenue: float
    lower: float
    upper: float


class ForecastResponse(ApiModel):
    model: str
    mae: float
    baseline_mae: float
    forecast: list[ForecastPointOut]


class ReturnRiskRequest(ApiModel):
    total: float = Field(ge=0)
    item_count: int = Field(ge=1)
    shop_count: int = Field(ge=1)
    prior_orders: int = Field(ge=0)
    prior_returns: int = Field(ge=0)
    prior_return_rate: float = Field(ge=0, le=1)
    account_age_days: int = Field(ge=0)
    hour: int = Field(ge=0, le=23)
    is_weekend: int = Field(ge=0, le=1)
    first_order: int = Field(ge=0, le=1)
    city: str = "unknown"


class ReturnRiskResponse(ApiModel):
    score: float
    level: str
    reasons: list[str]
    model: str
