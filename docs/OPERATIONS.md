# HOUND Operations & Maintenance Guide

## System Architecture Overview
HOUND consists of:
1. **Android Headless Vision Engine:** Runs camera capture (320x240 @ 10fps max), ML Kit candidate finding, LiteRT embedding inference, target tracking, mission state machine, and Ktor embedded Web/WebSocket dashboard server (port 8080).
2. **Raspberry Pi Agent:** Runs Python fail-safe watchdog listener (port 8765) connected to motor drivers. Motors default to DISARMED/STOP.

## Web Dashboard Access
- Wi-Fi Access: `http://<PHONE_IP>:8080`
- USB Reverse Access:
  ```powershell
  .\scripts\usb-dashboard.ps1
  ```
  Access via `http://localhost:8080` in host browser.

## Operating Modes
- **IDLE:** Ready for target learning.
- **LEARNING:** Teaches target prototype from 12 candidate samples over 3 seconds.
- **SEARCHING:** Searching current camera frames for learned target (`MATCH_THRESHOLD = 0.78`).
- **TRACKED:** Confirmed target object tracked in frame.
- **OCCLUDED:** Target occluded (`OCCLUSION_GRACE_MS = 350`).
- **LOST:** Target lost after 3000 ms (`OCCLUSION_TIMEOUT_MS = 3000`).
