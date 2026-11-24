from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

HEADERS = {"X-Internal-Key": "test-key"}


@pytest.fixture(scope="module")
def client(dataset, products, interactions, tmp_path_factory):
    import app.main as main

    main.registry.model_dir = tmp_path_factory.mktemp("models")
    main.settings.__dict__.update(data_dir=dataset)  # frozen dataclass: tests reuse the generated dataset
    with TestClient(main.app) as test_client:
        # Simulate a sync from the backend so the recommender serves catalog ids.
        main.registry.train(products, interactions, None, source="backend")
        yield test_client


def test_health_is_public(client):
    body = client.get("/health").json()
    assert body["status"] == "UP"
    assert body["recommender"] == "backend"


def test_endpoints_require_the_internal_key(client):
    assert client.post("/return-risk", json={}).status_code == 401
    assert client.post("/forecast", json={}, headers={"X-Internal-Key": "wrong"}).status_code == 401


def test_recommendations(client, interactions):
    user = int(interactions["user_id"].iloc[0])
    response = client.post("/recommendations", json={"userId": user, "limit": 4, "excludeProductIds": [1]},
                           headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert len(body["productIds"]) == 4 and 1 not in body["productIds"]
    assert body["strategy"] in {"personalized", "hybrid", "popular"}


def test_forecast(client):
    start = date(2026, 6, 1)
    history = [{"date": (start + timedelta(days=i)).isoformat(), "revenue": 100 + (50 if i % 7 in (5, 6) else 0)}
               for i in range(90)]
    response = client.post("/forecast", json={"history": history, "horizonDays": 10}, headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert len(body["forecast"]) == 10 and body["baselineMae"] >= 0
    assert set(body["forecast"][0]) == {"date", "revenue", "lower", "upper"}

    short = client.post("/forecast", json={"history": history[:5]}, headers=HEADERS)
    assert short.status_code == 422


def test_return_risk(client):
    features = {"total": 250, "itemCount": 2, "shopCount": 1, "priorOrders": 0, "priorReturns": 0,
                "priorReturnRate": 0, "accountAgeDays": 2, "hour": 2, "isWeekend": 1, "firstOrder": 1,
                "city": "Marseille"}
    response = client.post("/return-risk", json=features, headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert 0 <= body["score"] <= 1 and body["level"] in {"low", "medium", "high"}
    assert "First order from this customer" in body["reasons"]

    invalid = client.post("/return-risk", json={**features, "hour": 30}, headers=HEADERS)
    assert invalid.status_code == 422


def test_models_metrics_and_failed_retrain(client):
    assert "recommender" in client.get("/models", headers=HEADERS).json()
    assert client.post("/train", headers=HEADERS).status_code == 502  # no backend in tests


def test_copywriter_drafts_listing_copy(client):
    response = client.post("/copywriter", headers=HEADERS, json={
        "name": "Trail Runner Backpack 20 L", "category": "Sports & Outdoors",
        "keywords": ["waterproof", "padded laptop sleeve"], "tone": "premium"})
    assert response.status_code == 200
    body = response.json()
    assert body["categoryProfile"] == "sports"
    assert body["tagline"] and "Trail Runner Backpack 20 L" in body["description"]
    assert "Padded laptop sleeve" in body["highlights"]
    assert {"label": "Capacity", "value": "20 L"} in body["specs"]
    assert client.post("/copywriter", headers=HEADERS, json={"name": "x"}).status_code == 422
