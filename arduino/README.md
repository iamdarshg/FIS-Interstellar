# Arduino Uno SPI Slave Rover Controller

This directory contains the Arduino Uno sketch for receiving SPI movement and location commands from a Raspberry Pi (Zero / 3B+ / 4B) to drive a dual-motor rover body.

## Hardware Wiring Diagram

```text
Raspberry Pi (Zero)                 Arduino Uno
-------------------                 -----------
GPIO 10 (Pin 19 MOSI) ------------> Pin 11 (MOSI)
GPIO  9 (Pin 21 MISO) <------------ Pin 12 (MISO)
GPIO 11 (Pin 23 SCLK) ------------> Pin 13 (SCK)
GPIO  8 (Pin 24 CE0)  ------------> Pin 10 (SS)
GND     (Pin 6, 9, ..) -----------> GND

Motor Driver (L298N / Dual H-Bridge) <-> Arduino Uno
-----------------------------------     -----------
ENA (Left Motor Speed PWM) ------------> Pin 5
IN1 (Left Motor Direction 1) ----------> Pin 4
IN2 (Left Motor Direction 2) ----------> Pin 3
ENB (Right Motor Speed PWM) -----------> Pin 6
IN3 (Right Motor Direction 1) ---------> Pin 7
IN4 (Right Motor Direction 2) ---------> Pin 8
```

## Binary SPI Packet Protocol

Packets sent over MOSI from Raspberry Pi master to Arduino slave:

1. **Header Byte**: `0xAA` (Magic byte)
2. **Command Byte**:
   - `0x01`: `CMD_STOP`
   - `0x02`: `CMD_DRIVE_FORWARD`
   - `0x03`: `CMD_ROTATE_LEFT`
   - `0x04`: `CMD_ROTATE_RIGHT`
   - `0x05`: `CMD_MOVE_TO_REL`
   - `0x06`: `CMD_SET_VELOCITY`
3. **Length Byte**: `N` (number of payload bytes)
4. **Payload Bytes**: `[p0, p1, ... pN-1]`
5. **Checksum Byte**: XOR of Header + Cmd + Length + Payload bytes

Response sent back over MISO:
- `0x55` (Response Header), `Status` (`0x01` OK, `0x02` Checksum Err), `RoverState` (`0x00` Stopped, `0x01` Moving), `Checksum`

## Building & Flashing

Use Arduino IDE or `arduino-cli`:
```bash
arduino-cli compile --fqbn arduino:avr:uno rover_spi_slave
arduino-cli upload -p /dev/ttyACM0 --fqbn arduino:avr:uno rover_spi_slave
```
