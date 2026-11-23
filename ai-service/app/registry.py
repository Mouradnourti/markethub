"""Holds the trained models, persists them with joblib and swaps them atomically after retraining."""

from __future__ import annotations

import json
import logging
import threading
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd

from app.ml.recommender import Recommender, evaluate_hit_rate
from app.ml.return_risk import ReturnRiskModel

log = logging.getLogger(__name__)


class ModelRegistry:
    def __init__(self, model_dir: Path):
        self.model_dir = model_dir
        self.recommender = Recommender()
        self.return_risk = ReturnRiskModel()
        self.metrics: dict = {}
        self._lock = threading.Lock()

    # ------------------------------------------------------------------ persistence

    def load(self) -> None:
        for name in ("recommender", "return_risk"):
            path = self.model_dir / f"{name}.joblib"
            if path.exists():
                setattr(self, name, joblib.load(path))
        metrics = self.model_dir / "metrics.json"
        if metrics.exists():
            self.metrics = json.loads(metrics.read_text())
        log.info("Models loaded: recommender=%s return_risk=%s", self.recommender.trained_from, self.return_risk.source)

    def _save(self) -> None:
        self.model_dir.mkdir(parents=True, exist_ok=True)
        joblib.dump(self.recommender, self.model_dir / "recommender.joblib")
        joblib.dump(self.return_risk, self.model_dir / "return_risk.joblib")
        (self.model_dir / "metrics.json").write_text(json.dumps(self.metrics, indent=2, default=str))

    # ------------------------------------------------------------------ training

    def train(self, products: pd.DataFrame, interactions: pd.DataFrame | None, cod_orders: pd.DataFrame | None,
              source: str) -> dict:
        """Trains what the data allows. A model that cannot be trained keeps its previous version."""
        recommender, risk, metrics = self.recommender, self.return_risk, dict(self.metrics)
        if interactions is not None and len(interactions):
            recommender = Recommender().fit(products, interactions, source=source)
            metrics["recommender"] = {"source": source, "products": int(len(products)),
                                      "interactions": int(len(interactions)),
                                      **evaluate_hit_rate(products, interactions)}
        if cod_orders is not None:
            try:
                risk = ReturnRiskModel().fit(cod_orders, source=source)
                metrics["return_risk"] = {"source": source, **risk.metrics}
            except ValueError as error:
                log.warning("Return-risk model not retrained from %s: %s", source, error)
        metrics["trained_at"] = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self.recommender, self.return_risk, self.metrics = recommender, risk, metrics
            self._save()
        return metrics

    def train_from_csv(self, data_dir: Path, source: str = "synthetic") -> dict:
        return self.train(pd.read_csv(data_dir / "products.csv"), pd.read_csv(data_dir / "interactions.csv"),
                          pd.read_csv(data_dir / "cod_orders.csv"), source)
