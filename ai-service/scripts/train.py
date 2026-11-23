"""Trains and saves the models.

Usage:
  python -m scripts.train                 # from the synthetic CSVs (generated first if missing)
  python -m scripts.train --source backend  # from the live backend export
"""

from __future__ import annotations

import argparse
import json
import logging

from app.config import get_settings
from app.registry import ModelRegistry
from app.sync import sync_from_backend
from scripts.generate_dataset import generate


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser(description="Train MarketHub AI models")
    parser.add_argument("--source", choices=["synthetic", "backend"], default="synthetic")
    args = parser.parse_args()
    settings = get_settings()
    registry = ModelRegistry(settings.model_dir)

    if args.source == "backend":
        metrics = sync_from_backend(settings, registry)
    else:
        if not (settings.data_dir / "cod_orders.csv").exists():
            print("Generating synthetic dataset:", generate(settings.data_dir))
        metrics = registry.train_from_csv(settings.data_dir)
    print(json.dumps(metrics, indent=2, default=str))


if __name__ == "__main__":
    main()
