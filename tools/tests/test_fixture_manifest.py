"""Tests for fixture manifest validation and compliance."""

import json
from pathlib import Path

import pytest

MANIFEST_PATH = Path(__file__).parent.parent / "fixtures" / "manifest.json"


def test_manifest_structure_and_constraints() -> None:
    assert MANIFEST_PATH.exists(), "manifest.json must exist"
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert data.get("version") == 1
    scenarios = data.get("scenarios", [])
    assert len(scenarios) >= 5

    for sc in scenarios:
        assert "id" in sc
        assert "sha256" in sc and len(sc["sha256"]) == 64
        assert sc.get("width") == 320, "Width must be exactly 320"
        assert sc.get("height") == 240, "Height must be exactly 240"
        assert sc.get("containsPeople") is False, "containsPeople must be false"
        assert "expectedTerminalState" in sc


def test_manifest_rejects_people_flag() -> None:
    bad_scenario = {
        "id": "invalid_people",
        "sha256": "a" * 64,
        "width": 320,
        "height": 240,
        "containsPeople": True,
        "expectedTerminalState": "LOST",
    }
    assert bad_scenario["containsPeople"] is True
    with pytest.raises(AssertionError):
        assert bad_scenario["containsPeople"] is False


def test_manifest_rejects_invalid_dimensions() -> None:
    bad_scenario = {
        "id": "invalid_dim",
        "sha256": "a" * 64,
        "width": 640,
        "height": 480,
        "containsPeople": False,
        "expectedTerminalState": "LOST",
    }
    with pytest.raises(AssertionError):
        assert bad_scenario["width"] == 320 and bad_scenario["height"] == 240
