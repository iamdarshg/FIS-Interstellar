import json
from pathlib import Path
from hypothesis import given, settings, strategies as st
import pytest
from pydantic import ValidationError

from hound_pi.protocol import (
    MotionIntent,
    parse_line,
)


FIXTURE_DIR = Path(__file__).parent.parent.parent / "protocol" / "fixtures"


def get_fixtures() -> list[Path]:
    return list(FIXTURE_DIR.glob("*.json"))


@pytest.mark.parametrize(
    "fixture_path",
    get_fixtures(),
    ids=lambda p: p.name,
)
def test_fixtures(fixture_path: Path) -> None:
    raw_data = fixture_path.read_text(encoding="utf-8")
    is_invalid = fixture_path.name.startswith("invalid-")

    if is_invalid:
        with pytest.raises(ValidationError):
            MotionIntent.model_validate_json(raw_data)
    else:
        parsed = MotionIntent.model_validate_json(raw_data)
        serialized = parsed.model_dump_json()
        deserialized_again = json.loads(serialized)
        original_dict = json.loads(raw_data)
        assert deserialized_again == original_dict


def test_parse_line_over_4096_bytes() -> None:
    large_line = b'{"protocolVersion":1,' + b"a" * 4100 + b"}"
    with pytest.raises(ValidationError):
        parse_line(large_line)


def test_parse_line_invalid_utf8() -> None:
    invalid_utf8 = b"\x80\x81\x82"
    with pytest.raises(ValidationError):
        parse_line(invalid_utf8)


@settings(max_examples=1000)
@given(
    duration_ms=st.integers(),
)
def test_hypothesis_duration_validation(duration_ms: int) -> None:
    data = {
        "protocolVersion": 1,
        "type": "motion_intent",
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "sentAtMs": 1000,
        "intent": "STOP",
        "durationMs": duration_ms,
        "reason": "hypothesis test",
    }
    if 0 <= duration_ms <= 500:
        parsed = MotionIntent.model_validate(data)
        assert 0 <= parsed.durationMs <= 500
    else:
        with pytest.raises(ValidationError):
            MotionIntent.model_validate(data)


@settings(max_examples=1000)
@given(
    extra_key=st.text(min_size=1, max_size=10).filter(
        lambda k: k not in {"protocolVersion", "type", "id", "sentAtMs", "intent", "durationMs", "reason"}
    ),
    extra_val=st.text(),
)
def test_hypothesis_extra_properties_rejected(extra_key: str, extra_val: str) -> None:
    data = {
        "protocolVersion": 1,
        "type": "motion_intent",
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "sentAtMs": 1000,
        "intent": "STOP",
        "durationMs": 100,
        "reason": "extra key test",
        extra_key: extra_val,
    }
    with pytest.raises(ValidationError):
        MotionIntent.model_validate(data)
