import asyncio
import json
import uuid
from aiohttp.test_utils import TestClient, TestServer
import pytest
from pydantic import ValidationError

from hound_pi.agent import AgentEngine
from hound_pi.dashboard_html import get_dashboard_html
from hound_pi.protocol import (
    LocationCommand,
    MapState,
    Object2D,
    Position2D,
    parse_line,
)
from hound_pi.radar_ld1125h import HLKLD1125HRadar, MockSerialPort
from hound_pi.server import Server
from hound_pi.spi_driver import (
    CMD_STOP,
    MockSpiDevice,
    SPIRoverController,
    calc_checksum,
    create_spi_packet,
)


def test_spi_packet_creation_and_checksum() -> None:
    pkt = create_spi_packet(CMD_STOP)
    assert pkt[0] == 0xAA
    assert pkt[1] == CMD_STOP
    assert pkt[2] == 0  # length
    assert calc_checksum(pkt[:-1]) == pkt[-1]


def test_mock_spi_device() -> None:
    mock = MockSpiDevice()
    mock.open(0, 0)
    rx = mock.xfer2([0xAA, CMD_STOP, 0x00, 0xAA])
    assert rx[0] == 0x55
    assert mock.last_cmd == CMD_STOP
    mock.close()


def test_spi_rover_controller_methods() -> None:
    ctrl = SPIRoverController()
    assert ctrl.is_hardware is False

    res_stop = ctrl.stop()
    assert res_stop["accepted"] is True

    res_motion = ctrl.send_motion("DRIVE_FORWARD", duration_ms=300, speed=0.8)
    assert res_motion["accepted"] is True

    res_loc = ctrl.send_location(1.5, 2.0, speed=0.9)
    assert res_loc["accepted"] is True
    assert res_loc["targetX"] == 1.5
    assert res_loc["targetY"] == 2.0

    res_vel = ctrl.send_velocity(0.5, -0.5)
    assert res_vel["accepted"] is True

    ctrl.close()


def test_location_command_and_map_state_protocol() -> None:
    loc = LocationCommand(
        protocolVersion=1,
        type="location_command",
        id=str(uuid.uuid4()),
        sentAtMs=1000,
        targetX=2.5,
        targetY=-1.2,
        speed=0.9,
        reason="test_location",
    )
    dumped = loc.model_dump_json()
    parsed = parse_line(dumped)
    assert isinstance(parsed, LocationCommand)
    assert parsed.targetX == 2.5

    pos = Position2D(x=1.0, y=2.0)
    assert pos.x == 1.0

    obj = Object2D(
        id="obj-1",
        label="Target Object",
        x=2.0,
        y=3.0,
        confidence=0.95,
        distance=3.6,
        angle=45.0,
        lastSeenMs=1000,
    )
    map_st = MapState(
        protocolVersion=1,
        type="map_state",
        timestampMs=1000,
        roverX=0.0,
        roverY=0.0,
        roverHeading=0.0,
        objects=[obj],
    )
    parsed_map = parse_line(map_st.model_dump_json())
    assert isinstance(parsed_map, MapState)
    assert len(parsed_map.objects) == 1


def test_invalid_location_command() -> None:
    with pytest.raises(ValidationError):
        LocationCommand.model_validate({
            "protocolVersion": 1,
            "type": "location_command",
            "id": "invalid-uuid",
            "sentAtMs": 1000,
            "targetX": 0.0,
            "targetY": 0.0,
            "speed": 2.0,  # speed > 1.0
            "reason": "invalid",
        })


def test_agent_engine_with_spi_and_location() -> None:
    ctrl = SPIRoverController()
    engine = AgentEngine(spi_controller=ctrl)

    loc_cmd_json = json.dumps({
        "protocolVersion": 1,
        "type": "location_command",
        "id": str(uuid.uuid4()),
        "sentAtMs": 1000,
        "targetX": 3.0,
        "targetY": 4.0,
        "speed": 1.0,
        "reason": "navigate_to_target",
    })
    res_str = engine.process_line(loc_cmd_json)
    res = json.loads(res_str)
    assert res["type"] == "command_ack"
    assert res["accepted"] is True
    assert res["reason"] == "LOCATION_COMMAND_ACCEPTED"

    map_json = json.dumps({
        "protocolVersion": 1,
        "type": "map_state",
        "timestampMs": 1000,
        "roverX": 1.0,
        "roverY": 1.0,
        "roverHeading": 90.0,
        "objects": [],
    })
    res_map = json.loads(engine.process_line(map_json))
    assert res_map["accepted"] is True
    assert engine.latest_map_state is not None
    assert engine.latest_map_state.roverHeading == 90.0


def test_dashboard_html_generation() -> None:
    html = get_dashboard_html()
    assert "HOUND ROVER AP" in html
    assert "2D Spatial Vision Map" in html
    assert "<canvas id=\"mapCanvas\"" in html


def test_web_server_endpoints() -> None:
    async def _async_test() -> None:
        server = Server()
        test_server = TestServer(server.create_web_app())
        async with TestClient(test_server) as client:
            resp = await client.get("/")
            assert resp.status == 200
            text = await resp.text()
            assert "HOUND ROVER AP" in text

            resp = await client.get("/api/state")
            assert resp.status == 200
            data = await resp.json()
            assert data["status"] == "OK"

            resp = await client.get("/api/map")
            assert resp.status == 200

            resp = await client.get("/api/radar")
            assert resp.status == 200

            det_payload = {
                "id": "target-1",
                "label": "Red Ball",
                "x": 2.5,
                "y": 1.8,
                "confidence": 0.98,
            }
            resp = await client.post("/api/vision/detection", json=det_payload)
            assert resp.status == 200

            loc_payload = {
                "targetX": 2.5,
                "targetY": 1.8,
                "speed": 1.0,
                "reason": "click_red_ball",
            }
            resp = await client.post("/api/control/location", json=loc_payload)
            assert resp.status == 200

            motion_payload = {"intent": "DRIVE_FORWARD", "durationMs": 200}
            resp = await client.post("/api/control/motion", json=motion_payload)
            assert resp.status == 200

            resp = await client.post("/api/map/reset")
            assert resp.status == 200

    asyncio.run(_async_test())


def test_web_server_error_branches() -> None:
    async def _async_test() -> None:
        server = Server()
        obj = Object2D(
            id="o1",
            label="Obstacle",
            x=1.0,
            y=2.0,
            confidence=0.9,
            distance=2.2,
            angle=0.0,
            lastSeenMs=100,
        )
        server.engine.latest_map_state = MapState(
            protocolVersion=1,
            type="map_state",
            timestampMs=100,
            roverX=0.5,
            roverY=0.5,
            roverHeading=45.0,
            objects=[obj],
        )
        test_server = TestServer(server.create_web_app())
        async with TestClient(test_server) as client:
            resp = await client.get("/api/map")
            assert resp.status == 200
            mdata = await resp.json()
            assert mdata["roverHeading"] == 45.0

            resp = await client.post("/api/control/location", json={"targetX": "invalid"})
            assert resp.status == 400

            resp = await client.post(
                "/api/control/motion",
                data="invalid_json",
                headers={"Content-Type": "application/json"},
            )
            assert resp.status == 400

            resp = await client.post(
                "/api/vision/detection",
                data="invalid_json",
                headers={"Content-Type": "application/json"},
            )
            assert resp.status == 400

    asyncio.run(_async_test())


def test_spi_driver_branch_coverage() -> None:
    ctrl = SPIRoverController()
    assert ctrl.send_motion("ROTATE_LEFT")["accepted"] is True
    assert ctrl.send_motion("ROTATE_RIGHT")["accepted"] is True
    assert ctrl.send_motion("STOP")["accepted"] is True
    assert ctrl.send_motion("UNKNOWN_INTENT")["accepted"] is True

    engine = AgentEngine(spi_controller=ctrl)
    vs_json = json.dumps({
        "protocolVersion": 1,
        "type": "vision_state",
        "timestampMs": 100,
        "mode": "TRACKED",
        "confidence": 0.9,
        "reason": "tracked",
    })
    res = json.loads(engine.process_line(vs_json))
    assert res["type"] == "motion_intent"

    ctrl.close()


def test_radar_ld1125h_driver() -> None:
    radar = HLKLD1125HRadar()
    parsed_binary = radar.parse_frame(b"\xaa\xff\x01\x02\x58\x00\x64\xaa\x55")
    assert parsed_binary is not None
    assert parsed_binary["targetDetected"] is True
    assert parsed_binary["distanceMeters"] == 6.0

    parsed_ascii = radar.parse_frame(b"$JYBSS,250.0,1,100\r\n")
    assert parsed_ascii is not None
    assert parsed_ascii["distanceMeters"] == 2.5

    assert radar.parse_frame(b"") is None

    # Test read loop with mock serial
    detections: list[dict] = []
    radar.start_read_loop(callback=lambda d: detections.append(d))
    radar.start_read_loop()  # duplicate call branch
    import time
    time.sleep(0.1)
    radar.stop()

    det = radar.get_last_detection()
    assert "targetDetected" in det

    mock_sp = MockSerialPort()
    assert mock_sp.in_waiting() >= 0
    mock_sp.close()

    # Corrupt parsing coverage
    assert radar.parse_frame(b"$JYBSS,corrupt,data\r\n") is None
    assert radar.parse_frame(b"\xaa\xff\x00") is None


def test_sensor_fusion() -> None:
    from hound_pi.protocol import BoundingBox, VisionMode, VisionState
    from hound_pi.sensor_fusion import fuse_vision_and_radar

    vs = VisionState(
        protocolVersion=1,
        type="vision_state",
        timestampMs=100,
        mode=VisionMode.TRACKED,
        confidence=0.95,
        targetBox=BoundingBox(xMin=0.4, yMin=0.2, xMax=0.6, yMax=0.8),
        reason="OK",
    )
    radar_det = {"targetDetected": True, "distanceMeters": 2.0}

    fused = fuse_vision_and_radar(vs, radar_det)
    assert fused is not None
    assert fused.distance == 2.0
    assert fused.label == "Fused Vision+Radar Target"

    # Test fallback to vision default distance
    fused_default = fuse_vision_and_radar(vs, {"targetDetected": False})
    assert fused_default is not None
    assert fused_default.distance == 1.5
    assert fused_default.label == "Vision Target"

    fused_low_dist = fuse_vision_and_radar(vs, {"targetDetected": True, "distanceMeters": 0.05})
    assert fused_low_dist is not None
    assert fused_low_dist.distance == 1.5

    # Test vision without box
    vs_nobox = VisionState(
        protocolVersion=1,
        type="vision_state",
        timestampMs=100,
        mode=VisionMode.SEARCHING,
        confidence=0.0,
        reason="NONE",
    )
    assert fuse_vision_and_radar(vs_nobox, radar_det) is None


def test_radar_ld1125h_loop_coverage() -> None:
    radar = HLKLD1125HRadar()
    radar._serial._buffer = b""
    radar.start_read_loop()
    import time
    time.sleep(0.05)
    radar.stop()


def test_agent_engine_radar_fusion_coverage() -> None:
    radar = HLKLD1125HRadar()
    engine = AgentEngine(radar=radar)
    vs_json = json.dumps({
        "protocolVersion": 1,
        "type": "vision_state",
        "timestampMs": 100,
        "mode": "TRACKED",
        "confidence": 0.95,
        "targetBox": {"xMin": 0.4, "yMin": 0.2, "xMax": 0.6, "yMax": 0.8},
        "reason": "OK",
    })
    res = json.loads(engine.process_line(vs_json))
    assert res["type"] == "motion_intent"
    assert engine.latest_map_state is not None
    assert len(engine.latest_map_state.objects) == 1


def test_server_and_spi_remaining_coverage() -> None:
    async def _async_test() -> None:
        server = Server()
        test_server = TestServer(server.create_web_app())
        async with TestClient(test_server) as client:
            resp = await client.post(
                "/api/control/location",
                data="invalid json string",
                headers={"Content-Type": "application/json"},
            )
            assert resp.status == 400

            server.engine.spi_controller = None
            resp = await client.post("/api/control/motion", json={"intent": "DRIVE_FORWARD"})
            assert resp.status == 200

            resp = await client.post(
                "/api/vision/detection",
                data="invalid json string",
                headers={"Content-Type": "application/json"},
            )
            assert resp.status == 400

    asyncio.run(_async_test())


def test_radar_ld1125h_more_coverage() -> None:
    radar = HLKLD1125HRadar()
    assert radar.parse_frame(b"$JYBSS,300.0,0,50\r\n") is not None
    assert radar.parse_frame(b"SOME_OTHER_ASCII_LINE\r\n") is None
    assert radar.parse_frame(b"\xaa\xff\x01\x00") is None
    assert radar.parse_frame(b"\xaa\xff\x01\x00\x00\x00\x00\x00") is not None


def test_radar_ld1125h_loop_frame_processing() -> None:
    radar = HLKLD1125HRadar()
    radar._serial._buffer = b"\x00\xaa\xff\x01\x02\x58\x00\x64\xaa\x55"
    detections: list[dict] = []
    radar.start_read_loop(callback=lambda d: detections.append(d))
    import time
    time.sleep(0.1)
    radar.stop()
    assert len(detections) > 0


def test_spi_driver_extra_coverage() -> None:
    ctrl = SPIRoverController()
    ctrl._spi_device.xfer2 = lambda d: [0x00, 0x00]
    res = ctrl.stop()
    assert res["accepted"] is False
    ctrl.close()







