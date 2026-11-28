"""Catalog index for smart search and price suggestions.

Products are embedded with TF-IDF over words and character n-grams (typo tolerant), then projected with
truncated SVD (latent semantic analysis), which places products that share vocabulary close together even when the
query uses different words. A small hand-written synonym map expands shopping intents ("keep coffee hot" -> thermos,
insulated). Search blends lexical and latent similarity.

The same index gives sellers a price range: the prices of the most similar products, weighted by similarity.
Nothing is invented: if no comparable product exists the hint says so.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy import sparse
from sklearn.decomposition import TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

LEXICAL_WEIGHT = 0.6
MIN_SEARCH_SCORE = 0.08
MIN_PRICE_SIMILARITY = 0.1

# Intent words -> catalog vocabulary. Kept short and generic on purpose.
SYNONYMS: dict[str, tuple[str, ...]] = {
    "hot": ("warm", "insulated", "thermal", "heat"),
    "cold": ("insulated", "chilled", "thermal"),
    "coffee": ("espresso", "brew", "french press", "barista"),
    "tea": ("kettle", "infuser"),
    "music": ("audio", "speaker", "headphones", "sound"),
    "sound": ("audio", "speaker", "headphones"),
    "run": ("running", "jogging", "trainers", "sport"),
    "running": ("run", "jogging", "trainers"),
    "gym": ("fitness", "workout", "training", "yoga"),
    "sleep": ("bed", "pillow", "blanket", "night"),
    "cook": ("kitchen", "pan", "pot", "chef"),
    "cooking": ("kitchen", "pan", "pot", "chef"),
    "phone": ("smartphone", "mobile"),
    "laptop": ("notebook", "computer"),
    "computer": ("laptop", "keyboard", "mouse", "monitor"),
    "light": ("lamp", "led", "lighting"),
    "cozy": ("blanket", "candle", "cushion", "throw"),
    "cosy": ("blanket", "candle", "cushion", "throw"),
    "rain": ("waterproof", "jacket"),
    "water": ("waterproof", "bottle", "hydration"),
    "kids": ("children", "toy", "game"),
    "gift": ("set", "box"),
    "travel": ("backpack", "suitcase", "portable"),
    "hiking": ("outdoor", "trail", "backpack", "boots"),
    "relax": ("candle", "blanket", "yoga"),
}

TOKEN = re.compile(r"[a-z0-9]+")


def expand_query(query: str) -> str:
    words = TOKEN.findall(query.lower())
    extra = [synonym for word in words for synonym in SYNONYMS.get(word, ())]
    return " ".join([query.lower(), *extra])


def _document(row: pd.Series) -> str:
    name = str(row.get("name") or "")
    # The name counts double: it is the most precise signal.
    return " ".join([name, name, str(row.get("category") or ""), str(row.get("description") or "")]).lower()


@dataclass
class PriceHint:
    low: float
    typical: float
    high: float
    suggested: float
    confidence: str
    comparables: list[dict]


@dataclass
class CatalogIndex:
    product_ids: np.ndarray = field(default_factory=lambda: np.array([], dtype=int))
    names: list[str] = field(default_factory=list)
    categories: list[str] = field(default_factory=list)
    prices: np.ndarray = field(default_factory=lambda: np.array([], dtype=float))
    words: TfidfVectorizer | None = None
    chars: TfidfVectorizer | None = None
    svd: TruncatedSVD | None = None
    lexical: sparse.csr_matrix | None = None
    latent: np.ndarray | None = None
    trained_from: str = "untrained"

    @property
    def is_trained(self) -> bool:
        return self.lexical is not None and len(self.product_ids) > 0

    def fit(self, products: pd.DataFrame, source: str = "unknown") -> "CatalogIndex":
        products = products.drop_duplicates("id").reset_index(drop=True)
        documents = products.apply(_document, axis=1).tolist()
        self.product_ids = products["id"].to_numpy(dtype=int)
        self.names = products["name"].astype(str).tolist()
        self.categories = products["category"].fillna("").astype(str).tolist()
        self.prices = pd.to_numeric(products["price"], errors="coerce").fillna(0).to_numpy(dtype=float)

        self.words = TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, stop_words="english", min_df=1)
        self.chars = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True, min_df=1)
        self.lexical = normalize(sparse.hstack([self.words.fit_transform(documents),
                                                self.chars.fit_transform(documents)]).tocsr())
        components = max(2, min(120, len(documents) - 1, self.lexical.shape[1] - 1))
        self.svd = TruncatedSVD(n_components=components, random_state=42)
        self.latent = normalize(self.svd.fit_transform(self.lexical))
        self.trained_from = source
        return self

    def _similarity(self, text: str) -> np.ndarray:
        query = normalize(sparse.hstack([self.words.transform([text]), self.chars.transform([text])]).tocsr())
        lexical = (self.lexical @ query.T).toarray().ravel()
        latent = self.latent @ normalize(self.svd.transform(query)).ravel()
        return LEXICAL_WEIGHT * lexical + (1 - LEXICAL_WEIGHT) * np.clip(latent, 0, None)

    def search(self, query: str, limit: int = 12) -> list[tuple[int, float]]:
        if not self.is_trained or not query.strip():
            return []
        scores = self._similarity(expand_query(query))
        order = np.argsort(-scores)[:limit]
        return [(int(self.product_ids[i]), round(float(scores[i]), 4)) for i in order if scores[i] >= MIN_SEARCH_SCORE]

    def price_hint(self, name: str, category: str | None, description: str | None = None,
                   limit: int = 8) -> PriceHint | None:
        if not self.is_trained:
            return None
        scores = self._similarity(" ".join([name, name, category or "", description or ""]).lower())
        candidates = np.arange(len(self.product_ids))
        if category:
            wanted = category.lower().split(" / ")[-1].strip()
            same = np.array([c.lower() == wanted for c in self.categories])
            if same.sum() >= 3:
                candidates = candidates[same]
        candidates = candidates[(scores[candidates] >= MIN_PRICE_SIMILARITY) & (self.prices[candidates] > 0)]
        if len(candidates) == 0:
            return None
        top = candidates[np.argsort(-scores[candidates])[:limit]]
        weights = scores[top]
        prices = self.prices[top]
        low, typical, high = (_weighted_quantile(prices, weights, q) for q in (0.25, 0.5, 0.75))
        mean_similarity = float(weights.mean())
        confidence = "high" if len(top) >= 5 and mean_similarity >= 0.3 else "medium" if len(top) >= 3 else "low"
        comparables = [{"productId": int(self.product_ids[i]), "name": self.names[i], "price": round(float(self.prices[i]), 2),
                        "similarity": round(float(scores[i]), 3)} for i in top]
        return PriceHint(low=round(low, 2), typical=round(typical, 2), high=round(high, 2),
                         suggested=charm_price(typical), confidence=confidence, comparables=comparables)


def _weighted_quantile(values: np.ndarray, weights: np.ndarray, quantile: float) -> float:
    order = np.argsort(values)
    values, weights = values[order], weights[order]
    cumulative = np.cumsum(weights) - 0.5 * weights
    cumulative /= weights.sum()
    return float(np.interp(quantile, cumulative, values))


def charm_price(value: float) -> float:
    """Rounds to a familiar retail price point: 23.40 -> 22.99, 7.20 -> 6.99, 3.10 -> 3.10."""
    if value < 5:
        return round(value, 2)
    return round(max(math.floor(value) - 0.01, 0.99), 2) if value - math.floor(value) < 0.5 else round(
        math.floor(value) + 0.99, 2)
