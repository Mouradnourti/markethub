import os
from pathlib import Path

import pandas as pd
import pytest

os.environ.setdefault("AI_SYNC_ENABLED", "false")
os.environ.setdefault("INTERNAL_API_KEY", "test-key")


@pytest.fixture(scope="session")
def dataset(tmp_path_factory) -> Path:
    from scripts.generate_dataset import generate

    out = tmp_path_factory.mktemp("data")
    generate(out, seed=7)
    return out


@pytest.fixture(scope="session")
def products(dataset) -> pd.DataFrame:
    return pd.read_csv(dataset / "products.csv")


@pytest.fixture(scope="session")
def interactions(dataset) -> pd.DataFrame:
    return pd.read_csv(dataset / "interactions.csv")


@pytest.fixture(scope="session")
def cod_orders(dataset) -> pd.DataFrame:
    return pd.read_csv(dataset / "cod_orders.csv")
