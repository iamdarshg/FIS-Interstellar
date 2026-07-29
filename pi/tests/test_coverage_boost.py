import io
import threading
import time
import pytest
from pydantic import ValidationError
from hound_pi.protocol import BoundingBox, MotionIntent, parse_line
from hound_pi.agent import AgentEngine
from hound_pi.server import Server
from hound_pi.transport import LineTransport


def test_bounding_box_order_validation() -> None:
    with pytest.raises(ValidationError):
        BoundingBox(xMin=0.8, yMin=0.2, xMax=0.3, yMax=0.7)

    with pytest.raises(ValidationError):
        BoundingBox(xMin=0.2, yMin=0.8, xMax=0.7, yMax=0.3)


def test_motion_intent_uuid_validation() -> None:
    with pytest.raises(ValidationError):
        MotionIntent(
            protocolVersion=1,
            type="motion_intent",
            id="not-a-uuid",
            sentAtMs=1000,
            intent="STOP",
            durationMs=0,
            reason="test"
        )


def test_parse_line_edge_cases() -> None:
    # > 4096 bytes
    long_line = "a" * 4097
    with pytest.raises(ValidationError):
        parse_line(long_line)

    # Invalid UTF-8
    with pytest.raises(ValidationError):
        parse_line(b"\xff\xfe\xfd")

    # Unknown type
    with pytest.raises(ValidationError):
        parse_line('{"protocolVersion":1,"type":"unknown_type"}')

    # Non-dict json
    with pytest.raises(ValidationError):
        parse_line('[1, 2, 3]')

    # Invalid JSON syntax
    with pytest.raises(ValidationError):
        parse_line('{invalid_json}')

    # Parse motion intent & command ack lines
    mi_json = '{"protocolVersion":1,"type":"motion_intent","id":"00000000-0000-0000-0000-000000000001","sentAtMs":100,"intent":"STOP","durationMs":0,"reason":"test"}'
    parsed_mi = parse_line(mi_json)
    assert parsed_mi.type == "motion_intent"

    ack_json = '{"protocolVersion":1,"type":"command_ack","commandId":"00000000-0000-0000-0000-000000000001","accepted":true,"reason":"ok"}'
    parsed_ack = parse_line(ack_json)
    assert parsed_ack.type == "command_ack"


def test_agent_engine_non_vision_state_handling() -> None:
    engine = AgentEngine()
    mi_json = '{"protocolVersion":1,"type":"motion_intent","id":"00000000-0000-0000-0000-000000000001","sentAtMs":100,"intent":"STOP","durationMs":0,"reason":"test"}'
    resp = engine.process_line(mi_json)
    assert "command_ack" in resp
    assert '"accepted":false' in resp


def test_line_transport_empty_lines() -> None:
    in_buf = io.StringIO("\n\nline1\n \nline2\n\n")
    out_buf = io.StringIO()
    transport = LineTransport(in_buf, out_buf)
    lines = list(transport.read_lines())
    assert lines == ["line1", "line2"]


def test_stdstream_server() -> None:
    server = Server()
    old_stdin = pytest.MonkeyPatch()
    in_buf = io.StringIO('{"protocolVersion":1,"type":"vision_state","timestampMs":100,"mode":"SEARCHING","confidence":0.5,"reason":"test"}\n')
    out_buf = io.StringIO()

    old_stdin.setattr("sys.stdin", in_buf)
    old_stdin.setattr("sys.stdout", out_buf)

    try:
        server.run_stdstream_server()
    finally:
        old_stdin.undo()

    assert "motion_intent" in out_buf.getvalue()


def test_server_tcp_socket_exceptions() -> None:
    server = Server()

    def stop_server_soon() -> None:
        time.sleep(0.2)
        server.stop()

    t = threading.Thread(target=stop_server_soon)
    t.start()

    # Run TCP server on random high port and let it time out & stop
    server.start_tcp_server("127.0.0.1", 0)
    t.join()
