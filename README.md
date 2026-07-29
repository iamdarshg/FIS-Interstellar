# HOUND Stationary MVP

HOUND is a headless Android visual target learning and tracking system with an explainable web dashboard and safe Raspberry Pi motor control integration.

## Documentation Overview

- [Setup Guide](docs/SETUP.md): Environmental setup, Java/Python/Android dependencies, model exporter.
- [Operations Guide](docs/OPERATIONS.md): System architecture, web dashboard usage, operating modes.
- [Testing Matrix](docs/TESTING.md): DoD verification matrix, automated test gates, hardware requirements.
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md): Solutions for JVM, networking, and Pi hardware issues.

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
