"""Runtime settings, read from environment variables (no secrets in code)."""

import os
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


@dataclass(frozen=True)
class Settings:
    # BACKEND_URL wins; BACKEND_HOSTPORT ("host:port", as Render provides) is the alternative.
    backend_url: str = field(default_factory=lambda: os.getenv(
        "BACKEND_URL", "http://" + os.getenv("BACKEND_HOSTPORT", "localhost:8080")))
    internal_api_key: str = field(default_factory=lambda: os.getenv("INTERNAL_API_KEY", "dev-internal-key"))
    model_dir: Path = field(default_factory=lambda: Path(os.getenv("MODEL_DIR", ROOT / "models")))
    data_dir: Path = field(default_factory=lambda: Path(os.getenv("DATA_DIR", ROOT / "data")))
    sync_enabled: bool = field(default_factory=lambda: os.getenv("AI_SYNC_ENABLED", "true").lower() == "true")
    sync_interval_seconds: int = field(default_factory=lambda: int(os.getenv("AI_SYNC_INTERVAL", "3600")))
    sync_retry_seconds: int = field(default_factory=lambda: int(os.getenv("AI_SYNC_RETRY", "30")))


def get_settings() -> Settings:
    return Settings()
