import io
import json
import pytest
from hypothesis import given, settings, strategies as st
from hound_pi.agent import AgentEngine
from hound_pi.transport import LineTransport


def test_valid_vision_state_processing():
    engine = AgentEngine()
    valid_json = json.dumps({
        "protocolVersion": 1,
        "type": "vision_state",
        "timestampMs": 1000,
        "mode": "TRACKED",
        "confidence": 0.95,
        "reason": "OK"
    })
    response_str = engine.process_line(valid_json)
    data = json.loads(response_str)

    assert data["type"] == "motion_intent"
    assert data["intent"] == "STOP"
    assert data["durationMs"] == 0
    assert data["reason"] == "stationary_mode"


def test_corrupt_json_processing():
    engine = AgentEngine()
    response_str = engine.process_line("{corrupt json ...")
    data = json.loads(response_str)

    assert data["type"] == "command_ack"
    assert data["accepted"] is False
    assert data["reason"] == "INVALID_JSON"


@settings(max_examples=1000)
@given(st.text())
def test_hypothesis_arbitrary_string_resilience(random_input: str):
    engine = AgentEngine()
    response_str = engine.process_line(random_input)
    assert isinstance(response_str, str)
    parsed = json.loads(response_str)
    assert parsed["type"] in ("motion_intent", "command_ack")


def test_transport_chunked_stream():
    engine = AgentEngine()
    in_buf = io.StringIO(
        json.dumps({
            "protocolVersion": 1,
            "type": "vision_state",
            "timestampMs": 100,
            "mode": "IDLE",
            "confidence": 0.0,
            "reason": "NONE"
        }) + "\ninvalid line\n"
    )
    out_buf = io.StringIO()
    transport = LineTransport(in_buf, out_buf)

    engine.run(transport)

    lines = [l for l in out_buf.getvalue().split("\n") if l.strip()]
    assert len(lines) == 2
    r1 = json.loads(lines[0])
    r2 = json.loads(lines[1])

    assert r1["type"] == "motion_intent"
    assert r2["type"] == "command_ack"
    assert r2["accepted"] is False
