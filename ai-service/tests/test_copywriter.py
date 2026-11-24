from app.ml.copywriter import extract_specs, profile_for, write


def test_extracts_units_materials_and_colours():
    specs = dict(extract_specs("Nova X5 128 GB, 5,000 mAh, 65 W charger in black aluminium, 2-pack, 30 x 20 cm"))
    assert specs["Storage"] == "128 GB"
    assert specs["Battery"] == "5,000 mAh"
    assert specs["Power"] == "65 W"
    assert specs["Material"] == "Aluminium"
    assert specs["Colour"] == "Black"
    assert specs["Size"] == "30 x 20 cm"


def test_maps_categories_to_writing_profiles():
    assert profile_for("Smartphones & Tablets") == "electronics"
    assert profile_for("Home & Kitchen") == "home"
    assert profile_for("Beauty & Health") == "beauty"
    assert profile_for(None) == "general"


def test_never_invents_benefits_and_rewords_known_features():
    copy = write("Plain Mug", "Kitchen", [], "friendly")
    assert copy.highlights == []
    assert copy.specs == []

    copy = write("Travel Mug", "Kitchen", ["dishwasher safe", "leak-proof lid", "red"], "friendly")
    assert "Dishwasher safe for effortless cleaning" in copy.highlights
    assert "Leak-proof lid" in copy.highlights
    assert "Red" not in copy.highlights and ("Colour", "Red") in copy.specs


def test_output_is_deterministic_and_variants_change_wording():
    first = write("Linen Shirt", "Clothing", ["breathable"], "friendly", variant=0)
    assert write("Linen Shirt", "Clothing", ["breathable"], "friendly", variant=0) == first
    variants = {write("Linen Shirt", "Clothing", ["breathable"], "friendly", variant=v).description for v in range(3)}
    assert len(variants) > 1


def test_technical_tone_states_the_facts():
    copy = write("Electric Kettle 1.7 L", "Kitchen", ["2200 W"], "technical")
    assert "1.7 L capacity" in copy.tagline and "2200 W" in copy.tagline
    assert "Dimensions" in copy.suggested_spec_labels


def test_keeps_acronyms_intact_mid_sentence():
    copy = write("Nova X5", "Smartphones", ["AMOLED display", "fast charging"], "friendly")
    assert "AMOLED display" in copy.description
    assert "aMOLED" not in copy.description + copy.tagline
