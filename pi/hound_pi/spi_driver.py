import math
import struct
import threading
from typing import Any

MAGIC_HEADER = 0xAA
RESPONSE_HEADER = 0x55

CMD_STOP = 0x01
CMD_DRIVE_FORWARD = 0x02
CMD_ROTATE_LEFT = 0x03
CMD_ROTATE_RIGHT = 0x04
CMD_MOVE_TO_REL = 0x05
CMD_SET_VELOCITY = 0x06

ACK_OK = 0x01
ACK_CHECKSUM_ERROR = 0x02
ACK_INVALID_CMD = 0x03


def calc_checksum(data: bytes) -> int:
    chk = 0
    for b in data:
        chk ^= b
    return chk


def create_spi_packet(cmd: int, payload: bytes = b"") -> bytes:
    length = len(payload)
    header = bytes([MAGIC_HEADER, cmd, length])
    body = header + payload
    chk = calc_checksum(body)
    return body + bytes([chk])


class MockSpiDevice:
    """Mock SPI device for host environment / testing when spidev is absent."""

    def __init__(self) -> None:
        self.sent_packets: list[bytes] = []
        self.last_cmd: int = CMD_STOP
        self.max_speed_hz: int = 100000
        self.mode: int = 0

    def open(self, bus: int, device: int) -> None:
        pass

    def close(self) -> None:
        pass

    def xfer2(self, data: list[int]) -> list[int]:
        pkt = bytes(data)
        self.sent_packets.append(pkt)
        if len(pkt) >= 2:
            self.last_cmd = pkt[1]
        response = [RESPONSE_HEADER, ACK_OK, 0x00, RESPONSE_HEADER ^ ACK_OK ^ 0x00]
        # Pad response to match input length
        if len(response) < len(data):
            response.extend([0x00] * (len(data) - len(response)))
        return response[: len(data)]


class SPIRoverController:
    """Controls rover motor hardware connected to Arduino Uno over SPI bus."""

    def __init__(self, bus: int = 0, device: int = 0, speed_hz: int = 100000) -> None:
        self._lock = threading.Lock()
        self.bus = bus
        self.device = device
        self.speed_hz = speed_hz
        self._is_hardware = False
        self._spi_device: Any = None
        self._sent_packets: list[bytes] = []

        self._init_spi()

    def _init_spi(self) -> None:
        try:
            import spidev

            spi = spidev.SpiDev()
            spi.open(self.bus, self.device)
            spi.max_speed_hz = self.speed_hz
            spi.mode = 0
            self._spi_device = spi
            self._is_hardware = True
        except (ImportError, OSError, Exception):
            self._spi_device = MockSpiDevice()
            self._is_hardware = False

    @property
    def is_hardware(self) -> bool:
        return self._is_hardware

    def send_raw(self, packet: bytes) -> dict[str, Any]:
        with self._lock:
            self._sent_packets.append(packet)
            tx_data = list(packet)
            rx_data = self._spi_device.xfer2(tx_data)

            rx_bytes = bytes(rx_data)
            ack_ok = len(rx_bytes) >= 2 and rx_bytes[0] == RESPONSE_HEADER and rx_bytes[1] == ACK_OK
            is_moving = len(rx_bytes) >= 3 and rx_bytes[2] == 0x01

            return {
                "accepted": ack_ok,
                "isHardware": self._is_hardware,
                "isMoving": is_moving,
                "rxHex": rx_bytes.hex(),
                "txHex": packet.hex(),
            }

    def stop(self) -> dict[str, Any]:
        pkt = create_spi_packet(CMD_STOP)
        return self.send_raw(pkt)

    def send_motion(self, intent: str, duration_ms: int = 200, speed: float = 1.0) -> dict[str, Any]:
        spd_byte = max(0, min(255, int(speed * 255)))
        dur_ms = max(0, min(65535, duration_ms))

        if intent == "DRIVE_FORWARD":
            cmd = CMD_DRIVE_FORWARD
        elif intent == "ROTATE_LEFT":
            cmd = CMD_ROTATE_LEFT
        elif intent == "ROTATE_RIGHT":
            cmd = CMD_ROTATE_RIGHT
        else:
            cmd = CMD_STOP

        if cmd == CMD_STOP:
            payload = b""
        else:
            payload = struct.pack(">BH", spd_byte, dur_ms)

        pkt = create_spi_packet(cmd, payload)
        return self.send_raw(pkt)

    def send_location(self, target_x: float, target_y: float, speed: float = 1.0) -> dict[str, Any]:
        """Send relative location target (in cm/mm) to move rover towards (target_x, target_y)."""
        # Convert coordinates in meters to mm (int16)
        x_mm = max(-32768, min(32767, int(target_x * 1000)))
        y_mm = max(-32768, min(32767, int(target_y * 1000)))
        spd_byte = max(0, min(255, int(speed * 255)))

        payload = struct.pack(">hhB", x_mm, y_mm, spd_byte)
        pkt = create_spi_packet(CMD_MOVE_TO_REL, payload)

        res = self.send_raw(pkt)
        res["targetX"] = target_x
        res["targetY"] = target_y
        res["distance"] = math.hypot(target_x, target_y)
        res["angleDeg"] = math.degrees(math.atan2(target_x, target_y))
        return res

    def send_velocity(self, left_speed: float, right_speed: float) -> dict[str, Any]:
        l_dir = 1 if left_speed >= 0 else -1
        r_dir = 1 if right_speed >= 0 else -1

        l_mag = max(0, min(255, int(abs(left_speed) * 255)))
        r_mag = max(0, min(255, int(abs(right_speed) * 255)))

        payload = struct.pack(">bBbB", l_dir, l_mag, r_dir, r_mag)
        pkt = create_spi_packet(CMD_SET_VELOCITY, payload)
        return self.send_raw(pkt)

    def close(self) -> None:
        with self._lock:
            if self._spi_device is not None:
                try:
                    self._spi_device.close()
                except Exception:
                    pass
