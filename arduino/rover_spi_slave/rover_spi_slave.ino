/*
 * HOUND Rover Body SPI Slave Controller for Arduino Uno
 * 
 * Hardware Wiring (Raspberry Pi Zero <-> Arduino Uno):
 * - RPi MOSI (GPIO 10, Pin 19) <-> Arduino MOSI (Pin 11)
 * - RPi MISO (GPIO 9, Pin 21)  <-> Arduino MISO (Pin 12)
 * - RPi SCLK (GPIO 11, Pin 23) <-> Arduino SCK  (Pin 13)
 * - RPi CE0  (GPIO 8, Pin 24)  <-> Arduino SS   (Pin 10)
 * - RPi GND                   <-> Arduino GND
 *
 * Motor Driver (L298N / Dual H-Bridge):
 * - Left Motor:  ENA (Pin 5 PWM), IN1 (Pin 4), IN2 (Pin 3)
 * - Right Motor: ENB (Pin 6 PWM), IN3 (Pin 7), IN4 (Pin 8)
 */

#include <SPI.h>

// Motor Driver Pins
const int ENA = 5;
const int IN1 = 4;
const int IN2 = 3;
const int ENB = 6;
const int IN3 = 7;
const int IN4 = 8;

// Protocol Constants
const uint8_t MAGIC_HEADER = 0xAA;
const uint8_t RESPONSE_HEADER = 0x55;

enum CommandType {
  CMD_STOP          = 0x01,
  CMD_DRIVE_FORWARD = 0x02,
  CMD_ROTATE_LEFT   = 0x03,
  CMD_ROTATE_RIGHT  = 0x04,
  CMD_MOVE_TO_REL   = 0x05,
  CMD_SET_VELOCITY  = 0x06
};

enum AckStatus {
  ACK_OK             = 0x01,
  ACK_CHECKSUM_ERROR = 0x02,
  ACK_INVALID_CMD    = 0x03
};

// Rx State Machine
volatile uint8_t rxBuffer[32];
volatile uint8_t rxIndex = 0;
volatile bool packetReady = false;

// Tx Response Buffer
volatile uint8_t txBuffer[4] = {RESPONSE_HEADER, ACK_OK, 0x00, 0x00};
volatile uint8_t txIndex = 0;

// Rover State
unsigned long moveEndTime = 0;
bool isMoving = false;
int16_t currentTargetX = 0;
int16_t currentTargetY = 0;

void setup() {
  // Motor Output Pins
  pinMode(ENA, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(ENB, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  stopMotors();

  // SPI Slave Setup
  pinMode(MISO, OUTPUT);
  SPCR |= _BV(SPE);       // Turn on SPI in slave mode
  SPCR |= _BV(SPIE);      // Turn on SPI interrupt
  SPI.attachInterrupt();

  // Initial Response Packet: [Header, Status, MovingState, Checksum]
  txBuffer[0] = RESPONSE_HEADER;
  txBuffer[1] = ACK_OK;
  txBuffer[2] = 0x00; // Stopped
  txBuffer[3] = txBuffer[0] ^ txBuffer[1] ^ txBuffer[2];
}

// SPI Interrupt Service Routine
ISR(SPI_STC_vect) {
  uint8_t inByte = SPDR;

  // Send current byte from response buffer over MISO
  SPDR = txBuffer[txIndex];
  txIndex = (txIndex + 1) % sizeof(txBuffer);

  // Parse incoming MOSI byte stream
  if (!packetReady) {
    if (rxIndex == 0) {
      if (inByte == MAGIC_HEADER) {
        rxBuffer[rxIndex++] = inByte;
      }
    } else {
      rxBuffer[rxIndex++] = inByte;
      if (rxIndex >= 3) {
        uint8_t payloadLen = rxBuffer[2];
        uint8_t expectedTotalLen = 3 + payloadLen + 1; // Header + Cmd + Len + Payload + Checksum
        if (rxIndex >= expectedTotalLen || rxIndex >= sizeof(rxBuffer)) {
          packetReady = true;
        }
      }
    }
  }
}

void setMotorSpeeds(int leftSpeed, int rightSpeed) {
  // Left Motor
  if (leftSpeed > 0) {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, LOW);
    analogWrite(ENA, min(abs(leftSpeed), 255));
  } else if (leftSpeed < 0) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, HIGH);
    analogWrite(ENA, min(abs(leftSpeed), 255));
  } else {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    analogWrite(ENA, 0);
  }

  // Right Motor
  if (rightSpeed > 0) {
    digitalWrite(IN3, HIGH);
    digitalWrite(IN4, LOW);
    analogWrite(ENB, min(abs(rightSpeed), 255));
  } else if (rightSpeed < 0) {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, HIGH);
    analogWrite(ENB, min(abs(rightSpeed), 255));
  } else {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    analogWrite(ENB, 0);
  }
}

void stopMotors() {
  setMotorSpeeds(0, 0);
  isMoving = false;
}

void processIncomingPacket() {
  uint8_t cmd = rxBuffer[1];
  uint8_t len = rxBuffer[2];
  uint8_t receivedChecksum = rxBuffer[3 + len];

  // Calculate Checksum (XOR)
  uint8_t calcChecksum = 0;
  for (uint8_t i = 0; i < 3 + len; i++) {
    calcChecksum ^= rxBuffer[i];
  }

  if (calcChecksum != receivedChecksum) {
    txBuffer[1] = ACK_CHECKSUM_ERROR;
  } else {
    txBuffer[1] = ACK_OK;
    executeCommand(cmd, (uint8_t*)&rxBuffer[3], len);
  }

  txBuffer[2] = isMoving ? 0x01 : 0x00;
  txBuffer[3] = txBuffer[0] ^ txBuffer[1] ^ txBuffer[2];

  // Reset Rx state
  rxIndex = 0;
  packetReady = false;
}

void executeCommand(uint8_t cmd, uint8_t* payload, uint8_t len) {
  switch (cmd) {
    case CMD_STOP:
      stopMotors();
      break;

    case CMD_DRIVE_FORWARD: {
      uint8_t speed = (len > 0) ? payload[0] : 200;
      uint16_t duration = (len > 2) ? ((payload[1] << 8) | payload[2]) : 200;
      setMotorSpeeds(speed, speed);
      isMoving = true;
      moveEndTime = millis() + duration;
      break;
    }

    case CMD_ROTATE_LEFT: {
      uint8_t speed = (len > 0) ? payload[0] : 180;
      uint16_t duration = (len > 2) ? ((payload[1] << 8) | payload[2]) : 150;
      setMotorSpeeds(-speed, speed);
      isMoving = true;
      moveEndTime = millis() + duration;
      break;
    }

    case CMD_ROTATE_RIGHT: {
      uint8_t speed = (len > 0) ? payload[0] : 180;
      uint16_t duration = (len > 2) ? ((payload[1] << 8) | payload[2]) : 150;
      setMotorSpeeds(speed, -speed);
      isMoving = true;
      moveEndTime = millis() + duration;
      break;
    }

    case CMD_MOVE_TO_REL: {
      if (len >= 5) {
        int16_t relX = (payload[0] << 8) | payload[1];
        int16_t relY = (payload[2] << 8) | payload[3];
        uint8_t speed = payload[4];

        currentTargetX = relX;
        currentTargetY = relY;

        // Compute relative distance & angle to target
        float dist = sqrt((float)relX * relX + (float)relY * relY);
        float angleDeg = atan2((float)relX, (float)relY) * 180.0 / 3.14159;

        // Convert distance/angle to timed movement (approx 1000ms per 50cm, 5ms per deg)
        uint16_t turnTime = abs(angleDeg) * 5.0;
        uint16_t driveTime = (dist / 50.0) * 1000.0;

        if (turnTime > 20) {
          if (angleDeg < 0) {
            setMotorSpeeds(-speed, speed); // Turn Left
          } else {
            setMotorSpeeds(speed, -speed); // Turn Right
          }
          isMoving = true;
          moveEndTime = millis() + turnTime;
        } else {
          setMotorSpeeds(speed, speed); // Drive Forward
          isMoving = true;
          moveEndTime = millis() + driveTime;
        }
      }
      break;
    }

    case CMD_SET_VELOCITY: {
      if (len >= 4) {
        int8_t leftDir = (int8_t)payload[0];
        uint8_t leftMag = payload[1];
        int8_t rightDir = (int8_t)payload[2];
        uint8_t rightMag = payload[3];

        setMotorSpeeds(leftDir * leftMag, rightDir * rightMag);
        isMoving = (leftMag > 0 || rightMag > 0);
        moveEndTime = millis() + 500;
      }
      break;
    }

    default:
      txBuffer[1] = ACK_INVALID_CMD;
      break;
  }
}

void loop() {
  if (packetReady) {
    processIncomingPacket();
  }

  // Safety / Timed Movement Stop
  if (isMoving && millis() >= moveEndTime) {
    stopMotors();
  }
}
