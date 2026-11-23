"""Hybrid product recommender.

Item-based collaborative filtering (cosine similarity between items computed from who bought them) blended with
content similarity (TF-IDF over product name, category and description). Content similarity covers new products
nobody bought yet (cold start); unknown users get the most popular products.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.preprocessing import normalize

CF_WEIGHT = 0.75
POPULARITY_PRIOR = 0.6


@dataclass
class Recommender:
    cf_weight: float = CF_WEIGHT
    product_ids: np.ndarray = field(default_factory=lambda: np.array([], dtype=int))
    similarity: np.ndarray | None = None
    user_vectors: dict[int, np.ndarray] = field(default_factory=dict)
    popularity: np.ndarray | None = None
    trained_from: str = "untrained"

    # ------------------------------------------------------------------ training

    def fit(self, products: pd.DataFrame, interactions: pd.DataFrame, source: str = "unknown") -> "Recommender":
        """products: id, name, category, description. interactions: user_id, product_id, quantity."""
        products = products.drop_duplicates("id").reset_index(drop=True)
        self.product_ids = products["id"].to_numpy(dtype=int)
        index = {pid: i for i, pid in enumerate(self.product_ids)}

        known = interactions[interactions["product_id"].isin(index)]
        users = known["user_id"].unique()
        user_index = {uid: i for i, uid in enumerate(users)}
        values = np.log1p(known["quantity"].to_numpy(dtype=float))
        matrix = sparse.csr_matrix(
            (values, (known["user_id"].map(user_index), known["product_id"].map(index))),
            shape=(len(users), len(self.product_ids)),
        )

        cf = cosine_similarity(normalize(matrix.T.tocsr(), axis=1)) if len(users) else np.zeros((len(index), len(index)))
        text = (products["name"].fillna("") + " " + products["category"].fillna("") + " "
                + products["category"].fillna("") + " " + products["description"].fillna(""))
        tfidf = TfidfVectorizer(stop_words="english", min_df=1, ngram_range=(1, 2)).fit_transform(text)
        content = cosine_similarity(tfidf)

        self.similarity = self.cf_weight * cf + (1 - self.cf_weight) * content
        np.fill_diagonal(self.similarity, 0.0)
        self.user_vectors = {uid: matrix.getrow(i).toarray().ravel() for uid, i in user_index.items()}
        self.popularity = np.asarray(matrix.sum(axis=0)).ravel()
        self.trained_from = source
        return self

    # ------------------------------------------------------------------ inference

    @property
    def is_trained(self) -> bool:
        return self.similarity is not None

    def recommend(self, user_id: int, limit: int = 8, exclude: set[int] | None = None) -> tuple[list[int], str]:
        """Returns (product ids, strategy) where strategy is 'personalized', 'hybrid' or 'popular'."""
        exclude = set(exclude or ())
        vector = self.user_vectors.get(user_id)
        if vector is None or not vector.any():
            return self._popular(limit, exclude), "popular"

        scores = vector @ self.similarity
        if scores.max() > 0 and self.popularity is not None and self.popularity.max() > 0:
            # A light popularity prior breaks ties between equally similar items in favour of proven sellers.
            scores = scores / scores.max() + POPULARITY_PRIOR * self.popularity / self.popularity.max()
            scores[vector @ self.similarity <= 0] = 0
        purchased = set(self.product_ids[vector > 0].tolist())
        ranked = [int(self.product_ids[i]) for i in np.argsort(-scores, kind="stable")
                  if scores[i] > 0 and int(self.product_ids[i]) not in purchased | exclude]
        picks = ranked[:limit]
        if len(picks) == limit:
            return picks, "personalized"
        filler = self._popular(limit, exclude | purchased | set(picks))
        return (picks + filler)[:limit], "hybrid"

    def similar(self, product_id: int, limit: int = 6) -> list[int]:
        if product_id not in set(self.product_ids.tolist()):
            return []
        row = self.similarity[self._pos(product_id)]
        return [int(self.product_ids[i]) for i in np.argsort(-row, kind="stable")[:limit] if row[i] > 0]

    def _popular(self, limit: int, exclude: set[int]) -> list[int]:
        order = np.argsort(-self.popularity, kind="stable") if self.popularity is not None else []
        return [int(self.product_ids[i]) for i in order if int(self.product_ids[i]) not in exclude][:limit]

    def _pos(self, product_id: int) -> int:
        return int(np.where(self.product_ids == product_id)[0][0])


def evaluate_hit_rate(products: pd.DataFrame, interactions: pd.DataFrame, k: int = 10) -> dict[str, float]:
    """Leave-last-purchase-out evaluation of new-item discovery: hit rate@k vs. a popularity baseline.

    For each user with history, the last purchased product is hidden; it counts only if the user had never bought it
    before (repeat purchases say nothing about discovery). Both methods exclude products the user already owns.
    """
    interactions = interactions.sort_values("created_at")
    last = interactions.groupby("user_id").tail(1)
    train = interactions.drop(last.index)
    owned = train.groupby("user_id")["product_id"].agg(set)
    eligible = [row for row in last.itertuples()
                if row.user_id in owned.index and row.product_id not in owned[row.user_id]]
    model = Recommender().fit(products, train, source="evaluation")

    hits = popular_hits = 0
    for row in eligible:
        user_owned = owned[row.user_id]
        picks, _ = model.recommend(int(row.user_id), k)
        hits += int(row.product_id in picks)
        popular_hits += int(row.product_id in model._popular(k, user_owned))
    total = max(len(eligible), 1)
    return {"hit_rate_at_10": round(hits / total, 4), "popularity_baseline": round(popular_hits / total, 4),
            "evaluated_users": int(len(eligible))}
