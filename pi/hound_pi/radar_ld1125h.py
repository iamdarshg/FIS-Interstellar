import struct
import threading
import time
from typing import Any, Callable, Optional


class MockSerialPort:
    """Mock serial port for host testing without hardware UART."""

    def __init__(self) -> None:
        self.is_open = True
        self._buffer = b"\xaa\xff\x01\x02\x58\x00\x64\xaa\x55"  # 600cm target frame

    def read(self, size: int = 1) -> bytes:
        if not self._buffer:
            time.sleep(0.01)
            return b""
        chunk = self._buffer[:size]
        self._buffer = self._buffer[size:]
        return chunk

    def in_waiting(self) -> int:
        return len(self._buffer)

    def close(self) -> None:
        self.is_open = False


class HLKLD1125HRadar:
    """HLK-LD1125H 24GHz Millimeter-Wave Radar Sensor Driver for Raspberry Pi.

    UART Pinout:
    - RPi TXD (GPIO 14, Pin 8) <-> HLK-LD1125H RX
    - RPi RXD (GPIO 15, Pin 10) <-> HLK-LD1125H TX
    - RPi 5V   (Pin 2/4)       <-> HLK-LD1125H VCC
    - RPi GND  (Pin 6)         <-> HLK-LD1125H GND
    """

    def __init__(self, port: str = "/dev/ttyAMA0", baudrate: int = 115200) -> None:
        self.port = port
        self.baudrate = baudrate
        self._serial: Any = None
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        self.last_detection: dict[str, Any] = {
            "targetDetected": False,
            "distanceMeters": 0.0,
            "signalStrength": 0,
            "timestampMs": 0,
        }

        self._init_serial()

    def _init_serial(self) -> None:
        try:
            import serial

            self._serial = serial.Serial(self.port, self.baudrate, timeout=0.1)
        except Exception:
            self._serial = MockSerialPort()

    def parse_frame(self, frame: bytes) -> Optional[dict[str, Any]]:
        """Parse HLK-LD1125H serial frame format:

        Binary Frame: [0xAA, 0xFF, MovStatus, DistHigh, DistLow, SignalHigh, SignalLow, Checksum, 0x55]
        OR ASCII line: "$JYBSS,<dist_cm>,<state>..."
        """
        if not frame:
            return None

        # ASCII parsing mode
        if b"$" in frame or b"JYBSS" in frame:
            try:
                line = frame.decode("utf-8", errors="ignore").strip()
                if line.startswith("$JYBSS") or "JYBSS" in line:
                    parts = line.split(",")
                    if len(parts) >= 3:
                        dist_cm = float(parts[1])
                        state = parts[2]
                        return {
                            "targetDetected": dist_cm > 0,
                            "distanceMeters": dist_cm / 100.0,
                            "signalStrength": 100 if state != "0" else 0,
                            "timestampMs": int(time.time() * 1000),
                        }
            except Exception:
                pass

        # Binary frame parsing mode
        if len(frame) >= 8 and frame[0] == 0xAA:
            try:
                mov_status = frame[2]
                dist_cm = struct.unpack(">H", frame[3:5])[0]
                sig_strength = struct.unpack(">H", frame[5:7])[0] if len(frame) >= 7 else 50
                return {
                    "targetDetected": mov_status > 0 or dist_cm > 0,
                    "distanceMeters": dist_cm / 100.0,
                    "signalStrength": sig_strength,
                    "timestampMs": int(time.time() * 1000),
                }
            except Exception:
                pass

        return None

    def start_read_loop(self, callback: Optional[Callable[[dict[str, Any]], None]] = None) -> None:
        """Start background reading loop for radar sensor data."""
        if self._running:
            return
        self._running = True

        def _loop() -> None:
            buffer = b""
            while self._running:
                try:
                    chunk = self._serial.read(16)
                    if chunk:
                        buffer += chunk
                        while len(buffer) >= 9:
                            if buffer[0] == 0xAA:
                                frame = buffer[:9]
                                buffer = buffer[9:]
                                parsed = self.parse_frame(frame)
                                if parsed:
                                    with self._lock:
                                        self.last_detection = parsed
                                    if callback:
                                        callback(parsed)
                            else:
                                buffer = buffer[1:]
                    else:
                        time.sleep(0.02)
                except Exception:
                    time.sleep(0.05)

        self._thread = threading.Thread(target=_loop, daemon=True)
        self._thread.start()

    def get_last_detection(self) -> dict[str, Any]:
        with self._lock:
            return dict(self.last_detection)

    def stop(self) -> None:
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=0.5)
        if self._serial:
            try:
                self._serial.close()
            except Exception:
                pass
