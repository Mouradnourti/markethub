import pandas as pd
import pytest

from app.ml.catalog_index import CatalogIndex, charm_price, expand_query


@pytest.fixture(scope="module")
def index() -> CatalogIndex:
    rows = [
        (1, "Insulated Steel Thermos 750 ml", "Kitchen", "Double-wall flask that keeps drinks hot for 12 hours.", 24.9),
        (2, "French Press Coffee Maker 1 L", "Kitchen", "Rich coffee in four minutes, keeps it hot for an hour.", 32.9),
        (3, "Trail Running Shoes", "Sports", "Grippy trainers for jogging on muddy paths.", 89.0),
        (4, "Wireless Headphones", "Audio", "Noise cancelling headphones with 30 hour battery.", 129.0),
        (5, "Bluetooth Speaker", "Audio", "Portable speaker with deep sound for music outdoors.", 59.0),
        (6, "Studio Headphones", "Audio", "Closed-back headphones for mixing music.", 99.0),
        (7, "Scandinavian Table Lamp", "Lighting", "Warm light with a linen shade.", 54.0),
        (8, "Over-ear Headphones", "Audio", "Comfortable headphones for travel.", 79.0),
    ]
    products = pd.DataFrame(rows, columns=["id", "name", "category", "description", "price"])
    return CatalogIndex().fit(products, source="backend")


def test_search_understands_intent_words(index):
    hits = [pid for pid, _ in index.search("something to keep my coffee hot", 3)]
    assert set(hits[:2]) == {1, 2}


def test_search_tolerates_typos_and_ignores_noise(index):
    assert index.search("hedphones", 3)[0][0] in {4, 6, 8}
    assert index.search("music", 5)[0][0] in {5, 6}
    assert index.search("zzzqqq", 5) == []


def test_price_hint_uses_similar_products_of_the_category(index):
    hint = index.price_hint("Noise cancelling headphones", "Electronics / Audio")
    assert hint is not None
    assert {c["productId"] for c in hint.comparables} <= {4, 5, 6, 8}
    assert 59.0 <= hint.low <= hint.typical <= hint.high <= 129.0
    assert hint.confidence in {"low", "medium", "high"}
    assert str(hint.suggested).endswith(".99")


def test_untrained_index_has_no_answers():
    assert CatalogIndex().search("lamp") == []
    assert CatalogIndex().price_hint("lamp", None) is None


def test_helpers():
    assert "insulated" in expand_query("keep it hot")
    assert charm_price(23.4) == 22.99
    assert charm_price(23.7) == 23.99
    assert charm_price(3.1) == 3.1
