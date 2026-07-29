import json
import socket
import threading
import time
import pytest
from hound_pi.agent import AgentEngine
from hound_pi.server import Server


def get_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def test_tcp_server_client_connect_reconnect_and_idle_timeout():
    port = get_free_port()
    server = Server()
    server_thread = threading.Thread(
        target=server.start_tcp_server,
        args=("127.0.0.1", port, 1.0),
        daemon=True
    )
    server_thread.start()
    time.sleep(0.1)

    try:
        s1 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s1.connect(("127.0.0.1", port))
        v_state_1 = json.dumps({
            "protocolVersion": 1,
            "type": "vision_state",
            "timestampMs": 100,
            "mode": "IDLE",
            "confidence": 0.0,
            "reason": "NONE"
        }) + "\n"
        s1.sendall(v_state_1.encode("utf-8"))
        resp1 = s1.recv(1024).decode("utf-8").strip()
        data1 = json.loads(resp1)
        assert data1["type"] == "motion_intent"
        assert data1["intent"] == "STOP"
        s1.close()

        s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s2.connect(("127.0.0.1", port))
        v_state_2 = json.dumps({
            "protocolVersion": 1,
            "type": "vision_state",
            "timestampMs": 200,
            "mode": "SEARCHING",
            "confidence": 0.5,
            "reason": "ACTIVE"
        }) + "\n"
        s2.sendall(v_state_2.encode("utf-8"))
        resp2 = s2.recv(1024).decode("utf-8").strip()
        data2 = json.loads(resp2)
        assert data2["type"] == "motion_intent"

        time.sleep(1.5)
        s2.settimeout(0.5)
        closed_data = s2.recv(1024)
        assert closed_data == b""
        s2.close()

    finally:
        server.stop()
        server_thread.join(timeout=2.0)
        assert not server_thread.is_alive()
