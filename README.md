# HOUND Ambitious Robot-Vision Project

HOUND is intended to become a headless Android and Raspberry Pi robot that remembers multiple unfamiliar objects, finds a requested target, predicts it through motion and occlusion, ignores named distractors, and returns home after an autonomous search. The current codebase contains a host-tested stationary foundation; the ambitious movement and mission features remain required before competition sign-off.

## Documentation Overview

- [Setup Guide](docs/SETUP.md): Environmental setup, Java/Python/Android dependencies, model exporter.
- [Operations Guide](docs/OPERATIONS.md): System architecture, web dashboard usage, operating modes.
- [Testing Matrix](docs/TESTING.md): DoD verification matrix, automated test gates, hardware requirements.
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md): Solutions for JVM, networking, and Pi hardware issues.
- [Beginner Hardware Validation TODO](docs/HARDWARE_VALIDATION_TODO.md): Safe, step-by-step phone, offline, soak, Pi, and motor validation with explicit pass/fail gates.
- [Final-Day Setup Guide](docs/FINAL_DAY_SETUP.md): Night-before packing, event setup, live demonstration, recovery, and fallback instructions.

## Architecture & Features

- **Headless Android Foreground Service:** CameraX capture, object candidate discovery, LiteRT MobileNetV3 embedding inference, occlusion-aware tracker, Ktor local dashboard server.
- **Explainable Browser Dashboard:** Real-time stream of vision state, candidate bounding boxes, similarity scores, decision logs, interactive control buttons.
- **Fail-Safe Raspberry Pi Agent:** Python agent running watchdog protocol, disarmed stationary mode, physical stop on timeout or error.

## Project Structure

```text
android/                         Android Gradle root (:app, :domain, :vision, :dashboard)
pi/hound_pi/                    Python protocol watchdog & server
pi/tests/                       Pi unit & property tests
protocol/schema/                Canonical JSON schemas
tools/model/                    MobileNetV3 model exporter
tools/fixtures/                 Fixture manifest & media generators
tools/tests/                    Exporter & fixture validation tests
scripts/                        Build & acceptance scripts
docs/                           Setup, operations, troubleshooting docs
```

## Running Verification

```powershell
.\scripts\check.ps1
.\scripts\acceptance.ps1
.\scripts\soak.ps1
```
