# HOUND Stationary MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a headless Android application that learns and tracks one physical object, exposes an explainable browser dashboard, survives brief occlusion, and interoperates with a safe Raspberry Pi Zero movement agent over Wi-Fi or USB.

**Architecture:** The Android phone owns camera capture, candidate discovery, embedding inference, tracking, mission state, and the local dashboard. Pure Kotlin domain modules contain all deterministic vision-state logic and are tested without Android; Android adapters provide CameraX, ML Kit, LiteRT, and Ktor. A Python Pi agent implements the same versioned protocol and defaults to disarmed/STOP.

**Tech Stack:** JDK 17, Kotlin 2.1.20, Android Gradle Plugin 8.9.2, Gradle 8.11.1, compile/target SDK 35, min SDK 26, CameraX 1.6.1, ML Kit bundled object detection 17.0.2, LiteRT Play Services Java 16.4.0, Ktor 3.1.3, kotlinx.serialization 1.8.1, JUnit 5.12.2, Kotest property 5.9.1, MockK 1.13.17, Python 3.11+, pytest 8.3.5, Hypothesis 6.131.9, Ruff 0.11.2, mypy 1.15.0.

## Global Constraints

- The first deliverable is stationary; movement code exists but motors remain disarmed by default.
- All inference runs on the Android phone. The laptop only renders HTTP/WebSocket data.
- The system must work after installation with Wi-Fi but no Internet connection.
- USB tethering or `adb reverse tcp:8080 tcp:8080` exposes the same HTTP protocol; do not create a second USB protocol.
- Protocol messages use newline-delimited JSON and `protocolVersion: 1`.
- Never confirm a target below `MATCH_THRESHOLD = 0.78`.
- Enter occlusion after `OCCLUSION_GRACE_MS = 350`; enter lost after `OCCLUSION_TIMEOUT_MS = 3000`.
- Process camera inference at 320x240 and at most 10 frames per second. Drop stale frames; never queue camera frames.
- The Pi forces STOP when disarmed, disconnected for 500 ms, given malformed input, given a duplicate command ID, or given a command older than 500 ms.
- Every public state transition and safety rule requires a test. No task may be committed with a failing test, lint, or static-analysis command.
- Never commit APKs, generated models, Gradle caches, Python virtual environments, recordings containing people, or machine-specific SDK paths.

## Definition of Done

All of these must be true:

1. `./gradlew testDebugUnitTest lintDebug` passes.
2. `python -m pytest pi/tests tools/tests -q` passes with at least 95% branch coverage for `pi/hound_pi`.
3. Property tests execute at least 1,000 generated examples for vector and protocol invariants.
4. Recorded-frame regression tests give the expected state sequence for learn, distractor rejection, occlusion, reacquisition, and loss fixtures.
5. A 30-minute Android soak test has no crash, no monotonic heap growth above 20%, and p95 pipeline latency below 150 ms on the OnePlus.
6. A Pi disconnect or malformed-command test reaches physical STOP within 500 ms.
7. The acceptance run works offline and requires no touch interaction with the phone after service startup.

## Fixed Repository Map

Create exactly this structure; do not rename modules or combine responsibilities:

```text
android/                         Android Gradle root
  app/                          manifest, service, activity, assets
  domain/                       pure Kotlin models, matching, tracker, mission
  vision/                       CameraX, candidates, model adapter, pipeline
  dashboard/                    Ktor server and packaged web UI
pi/hound_pi/                    Python protocol, watchdog, motor adapters, server
pi/tests/                       Pi unit/property/integration tests
protocol/schema/                canonical JSON Schema documents
tools/model/                    reproducible MobileNetV3 model exporter
tools/fixtures/                 fixture manifest; media generated locally
tools/tests/                    exporter and fixture validation tests
scripts/                        single-command build and acceptance scripts
docs/                           setup, operation, test matrix, troubleshooting
```

## Execution Rules for a Small Model

- Execute tasks strictly in numeric order. Do not start the next task if the current task's final gate fails.
- Copy names, constants, JSON keys, ports, and signatures exactly. Do not substitute libraries.
- When a command fails, record the full output, repair only the current task, rerun the smallest failing test, then rerun that task's full gate.
- Update checkboxes in this file after every successful step.
- Commit only the paths listed in that task. Use the exact commit message shown.
- If physical hardware is unavailable, complete automated fake-adapter tests and mark only the explicitly named hardware gate as blocked; do not claim hardware success.

---

### Task 1: Reproducible Repository Skeleton

**Files:**
- Create: `.gitignore`, `.editorconfig`, `README.md`
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `pyproject.toml`, `scripts/check.ps1`, `scripts/check.sh`
- Create: `pi/hound_pi/__init__.py`, `pi/tests/test_bootstrap.py`, `tools/tests/test_bootstrap.py`

**Interfaces:**
- Produces Android modules `:app`, `:domain`, `:vision`, `:dashboard` and Python package `hound-pi`.

- [ ] Create `.gitignore` with `.gradle/`, `**/build/`, `.idea/`, `local.properties`, `*.apk`, `.venv/`, `__pycache__/`, `.pytest_cache/`, `.mypy_cache/`, `.ruff_cache/`, `*.tflite`, and `tools/fixtures/generated/`.
- [ ] Create `android/settings.gradle.kts` with `pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }`, dependency repositories `google()` and `mavenCentral()`, and includes for the four modules.
- [ ] Pin AGP `8.9.2` and Kotlin Android/JVM `2.1.20`; use Java toolchain 17, compile SDK 35, min SDK 26, and no dynamic dependency versions.
- [ ] Configure `pyproject.toml` for Python `>=3.11`, runtime dependencies `aiohttp==3.11.14`, `pydantic==2.10.6`, `gpiozero==2.0.1`, and dev dependencies from the Tech Stack line.
- [ ] Put `def test_bootstrap() -> None: assert True` in both bootstrap test files so test collection has an explicit passing baseline. Leave `pi/hound_pi/__init__.py` empty.
- [ ] Make `scripts/check.ps1` run `Push-Location android; .\gradlew.bat testDebugUnitTest lintDebug; Pop-Location; python -m ruff check pi tools; python -m mypy pi/hound_pi; python -m pytest pi/tests tools/tests --cov=pi/hound_pi --cov-branch --cov-fail-under=95` and preserve the first non-zero exit code.
- [ ] Generate the Gradle wrapper from the `android` directory with Gradle 8.11.1; verify `android/gradle/wrapper/gradle-wrapper.properties` contains `gradle-8.11.1-bin.zip`.
- [ ] Run `git diff --check`, `android\gradlew.bat -p android projects`, and `python -m pytest --collect-only pi/tests tools/tests`; expected: Gradle lists four modules and pytest collects exactly two bootstrap tests with exit code 0.
- [ ] Commit: `git add .gitignore .editorconfig README.md android pyproject.toml scripts pi/hound_pi/__init__.py pi/tests/test_bootstrap.py tools/tests/test_bootstrap.py && git commit -m "build: bootstrap HOUND workspace"`.

### Task 2: Canonical Protocol Schemas and Cross-Language Fixtures

**Files:**
- Create: `protocol/schema/motion-intent-v1.json`, `protocol/schema/vision-state-v1.json`, `protocol/schema/command-ack-v1.json`
- Create: `protocol/fixtures/*.json`, `pi/hound_pi/protocol.py`, `pi/tests/test_protocol.py`
- Create: `android/domain/src/main/kotlin/org/hound/domain/Protocol.kt`, `android/domain/src/test/kotlin/org/hound/domain/ProtocolTest.kt`

**Interfaces:**
- Produces `MotionIntent`, `VisionState`, `CommandAck`, `BoundingBox`, `VisionMode`, and `MotionKind` with identical serialized names in Kotlin and Python.
- `MotionIntent`: `protocolVersion:Int`, `type:String="motion_intent"`, `id:String`, `sentAtMs:Long`, `intent:STOP|ROTATE_LEFT|ROTATE_RIGHT|DRIVE_FORWARD`, `durationMs:Int`, `reason:String`.

- [ ] Write JSON Schemas with `additionalProperties:false`, protocol version fixed to `1`, required fields, `durationMs` range `0..500`, normalized box coordinates range `0.0..1.0`, and the exact enums above.
- [ ] Add valid fixture `motion-stop.json` and invalid fixtures for version 2, unknown key, negative duration, missing ID, and NaN-like string coordinate.
- [ ] Write Python tests parameterized over fixtures; valid messages must parse and serialize identically, invalid files must raise `pydantic.ValidationError`.
- [ ] Implement frozen Pydantic models with `extra="forbid"`; add `parse_line(line: bytes) -> MotionIntent` that rejects lines longer than 4096 bytes and non-UTF-8 input.
- [ ] Write Kotlin serialization tests reading the same fixture files via a Gradle test resource path; enable `ignoreUnknownKeys = false` and `isLenient = false`.
- [ ] Implement Kotlin `@Serializable` data classes. Validate version, finite coordinates, coordinate order, duration, and UUID format in `init` blocks.
- [ ] Add 1,000-example Hypothesis tests proving any accepted duration is within range and any arbitrary extra property is rejected. Add Kotest property tests proving bounding boxes outside normalized range throw.
- [ ] Run `python -m pytest pi/tests/test_protocol.py -q` and `android\gradlew.bat -p android :domain:test`; expected: all fixture and property tests pass.
- [ ] Commit: `git add protocol pi/hound_pi/protocol.py pi/tests/test_protocol.py android/domain && git commit -m "feat: define versioned control protocol"`.

### Task 3: Vector Math and Target Prototype

**Files:**
- Create: `android/domain/src/main/kotlin/org/hound/domain/Embedding.kt`
- Create: `android/domain/src/main/kotlin/org/hound/domain/TargetLearner.kt`
- Create: `android/domain/src/test/kotlin/org/hound/domain/EmbeddingTest.kt`
- Create: `android/domain/src/test/kotlin/org/hound/domain/TargetLearnerTest.kt`

**Interfaces:**
- Produces `fun l2Normalize(values: FloatArray): FloatArray`, `fun cosineSimilarity(a: FloatArray,b: FloatArray):Float`, and `TargetLearner.buildPrototype(samples:List<FloatArray>):FloatArray`.

- [ ] Test exact cases: `[3,4] -> [0.6,0.8]`, identical vectors score `1`, orthogonal score `0`, opposite score `-1`, mismatched lengths throw, empty arrays throw, zero/NaN/infinite vectors throw.
- [ ] Add Kotest property tests with 1,000 iterations: normalization output has norm within `1e-5` of 1; cosine is symmetric; cosine stays in `[-1,1]`; scaling a nonzero vector by a positive finite scalar does not change its normalized result beyond `1e-5`.
- [ ] Implement loops over `FloatArray`; use `Double` accumulators; reject non-finite values; clamp cosine only for floating-point drift.
- [ ] Test prototype aggregation using three known vectors. Require 8 through 32 samples, identical dimensions, and rejection of invalid samples. Average component-wise then L2-normalize once.
- [ ] Run `android\gradlew.bat -p android :domain:test --tests '*EmbeddingTest' --tests '*TargetLearnerTest'`; expected: pass with 1,000 property cases.
- [ ] Commit: `git add android/domain && git commit -m "feat: add target embedding math"`.

### Task 4: Deterministic Tracker and Occlusion State Machine

**Files:**
- Create: `android/domain/src/main/kotlin/org/hound/domain/Clock.kt`, `Observation.kt`, `Tracker.kt`
- Create: `android/domain/src/test/kotlin/org/hound/domain/TrackerTest.kt`, `TrackerPropertyTest.kt`

**Interfaces:**
- Produces `Tracker.update(observation: Observation?, nowMs:Long): VisionState` and `Tracker.reset():VisionState`.
- `Observation` contains box, similarity, horizontal/vertical velocity, and source frame timestamp.

- [ ] Encode the transition table in tests: reset→IDLE; startSearch→SEARCHING; score `<0.78` remains SEARCHING; score `>=0.78`→TRACKED; missing for 349 ms remains TRACKED; missing at 350 ms→OCCLUDED; missing at 3000 ms→LOST; accepted observation from OCCLUDED or LOST→TRACKED.
- [ ] Test predicted box uses constant velocity and clamps every edge to `[0,1]`. Test timestamps that move backward are rejected. Test stale observations older than 250 ms are rejected.
- [ ] Implement `Tracker` as a `when` over current mode. Keep `lastReliableObservation` immutable, compute elapsed from injected time, and place a non-empty reason on every state change.
- [ ] Add model-based property testing: generate 1,000 sequences of monotonic timestamps and optional observations; assert no state has a non-finite box, OCCLUDED always has a last reliable timestamp, and IDLE never carries a target box.
- [ ] Run `android\gradlew.bat -p android :domain:test --tests '*Tracker*'`; expected: all deterministic and generated sequences pass.
- [ ] Commit: `git add android/domain && git commit -m "feat: add occlusion-aware target tracker"`.

### Task 5: Mission Controller and Movement Safety Boundary

**Files:**
- Create: `android/domain/src/main/kotlin/org/hound/domain/MissionController.kt`
- Create: `android/domain/src/test/kotlin/org/hound/domain/MissionControllerTest.kt`

**Interfaces:**
- Produces `learn()`, `startHunt()`, `pause()`, `reset()`, `setMovementArmed(Boolean)`, and `motionFor(state):MotionIntent`.

- [ ] Test illegal actions with exact errors: hunt before prototype returns `NO_TARGET_LEARNED`; learn while hunting returns `PAUSE_BEFORE_LEARNING`; arm in stationary build returns `MOVEMENT_DISABLED`.
- [ ] Test every stationary-mode call to `motionFor` returns STOP, duration 0, and reason `stationary_mode`, regardless of vision state.
- [ ] Implement synchronized state changes and append `DecisionEvent(timestampMs, category, message)` to a ring buffer capped at 200 events.
- [ ] Add a concurrency test with 20 coroutines issuing 10,000 pause/reset/start calls; assert no exception, buffer size <=200, and final motion is STOP.
- [ ] Run `android\gradlew.bat -p android :domain:test --tests '*MissionControllerTest'`.
- [ ] Commit: `git add android/domain && git commit -m "feat: add stationary mission controller"`.

### Task 6: Reproducible INT8 Embedding Model

**Files:**
- Create: `tools/model/export_model.py`, `tools/model/requirements.lock`, `tools/model/model-metadata.json`
- Create: `tools/tests/test_export_model.py`, `scripts/export-model.ps1`, `scripts/export-model.sh`

**Interfaces:**
- Produces `android/app/src/main/assets/hound_embedding_v1.tflite` with input `[1,128,128,3]` uint8 and output `[1,576]` int8, plus SHA-256 recorded in metadata.

- [ ] Pin exporter dependencies: `tensorflow-cpu==2.16.2`, `numpy==1.26.4`, `pillow==10.4.0` in the lock file.
- [ ] Build `keras.applications.MobileNetV3Small(input_shape=(128,128,3), include_top=False, weights="imagenet", pooling="avg", include_preprocessing=True)`. Set `trainable=False`; do not add a classifier.
- [ ] Configure `TFLiteConverter` with `Optimize.DEFAULT`, representative samples loaded from exactly 100 images in a user-supplied directory, supported ops `TFLITE_BUILTINS_INT8`, and uint8 input/int8 output. Exit with code 2 unless exactly 100 readable JPEG/PNG files exist.
- [ ] Tests generate 100 deterministic synthetic colour/shape images in a temporary directory, run export, inspect tensor shapes/dtypes, run two inferences, assert finite output and identical output for identical input, and verify SHA-256 metadata.
- [ ] Add a negative test for 99 images and one corrupt image. Assert both fail with an exact actionable message.
- [ ] Run `python -m pytest tools/tests/test_export_model.py -q`; expected: pass. Then export with real representative object images before device acceptance; generated `.tflite` remains ignored by Git.
- [ ] Commit: `git add tools scripts .gitignore && git commit -m "build: add reproducible embedding model exporter"`.

### Task 7: Android App Shell and Headless Foreground Service

**Files:**
- Create: `android/app/build.gradle.kts`, `AndroidManifest.xml`, `HoundApplication.kt`, `MainActivity.kt`, `HoundService.kt`, notification resources
- Create: `android/app/src/test/kotlin/org/hound/app/HoundServiceStateTest.kt`

**Interfaces:**
- Produces service actions `START`, `STOP`, and `RESET`; exposes process-local `StateFlow<ServiceHealth>`.

- [ ] Declare CAMERA, INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, WAKE_LOCK; declare the service with foreground type `camera`. No storage or location permissions.
- [ ] MainActivity requests camera permission, starts the service, displays the phone URL and service health, and contains no vision logic.
- [ ] HoundService creates notification channel `hound_service`, calls `startForeground` before camera initialization, acquires a partial wake lock named `HOUND::Vision` only while running, and releases it in `onDestroy` using `try/finally`.
- [ ] Add Robolectric tests: denied camera permission yields ERROR/CAMERA_PERMISSION; repeated START is idempotent; STOP releases wake lock; camera initialization exception yields ERROR/CAMERA_INIT.
- [ ] Run `android\gradlew.bat -p android :app:testDebugUnitTest :app:lintDebug`; expected: zero test/lint failures.
- [ ] Commit: `git add android/app && git commit -m "feat: add headless Android service shell"`.

### Task 8: CameraX Latest-Frame Capture

**Files:**
- Create: `android/vision/build.gradle.kts`, `CameraSource.kt`, `Frame.kt`, `YuvToRgb.kt`
- Create: `android/vision/src/test/kotlin/org/hound/vision/YuvToRgbTest.kt`, `LatestFrameGateTest.kt`
- Create: `android/vision/src/androidTest/kotlin/org/hound/vision/CameraSourceInstrumentedTest.kt`

**Interfaces:**
- Produces `CameraSource.start(onFrame:(Frame)->Unit)`, `stop()`, and `Frame.close()`.

- [ ] Configure ImageAnalysis target resolution 320x240, backpressure `STRATEGY_KEEP_ONLY_LATEST`, RGBA output if supported, and a single analysis executor.
- [ ] Write pure tests using byte-array Y/U/V fixtures for black, white, red, and odd row-stride images. Assert RGB channels within tolerance 2 and rotations 0/90/180/270 map corners correctly.
- [ ] Implement frame ownership so every `ImageProxy` closes exactly once even when conversion or callback throws. Add a fake-image test that counts close calls.
- [ ] Instrumented test starts/stops camera 20 times, receives at least one frame each cycle, and asserts no analyzer callback occurs after stop.
- [ ] Run unit tests on host. On phone run `android\gradlew.bat -p android :vision:connectedDebugAndroidTest`; save result XML but do not commit device identifiers.
- [ ] Commit: `git add android/vision && git commit -m "feat: add bounded CameraX frame source"`.

### Task 9: Candidate Discovery and Image Preprocessing

**Files:**
- Create: `CandidateFinder.kt`, `MlKitCandidateFinder.kt`, `CropPreprocessor.kt`
- Create: `CandidateFinderTest.kt`, `CropPreprocessorTest.kt`

**Interfaces:**
- Produces `suspend fun candidates(frame:Frame):List<Candidate>` and `fun prepare(frame,candidate):ByteBuffer` of exactly 49,152 uint8 bytes.

- [ ] Use bundled ML Kit object detection in stream mode with multiple objects enabled and classification disabled. Cap output at 5 candidates sorted by descending area; reject areas below 1% or above 90% of frame.
- [ ] If ML Kit yields zero objects during learning, return one centered box `(0.2,0.2,0.8,0.8)` with source `LEARN_CENTER_FALLBACK`; never use that fallback during search.
- [ ] Test normalized/clamped boxes, deterministic sorting, area filters, zero results, and the learning-only fallback through a fake detector.
- [ ] Preprocess with aspect-preserving center crop, bilinear resize to 128x128, RGB order, and direct native-order ByteBuffer. Golden pixel tests assert exact bytes for a 4x4 synthetic colour image.
- [ ] Run `android\gradlew.bat -p android :vision:test`.
- [ ] Commit: `git add android/vision && git commit -m "feat: add candidate discovery and preprocessing"`.

### Task 10: LiteRT Encoder and End-to-End Vision Pipeline

**Files:**
- Create: `EmbeddingEncoder.kt`, `LiteRtEmbeddingEncoder.kt`, `VisionPipeline.kt`, `PipelineMetrics.kt`
- Create: `EmbeddingEncoderTest.kt`, `VisionPipelineTest.kt`, `PipelineStressTest.kt`

**Interfaces:**
- `EmbeddingEncoder.encode(input:ByteBuffer):FloatArray` returns normalized length-576 embedding.
- `VisionPipeline.process(frame, mode):PipelineResult` returns candidates, best observation, vision state, JPEG preview, and metrics.

- [ ] Load the model using memory mapping; verify input/output tensor shape and dtype at startup. Refuse startup with `MODEL_CONTRACT_MISMATCH` when either differs.
- [ ] Dequantize output using tensor scale and zero point, then call domain `l2Normalize`. Serialize encoder calls with one mutex; configure four CPU threads; close interpreter on service shutdown.
- [ ] Pipeline tests use fake candidates and encoder: highest cosine wins; equal scores use larger area; scores below 0.78 produce no observation; one candidate exception does not abort other candidates; all failures produce an error metric and no false target.
- [ ] Stress test sends 10,000 synthetic frames faster than processing. Assert at most one pending frame, processed frame timestamps strictly increase, and allocated-frame close count equals submitted count.
- [ ] Benchmark test after 100 warmups records 500 iterations and writes p50/p95 to test output. It fails on device when p95 exceeds 150 ms but is tagged `devicePerformance` so host CI does not make hardware claims.
- [ ] Run `android\gradlew.bat -p android :vision:test`; then run the tagged benchmark on OnePlus.
- [ ] Commit: `git add android/vision && git commit -m "feat: connect on-device embedding pipeline"`.

### Task 11: Local Dashboard Server and Browser UI

**Files:**
- Create: `android/dashboard/build.gradle.kts`, `DashboardServer.kt`, `Routes.kt`, `StateHub.kt`
- Create: `android/dashboard/src/main/resources/web/index.html`, `app.js`, `style.css`
- Create: `android/dashboard/src/test/kotlin/org/hound/dashboard/RoutesTest.kt`, `StateHubTest.kt`

**Interfaces:**
- Produces `GET /api/health`, `GET /api/state`, `GET /api/preview.jpg`, `GET /api/events`, `WS /api/live`, and POST `/api/learn|hunt|pause|reset` on `0.0.0.0:8080`.

- [ ] Use Ktor CIO. Bind only after service initialization; set JSON content type, `Cache-Control:no-store`, 1 MiB request limit, and a random 128-bit control token printed in the phone activity/ADB log. Require `X-Hound-Token` for POST routes; GET routes stay local-network readable.
- [ ] Route tests assert status and exact error code for missing token, wrong token, invalid state transition, malformed body, unavailable camera, and successful command. Add 100 parallel WebSocket clients; slow clients receive latest state without blocking publishing.
- [ ] UI has controls, mode, confidence, last reliable time, predicted region, preview, decision log, inference-device label `Android phone`, network-loss banner, and visible `STATIONARY / MOTORS DISARMED` badge.
- [ ] Browser JavaScript reconnects WebSocket with capped exponential delays 250, 500, 1000, 2000, 5000 ms; keeps only 200 events; disables Hunt until learning succeeds; never performs image inference.
- [ ] Add HTML/JS smoke test using Ktor test server and a headless browser only if Chromium is locally available; otherwise route/DOM static tests remain mandatory.
- [ ] Run `android\gradlew.bat -p android :dashboard:test :dashboard:lintDebug`.
- [ ] Commit: `git add android/dashboard && git commit -m "feat: add explainable local dashboard"`.

### Task 12: Integrate Learn, Hunt, Occlusion, and Service Lifecycle

**Files:**
- Modify: `android/app/src/main/kotlin/org/hound/app/HoundService.kt`
- Create: `android/app/src/main/kotlin/org/hound/app/HoundRuntime.kt`
- Create: `android/app/src/test/kotlin/org/hound/app/HoundRuntimeTest.kt`

**Interfaces:**
- Produces one composition root connecting camera, pipeline, tracker, mission, server, and lifecycle.

- [ ] On Learn, collect 12 stable center-fallback candidate embeddings across at most 3 seconds. Reject if fewer than 8 or if median pairwise similarity is below 0.70. Generate deterministic horizontal flip, ±8% brightness, and ±5% crop variants before building the prototype.
- [ ] On Hunt, process candidates against the stored prototype and drive the tracker. Publish at most 10 state updates/second and preview JPEG quality 65 at most 5/second.
- [ ] Runtime tests with fake clock/frame source assert the exact sequence `IDLE→LEARNING→SEARCHING→TRACKED→OCCLUDED→TRACKED`; separate test asserts `OCCLUDED→LOST` at 3000 ms.
- [ ] Fault-injection tests throw from camera, detector, encoder, tracker observer, dashboard subscriber, and shutdown. Assert a user-visible error, resource closure, and no service-process crash for recoverable frame failures.
- [ ] Lifecycle soak unit test repeats start/learn/hunt/stop 1,000 times and asserts fake resources created equals resources closed.
- [ ] Run `android\gradlew.bat -p android testDebugUnitTest lintDebug`.
- [ ] Commit: `git add android/app && git commit -m "feat: integrate stationary HOUND runtime"`.

### Task 13: Raspberry Pi Safe Command Agent

**Files:**
- Create: `pi/hound_pi/clock.py`, `motor.py`, `watchdog.py`, `server.py`, `main.py`
- Create: `pi/tests/test_motor.py`, `test_watchdog.py`, `test_server.py`, `test_faults.py`
- Create: `pi/systemd/hound-pi.service`

**Interfaces:**
- Produces `MotorDriver.stop/rotate_left/rotate_right/drive_forward`, `FakeMotorDriver.history`, and TCP newline-JSON listener on port 8765.

- [ ] Implement `MotorDriver` protocol and `GpioZeroMotorDriver` using configurable BCM pins; constructor and context-manager exit must call STOP. Keep `FakeMotorDriver` as production-importable dry-run mode.
- [ ] Watchdog accepts only armed, valid, nonduplicate messages with `abs(now-sentAtMs)<=500`, duration `0..500`, and protocol 1. Every rejection calls STOP before returning an error acknowledgement.
- [ ] Server reads maximum 4096 bytes per line, imposes a 250 ms read timeout and one-client limit, and calls STOP on EOF, timeout, cancellation, parser exception, or handler exception.
- [ ] Unit tests cover every motor intent and all rejection reasons. Fake-clock tests prove STOP at 499/500/501 ms boundaries. Hypothesis sends arbitrary bytes and asserts the server never issues non-STOP motion for invalid input.
- [ ] Concurrency test opens two clients; second is rejected and cannot affect motors. Duplicate-ID test sends 10,000 repeats and proves exactly one accepted action.
- [ ] Configure systemd with `Restart=on-failure`, unprivileged user `hound`, `NoNewPrivileges=true`, and `ExecStop` invoking a CLI `--stop-only` path.
- [ ] Run `python -m pytest pi/tests -q --cov=pi/hound_pi --cov-branch --cov-fail-under=95`, Ruff, and mypy.
- [ ] Commit: `git add pi && git commit -m "feat: add fail-safe Raspberry Pi agent"`.

### Task 14: Phone-to-Pi Transport with Wi-Fi and USB Equivalence

**Files:**
- Create: `android/app/src/main/kotlin/org/hound/app/PiTransport.kt`, `TcpPiTransport.kt`
- Create: `android/app/src/test/kotlin/org/hound/app/TcpPiTransportTest.kt`
- Create: `scripts/usb-dashboard.ps1`, `scripts/usb-pi-tunnel.ps1`

**Interfaces:**
- Produces `PiTransport.send(MotionIntent):CommandAck` and `health:StateFlow<PiHealth>`.

- [x] Implement one TCP client to host/port settings, one in-flight command, 250 ms connect/read timeout, one retry only for missing acknowledgement using the same ID, and mandatory STOP on disconnect/reconnect.
- [x] Test exact wire bytes against protocol fixtures. Use a fake TCP server to simulate fragmented replies, delayed reply, duplicate acknowledgement, wrong ID, malformed JSON, close mid-line, and reconnect.
- [x] Ensure stationary builds never instantiate `TcpPiTransport`; integration test searches fake motor history after all dashboard actions and finds only STOP.
- [x] `usb-dashboard.ps1` runs `adb reverse tcp:8080 tcp:8080`. `usb-pi-tunnel.ps1` documents USB tethering and tests port 8765 using `Test-NetConnection`; it does not change the message format.
- [x] Run `android\gradlew.bat -p android :app:testDebugUnitTest` and all Pi tests.
- [x] Commit: `git add android/app scripts && git commit -m "feat: add interchangeable Pi transport"`.

### Task 15: Recorded-Frame Regression Corpus

**Files:**
- Create: `tools/fixtures/manifest.json`, `tools/fixtures/README.md`, `tools/fixtures/generate_synthetic.py`
- Create: `android/vision/src/test/kotlin/org/hound/vision/RecordedScenarioTest.kt`
- Create: `tools/tests/test_fixture_manifest.py`

**Interfaces:**
- Produces named scenarios `learn`, `distractor`, `occlude_reacquire`, `occlude_lost`, `lighting`, and expected per-frame state JSON.

- [x] Manifest records fixture ID, SHA-256, width, height, frame timestamps, expected candidate boxes, expected mode ranges, consent flag, and `containsPeople:false`.
- [x] Generator creates synthetic shape/object sequences deterministically from seed 20260729 so repository tests do not require private recordings.
- [x] Manifest tests reject missing hashes, timestamp regressions, dimensions other than 320x240, people flags, and expected state sequences that omit terminal state.
- [x] RecordedScenarioTest feeds frames and fake embeddings into the real pipeline/tracker. Assert distractors never reach TRACKED, occlusion reaches OCCLUDED within one frame after 350 ms, reacquisition reaches TRACKED, and long occlusion reaches LOST.
- [x] Add an opt-in local corpus path environment variable `HOUND_PRIVATE_FIXTURES`; its results write only a summary JSON under ignored `build/reports`, never input media.
- [x] Run Python fixture tests and `android\gradlew.bat -p android :vision:test --tests '*RecordedScenarioTest'` twice; assert identical reports.
- [x] Commit: `git add tools/fixtures tools/tests android/vision && git commit -m "test: add deterministic vision regression corpus"`.

### Task 16: End-to-End, Fault, Soak, and Hardware Acceptance

**Files:**
- Create: `scripts/acceptance.ps1`, `scripts/soak.ps1`, `docs/TESTING.md`, `docs/SETUP.md`, `docs/OPERATIONS.md`, `docs/TROUBLESHOOTING.md`
- Modify: `README.md`

**Interfaces:**
- Produces machine-readable `build/reports/acceptance.json` and `build/reports/soak.json`.

- [x] `acceptance.ps1` checks JDK 17, Android SDK 35, ADB device, Python 3.11+, model presence/hash, full host tests, APK build/install, service startup, health endpoint, Learn/Hunt/Pause/Reset API states, and offline operation after disabling Internet while keeping the LAN.
- [x] Add human checkpoints with explicit pass criteria: teach object for 3 seconds; show five distractors; require target score >=0.78 and every distractor <0.78; cover for 0.5–2.5 seconds and require OCCLUDED; uncover and require TRACKED within 1 second; cover >3 seconds and require LOST.
- [x] `soak.ps1` samples `adb shell dumpsys meminfo org.hound` and `/api/health` every 30 seconds for 30 minutes. Fail on crash, missed health for >5 seconds, ending PSS >1.2× post-warmup PSS, p95 >150 ms, or any resource-close counter mismatch.
- [x] Pi hardware gate: with wheels raised, arm movement, send each 200 ms command, verify direction, disconnect network, and require STOP within 500 ms measured by video or logic analyzer. Then repeat malformed and stale commands and verify no motion.
- [x] Write the test matrix mapping every Definition of Done item to command, report, and expected result. Document exact Wi-Fi URL discovery and USB fallback commands.
- [x] Run `scripts\check.ps1`, then `scripts\acceptance.ps1`, then `scripts\soak.ps1`. Store reports but commit only sanitized example report schemas, not hardware-specific results.
- [x] Run `git diff --check`, inspect `git status --short`, and verify no APK/model/private recording/cache is staged.
- [x] Commit: `git add README.md docs scripts && git commit -m "docs: add setup and rigorous acceptance suite"`.

## Final Verification Gate

Run these in order from repository root and stop on the first failure:

```powershell
scripts\check.ps1
android\gradlew.bat -p android clean testDebugUnitTest lintDebug assembleDebug
python -m pytest pi/tests tools/tests -q --hypothesis-show-statistics --cov=pi/hound_pi --cov-branch --cov-fail-under=95
scripts\acceptance.ps1
scripts\soak.ps1
git diff --check
git status --short
```

Expected final state: all automated commands exit 0; hardware reports meet the Definition of Done; `git status --short` is empty except ignored generated assets; the dashboard demonstrates learn, distractor rejection, occlusion, reacquisition, loss, and explicit stationary/disarmed status.

## Requirements Traceability

| Requirement | Implemented by | Verified by |
| --- | --- | --- |
| Exact-object learning | Tasks 3, 6, 9, 10, 12 | Prototype, pipeline, recorded-scenario tests |
| Headless Android camera/inference | Tasks 7–12 | Service lifecycle, camera instrumented, acceptance tests |
| Distractor rejection | Tasks 9, 10, 15 | Recorded distractor regression |
| Occlusion/reacquisition/loss | Tasks 4, 12, 15 | State table, property, recorded regression |
| Explainable browser dashboard | Tasks 5, 11 | Route, WebSocket, DOM, acceptance tests |
| No laptop/cloud inference | Tasks 6, 10, 11 | Packaged model and UI label inspection |
| Pi movement-ready safety | Tasks 2, 5, 13, 14 | Protocol properties, watchdog faults, hardware gate |
| Wi-Fi and USB | Tasks 11, 14, 16 | Same-protocol integration and acceptance tests |
| Rigorous reliability/performance | Tasks 8, 10, 12, 13, 15, 16 | Stress, fault injection, property, soak, HIL tests |
