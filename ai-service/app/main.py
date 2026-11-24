"""FastAPI application. Interactive docs: http://localhost:8000/docs"""

from __future__ import annotations

import hmac
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.config import get_settings
from app.ml import copywriter
from app.ml.forecaster import NotEnoughHistory, forecast
from app.ml.return_risk import explain
from app.registry import ModelRegistry
from app.schemas import (
    CopyRequest,
    CopyResponse,
    ForecastPointOut,
    ForecastRequest,
    ForecastResponse,
    RecommendationRequest,
    RecommendationResponse,
    ReturnRiskRequest,
    ReturnRiskResponse,
    SpecOut,
)
from app.sync import SyncLoop, sync_from_backend
from scripts.generate_dataset import generate

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("ai-service")
settings = get_settings()
registry = ModelRegistry(settings.model_dir)


@asynccontextmanager
async def lifespan(_: FastAPI):
    registry.load()
    if not registry.return_risk.is_trained:
        log.info("No saved models: training on the synthetic dataset")
        if not (settings.data_dir / "cod_orders.csv").exists():
            generate(settings.data_dir)
        registry.train_from_csv(settings.data_dir)
    loop = SyncLoop(settings, registry) if settings.sync_enabled else None
    if loop:
        loop.start()
    yield
    if loop:
        loop.stop()


app = FastAPI(title="MarketHub AI service", version="1.0.0", lifespan=lifespan,
              description="Recommendations, sales forecasting and cash-on-delivery return-risk scoring.")


def require_internal_key(x_internal_key: str | None = Header(default=None)) -> None:
    if not x_internal_key or not hmac.compare_digest(x_internal_key, settings.internal_api_key):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or missing internal API key")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "recommender": registry.recommender.trained_from,
            "returnRisk": registry.return_risk.source}


@app.get("/models", dependencies=[Depends(require_internal_key)])
def models() -> dict:
    return registry.metrics


@app.post("/recommendations", response_model=RecommendationResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_key)])
def recommendations(request: RecommendationRequest) -> RecommendationResponse:
    # Ids only match the live catalog when the model was trained on backend data.
    if registry.recommender.trained_from != "backend":
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Recommender not trained on catalog data yet")
    ids, strategy = registry.recommender.recommend(request.user_id, request.limit, set(request.exclude_product_ids))
    return RecommendationResponse(product_ids=ids, strategy=strategy)


@app.post("/forecast", response_model=ForecastResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_key)])
def sales_forecast(request: ForecastRequest) -> ForecastResponse:
    try:
        result = forecast([(p.date, p.revenue) for p in request.history], request.horizon_days)
    except NotEnoughHistory as error:
        raise HTTPException(422, str(error)) from error
    return ForecastResponse(model=result.model, mae=result.mae, baseline_mae=result.baseline_mae,
                            forecast=[ForecastPointOut(**vars(p)) for p in result.points])


@app.post("/return-risk", response_model=ReturnRiskResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_key)])
def return_risk(request: ReturnRiskRequest) -> ReturnRiskResponse:
    if not registry.return_risk.is_trained:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Return-risk model not trained")
    features = request.model_dump()
    score = registry.return_risk.score(features)
    level = "high" if score >= 0.35 else "medium" if score >= 0.15 else "low"
    return ReturnRiskResponse(score=round(score, 4), level=level, reasons=explain(features),
                              model=f"{registry.return_risk.model_name} ({registry.return_risk.source})")


@app.post("/copywriter", response_model=CopyResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_key)])
def write_copy(request: CopyRequest) -> CopyResponse:
    """Drafts a tagline, description and key benefits from the seller's product name and keywords."""
    copy = copywriter.write(request.name, request.category, request.keywords, request.tone, request.variant)
    return CopyResponse(tagline=copy.tagline, description=copy.description, highlights=copy.highlights,
                        specs=[SpecOut(label=label, value=value) for label, value in copy.specs],
                        suggested_spec_labels=copy.suggested_spec_labels, category_profile=copy.category_profile)


@app.post("/train", dependencies=[Depends(require_internal_key)])
def train() -> dict:
    try:
        return sync_from_backend(settings, registry)
    except Exception as error:  # noqa: BLE001
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"Could not retrain from the backend: {error}") from error
