"""Pulls the live training dataset from the backend over REST and retrains the models periodically."""

from __future__ import annotations

import logging
import threading

import httpx
import pandas as pd

from app.config import Settings
from app.registry import ModelRegistry

log = logging.getLogger(__name__)


def fetch_dataset(settings: Settings) -> dict[str, pd.DataFrame]:
    response = httpx.get(f"{settings.backend_url}/api/internal/ml/dataset",
                         headers={"X-Internal-Key": settings.internal_api_key}, timeout=30.0)
    response.raise_for_status()
    body = response.json()
    return {
        "products": pd.DataFrame(body["products"], columns=["id", "name", "category", "description", "price", "shop_id"]),
        "interactions": pd.DataFrame(body["interactions"], columns=["user_id", "product_id", "quantity", "created_at"]),
        "cod_orders": pd.DataFrame(body["codOrders"]),
    }


def sync_from_backend(settings: Settings, registry: ModelRegistry) -> dict:
    data = fetch_dataset(settings)
    if data["products"].empty:
        raise ValueError("The backend returned an empty catalog")
    metrics = registry.train(data["products"], data["interactions"], data["cod_orders"], source="backend")
    log.info("Models retrained from backend data: %s", metrics)
    return metrics


class SyncLoop(threading.Thread):
    """Retries until the backend is reachable, then refreshes the models every sync interval."""

    def __init__(self, settings: Settings, registry: ModelRegistry):
        super().__init__(daemon=True, name="model-sync")
        self.settings, self.registry = settings, registry
        self._stop_event = threading.Event()

    def run(self) -> None:
        while not self._stop_event.is_set():
            try:
                sync_from_backend(self.settings, self.registry)
                delay = self.settings.sync_interval_seconds
            except Exception as error:  # noqa: BLE001 - keep the loop alive whatever happens
                log.info("Backend dataset not available yet (%s), retrying", error)
                delay = self.settings.sync_retry_seconds
            self._stop_event.wait(delay)

    def stop(self) -> None:
        self._stop_event.set()
