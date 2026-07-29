# HOUND: Headless Android and Raspberry Pi Zero Design

## Goal

Create a screenless, local-only robot-vision system that can learn one unfamiliar physical object, find and track it from a stationary camera, explain its decisions in a browser dashboard, and later control a wheeled Raspberry Pi Zero robot base.

## Scope

The first deliverable is a stationary demonstration. A screenless Android phone captures video, learns a target on demand, searches for it among distractors, tracks it, and retains a predicted state during a short occlusion. A laptop browser is the operator and dashboard. No cloud service and no laptop-side inference are used.

Movement support is an extension point: the Android device produces high-level motion intents; a Pi Zero agent later converts them to motor outputs and supplies sensor readings.

## Hardware and Roles

| Component | Responsibility |
| --- | --- |
| Old OnePlus phone, Android, Wi-Fi | Camera, on-device vision inference, tracker, target memory, dashboard server, mission controller |
| Laptop browser | Operator controls and read-only dashboard |
| Raspberry Pi Zero (later) | Motor-driver control, optional distance sensing and odometry, command acknowledgement |

The phone operates without a display after setup. It joins a local Wi-Fi network or creates a hotspot. USB tethering and ADB port forwarding are supported as transport alternatives, not a separate protocol.

## Architecture

### Android service

The Android application runs as a foreground service with these independent modules:

1. `CameraSource` captures frames using CameraX.
2. `TargetLearner` collects a short burst after the operator presses Learn, applies deterministic image augmentations, obtains embedding vectors, and averages plus L2-normalises them into one target prototype.
3. `CandidateFinder` proposes regions using motion/contours and optional lightweight object detections. It does not decide object class.
4. `TargetMatcher` computes cosine similarity between each candidate embedding and the prototype, then produces a target confidence.
5. `Tracker` combines the accepted candidate, bounding-box geometry, colour histogram, and optical flow. When observations disappear it transitions to occluded and predicts the position with a Kalman filter until it times out.
6. `MissionController` maintains the stationary hunt state now and emits abstract movement intents later.
7. `DashboardServer` provides a local HTTP/WebSocket API, controls, camera preview annotations, decision log, and a simple single-page dashboard.

The model is a TensorFlow Lite INT8 embedding encoder packaged in the app. The system never retrains it; learning means deriving a new target prototype locally from the supplied object.

### Pi Zero agent

The Pi process is deliberately small. It receives versioned JSON messages, validates them, sends motor-driver commands only after a future mobile-mode feature is enabled, and publishes acknowledgements and sensor data. It must boot and remain safe with motors disabled until an explicit arm command.

## Data Flow

1. A browser calls `POST /api/learn`.
2. The phone captures the configured learning frame burst and stores only the numeric target prototype plus non-identifying diagnostic data in memory.
3. For each camera frame, the phone proposes candidates, creates embeddings, scores them, tracks the best candidate, and publishes a `VisionState` update to dashboard clients.
4. If a reliable target is absent for the configured grace period, the tracker emits `OCCLUDED` with its last reliable position and predicted reappearance region. A matching candidate moves it back to `TRACKED`.
5. A future active-search policy transforms the memory grid into an abstract `MotionIntent`; the transport adapter sends this over Wi-Fi or USB to the Pi.

## Contracts

All phone-to-Pi traffic uses newline-delimited JSON with `protocolVersion: 1`. Transport is interchangeable: HTTP/WebSocket over Wi-Fi for normal use, or the same local HTTP endpoint exposed through USB tethering/ADB forwarding.

`MotionIntent`:

```json
{
  "protocolVersion": 1,
  "type": "motion_intent",
  "id": "uuid",
  "intent": "STOP",
  "durationMs": 0,
  "reason": "stationary_mode"
}
```

`VisionState` includes `mode` (`IDLE`, `LEARNING`, `SEARCHING`, `TRACKED`, `OCCLUDED`, `LOST`), top candidate score, target bounding box when available, last reliable time, predicted region, and a human-readable reason.

## Dashboard

The dashboard is intentionally explainable rather than a raw bounding-box feed. It includes:

- annotated camera preview with candidate and target scores;
- current mission state and target confidence;
- target memory showing last observation and occlusion prediction;
- a decision log stating why a candidate was accepted/rejected or why state changed;
- controls for Learn, Start Hunt, Pause, Reset, and an explicit future Arm Movement control.

The dashboard must state when operating in stationary mode and never imply that laptop software ran vision inference.

## Failure Handling and Safety

- Camera or model initialisation failures surface as a visible dashboard error and do not start a hunt.
- Learning rejects a burst with no stable foreground candidate and requests another presentation.
- Low-confidence matches remain candidates, not confirmed targets.
- Occlusion has a bounded prediction timeout; expiry produces `LOST`, not a false target claim.
- The Pi defaults to disarmed and forces STOP on malformed, expired, duplicate, or disconnected commands.
- All services work on a private local network without Internet access.

## Testing and Acceptance Criteria

Unit tests cover prototype aggregation, cosine scoring, state transitions, prediction timeout, protocol validation, and movement safety defaults. Instrumented Android tests verify the local API lifecycle with a fake camera/model. Pi tests run without hardware through a fake motor driver.

The stationary demo is successful when an operator can use a laptop browser to teach one object, see it selected among distractors, temporarily occlude it, observe `OCCLUDED` plus a predicted region, and see it reacquired with an explanation. It must work with no Internet connection and without interacting with the phone display during the demonstration.

## Non-goals for the First Version

- General-purpose object classification or retraining.
- Autonomous driving, obstacle avoidance, wheel odometry, or a full arena map.
- Cloud inference, remote telemetry, account login, or a companion laptop inference process.
- Recognition guarantees across extreme lighting, object deformation, or long occlusions.
