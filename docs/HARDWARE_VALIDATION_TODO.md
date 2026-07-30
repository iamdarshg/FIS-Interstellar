# HOUND Hardware Validation TODO

This checklist is written for someone who has never programmed before. Work from top to bottom. Tick a box only when you see the exact result described.

If a step says **STOP**, do not continue. Write down what happened and ask the project developer for help. Never guess around electrical or motor problems.

## What “validated” means

HOUND is hardware-validated only when all four columns below are complete. The competition target is the **ambitious version**. The stationary test is a development checkpoint, not the finished project.

| Part | What must work | Current status |
| --- | --- | --- |
| Android phone (OnePlus Nord CE 3 5G) | Camera, model, dashboard, Learn, Search, occlusion, offline use | Primary APK built (`app-oneplus-debug.apk`). All unit/lint checks pass. Ready for phone test. |
| Android phone (Moto G2 Backup) | Legacy 32-bit fallback build, camera, dashboard, offline use | Backup APK built (`app-motog2-debug.apk`, `minSdk 23`). All unit/lint checks pass. |
| Headless operation | Cold start and complete demonstration without touching the phone screen | Service shell & control server implemented. Ready for test. |
| Moving Pi robot | Autonomous search, obstacle-safe movement, approach, return, and emergency stopping within 500 ms | Pi core agent engine & TCP server implemented. Active hardware setup. |
| Ambitious intelligence | Moving-target prediction, multiple remembered targets, natural-language mission, and autonomous return | Tracker predictor & target learner implemented. |

The **stationary phone demonstration** is only the first validation stage. It is not acceptable as the final competition demonstration. The ambitious version requires the Pi, safe movement hardware, and all tests in Section I.

## Equipment checklist

- [ ] Old OnePlus phone, charged to at least 80%.
- [ ] A known-good USB data cable. A charge-only cable will not work.
- [ ] Windows laptop and its charger.
- [ ] A private Wi-Fi router or phone hotspot that works without Internet.
- [ ] Phone stand or tripod with a clear view of the test table.
- [ ] One target object and five similar distractor objects.
- [ ] An opaque box or cloth for the occlusion test.
- [ ] Printed copy of this checklist and a pen.
- [ ] Ambitious-version stage: Pi Zero, correct power supply, microSD card, motor driver, motors, raised test stand, and an adult familiar with the wiring.

## Safety rules

- [ ] Do not test motors with wheels touching the floor. Raise the robot so every wheel spins freely.
- [ ] Do not power motors directly from a Raspberry Pi GPIO pin.
- [ ] Do not connect or move motor wires while power is on.
- [ ] Keep a physical power switch or battery connector within reach.
- [ ] If a motor moves unexpectedly, disconnect motor power immediately.
- [ ] Keep the phone ventilated. Stop if it becomes too hot to hold comfortably, swells, smells unusual, or repeatedly shuts down.

## A. One-time laptop check

Open **PowerShell** in the HOUND repository folder. Copy each grey command, paste it into PowerShell, and press Enter.

- [ ] Run the full computer-only test:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\check.ps1
  ```

  **PASS:** the Android build says `BUILD SUCCESSFUL`, Ruff says `All checks passed`, MyPy says `Success`, and pytest says `30 passed` with coverage at least 95%.

  **STOP:** any red error, `FAILED`, or fewer than 30 passing tests.

- [ ] Check that Android tools can see the phone tool:

  ```powershell
  adb version
  ```

  **PASS:** a version number appears.

  **STOP:** “adb is not recognized.” Install Android Platform Tools or ask the developer to add ADB to the laptop’s PATH.

## B. Prepare the OnePlus while its screen still works

These steps are required before treating the phone as screenless.

- [ ] Connect the phone to Wi-Fi and disable automatic system updates for the demonstration period.
- [ ] Open **Settings → About phone** and tap **Build number** seven times until Developer Options are enabled.
- [ ] Open **Settings → System/Additional settings → Developer options** and enable **USB debugging**.
- [ ] Disable battery optimization for HOUND after it is installed.
- [ ] Set the phone so HOUND is allowed to use the camera and run in the background.
- [ ] Connect the phone to the laptop with the USB cable.
- [ ] Accept the phone’s “Allow USB debugging?” message and select **Always allow from this computer**.
- [ ] Run:

  ```powershell
  adb devices
  ```

  **PASS:** one line ends in `device`.

  **STOP:** it says `unauthorized`, `offline`, or shows no phone. Reconnect the cable, unlock the phone, and accept the permission message.

## C. Create the on-phone AI model

The model file is deliberately not stored in Git. It must be generated before building the APK.

- [ ] Make a folder named `representative-images` outside this repository.
- [ ] Put exactly 100 JPG or PNG photos in it. Use ordinary objects under varied bright/dim lighting and backgrounds. Do not use photos containing people.
- [ ] Count the files in PowerShell, replacing the example path with the real folder:

  ```powershell
  (Get-ChildItem 'C:\path\to\representative-images' -File | Where-Object Extension -Match '^\.(jpg|jpeg|png)$').Count
  ```

  **PASS:** it prints `100`.

- [ ] Generate the model:

  ```powershell
  python tools\model\export_model.py --images-dir 'C:\path\to\representative-images'
  ```

  **PASS:** the last line starts with `SUCCESS: Exported model`.

- [ ] Confirm the file exists:

  ```powershell
  Get-Item android\app\src\main\assets\hound_embedding_v1.tflite
  ```

  **PASS:** a file path and a non-zero size appear.

## D. Build and install the phone app

- [ ] Set Java 17 for this PowerShell window:

  ```powershell
  $env:JAVA_HOME='C:\Users\Darsh Gupta\jdk-17'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  ```

- [ ] Build the APK:

  ```powershell
  android\gradlew.bat -p android clean assembleDebug
  ```

  **PASS:** the final line says `BUILD SUCCESSFUL`.

- [ ] Install it:

  ```powershell
  adb install -r android\app\build\outputs\apk\debug\app-debug.apk
  ```

  **PASS:** the final line says `Success`.

- [ ] Start HOUND:

  ```powershell
  adb shell am start -n org.hound.app/.MainActivity
  ```

- [ ] Grant camera permission on the phone when asked. If the screen cannot be used, run:

  ```powershell
  adb shell pm grant org.hound.app android.permission.CAMERA
  adb shell am force-stop org.hound.app
  adb shell am start -n org.hound.app/.MainActivity
  ```

## E. Mandatory Android wiring gate

The current source checkout may fail this gate because the Android service shell is present but the real camera, model, pipeline, and dashboard composition are not yet connected in `HoundService`.

- [ ] Connect the dashboard over USB:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\usb-dashboard.ps1
  ```

- [ ] Open `http://localhost:8080` in Chrome.

  **PASS:** the HOUND dashboard loads and shows a live camera preview.

  **STOP:** “connection refused,” a permanently blank preview, or a dashboard that never updates. Record this as **Android runtime wiring blocked**. Host unit tests do not override this failure.

## F. Stationary demonstration validation

Only perform this section after Section E passes.

### Camera and controls

- [ ] Put the phone on its stand. Confirm the preview is upright and not mirrored incorrectly.
- [ ] Cover and uncover the lens. Confirm the preview visibly changes within one second.
- [ ] Press **RESET**. Confirm the mode becomes `IDLE`.
- [ ] Press **START** before learning an object. Confirm HOUND refuses to hunt or reports that no target has been learned.

### Learn one object

- [ ] Use a simple object about the size of a bottle or calculator.
- [ ] Hold it centered, 30–60 cm from the camera, with a plain background.
- [ ] Press **LEARN** and hold the object still for three seconds.
- [ ] **PASS:** the mode leaves `LEARNING` and the system reports that a target was learned.
- [ ] Repeat learning five times with five different objects. Record any object that fails.

### Distractor test

- [ ] Place the learned object among five similar objects.
- [ ] Press **START**.
- [ ] **PASS:** the learned object reaches `TRACKED`, its score is at least `0.78`, and no distractor is confirmed as the target.
- [ ] Move the learned object to the left, center, and right. It must remain or return to `TRACKED` in all three positions.
- [ ] Repeat from distances of approximately 30 cm, 60 cm, and 100 cm.

### Occlusion test

- [ ] While the object is tracked, cover it for about one second.
- [ ] **PASS:** mode changes to `OCCLUDED`, not immediately to `LOST`.
- [ ] Uncover it before three seconds have elapsed.
- [ ] **PASS:** mode returns to `TRACKED` within one second.
- [ ] Cover it for more than three seconds.
- [ ] **PASS:** mode changes to `LOST`.
- [ ] Uncover it again.
- [ ] **PASS:** the same target is reacquired without pressing Learn again.

### Offline and headless test

- [ ] Disconnect the Wi-Fi router from the Internet while leaving its local Wi-Fi powered.
- [ ] **PASS:** the dashboard, Learn, Start, Stop, and Reset still work.
- [ ] Turn the phone screen off for ten minutes.
- [ ] **PASS:** the browser preview and state continue updating.
- [ ] Restart the phone, do not touch its screen, reconnect ADB/USB, and start HOUND using the command in Section D.
- [ ] **PASS:** the full stationary demonstration works without touching the phone.

### Thirty-minute reliability test

- [ ] Run:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\soak.ps1 -DurationSeconds 1800 -IntervalSeconds 30
  ```

- [ ] During the 30 minutes, perform Learn/Search/Occlude/Reset at least ten times.
- [ ] **PASS:** no crash, frozen preview, unexpected phone restart, or visible overheating occurs.
- [ ] Open `build\reports\soak.json` and attach it to the signed validation record.

## G. Raspberry Pi network-only validation

This checks communication only. It does not prove motor safety.

- [ ] Install Raspberry Pi OS Lite 64-bit, enable SSH, and connect the Pi to the same private network.
- [ ] From the repository on the Pi, install Python 3.11+ and run `python -m pip install -e .`.
- [ ] Confirm there is a documented command or systemd service that launches `hound_pi.server.Server` on port 8765.

  **CURRENT EXPECTED RESULT:** blocked. This checkout defines the server class but does not provide a Pi CLI entry point or installed systemd service.

- [ ] After a developer supplies the entry point, run this on the laptop, replacing the IP if needed:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\usb-pi-tunnel.ps1 -PiHost 192.168.4.1 -Port 8765
  ```

  **PASS:** it prints `SUCCESS: Connected to Pi`.

## H. Moving-robot validation

Do not begin this section until a developer adds and reviews a real GPIO motor driver, a motor-pin configuration, an arm/disarm control, and a Pi service entry point.

- [ ] Obtain a wiring diagram naming the exact Pi GPIO pins and motor-driver terminals.
- [ ] Have a second person inspect the wiring before power-on.
- [ ] Raise all wheels off the floor and keep motor power disconnected.
- [ ] Boot the Pi. **PASS:** no motor moves.
- [ ] Connect motor power while the system remains disarmed. **PASS:** no motor moves.
- [ ] Test STOP, rotate left, rotate right, and forward for 200 ms each.
- [ ] Disconnect Wi-Fi during movement. **PASS:** every motor stops within 500 ms.
- [ ] Send a stale, malformed, and duplicate command. **PASS:** none causes movement.
- [ ] Restart the Pi and Android phone. **PASS:** motors remain stopped until explicitly armed.
- [ ] Only after every raised-wheel test passes, perform a slow floor test inside a clear one-metre safety area.

## I. Ambitious-version validation

This section is mandatory for the final project. It is currently blocked until the software and movement hardware are implemented.

### Multiple learned targets

- [ ] Teach three different objects named `calculator`, `bottle`, and `toy` without resetting between them.
- [ ] Hide all three among at least five distractors.
- [ ] Ask HOUND to find each object by its saved name.
- [ ] **PASS:** it selects the requested instance, not merely another object of the same general class.
- [ ] Restart the mission but not the app. **PASS:** all three target names remain available.
- [ ] Delete one saved target. **PASS:** the other two remain unchanged and the deleted name can no longer be selected.

### Moving target and trajectory prediction

- [ ] Start with the target stationary and allow HOUND to reach `TRACKED`.
- [ ] Move the target steadily left-to-right across the camera view.
- [ ] **PASS:** the dashboard shows a direction or predicted reappearance region before the target reaches it.
- [ ] Pass the target behind an opaque obstacle for one to two seconds while continuing the same motion.
- [ ] **PASS:** HOUND shows `OCCLUDED`, predicts the correct exit side, and reacquires the same target within one second of reappearance.
- [ ] Repeat right-to-left and at two different speeds. **PASS:** at least five of six trials predict the correct exit side.
- [ ] Move a distractor across the expected path during occlusion. **PASS:** HOUND does not switch identity to the distractor.

### Natural-language mission

The final interface may be rule-based; it does not need a language model. It must accept this exact demonstration command:

```text
Find the calculator, ignore the bottle, and return after locating it.
```

- [ ] Enter the command in the dashboard.
- [ ] **PASS:** the dashboard displays a parsed plan before movement: target `calculator`; ignored target `bottle`; completion action `return`.
- [ ] Try an unknown target name. **PASS:** HOUND refuses to start and clearly says the target has not been learned.
- [ ] Try an unclear command. **PASS:** HOUND asks for correction and does not move.
- [ ] While the robot is moving, press STOP. **PASS:** motors stop within 500 ms and the mission becomes paused or cancelled.

### Autonomous search and return

- [ ] Mark a safe home position and place the robot there.
- [ ] Put the learned calculator somewhere in the approved search area and place the ignored bottle in an easier-to-see position.
- [ ] Start the exact mission command above without steering the robot manually.
- [ ] **PASS:** HOUND searches more than one viewpoint, records searched/unsearched areas, and does not approach the ignored bottle.
- [ ] Move the calculator once while HOUND is searching.
- [ ] **PASS:** HOUND updates its last-seen/predicted location and continues the mission.
- [ ] **PASS:** HOUND approaches the calculator, announces a confirmed match, then returns to the marked home position.
- [ ] Measure final stopping position. **PASS:** it stops within 30 cm of home in at least four of five trials.
- [ ] Place a safe obstacle in its planned path. **PASS:** it stops or routes around it without contact.

### Ambitious end-to-end reliability

- [ ] Run ten complete missions using at least three target objects and two object movements during each mission.
- [ ] **PASS:** at least nine missions find the correct target, never approach an ignored target, and return home safely.
- [ ] Run continuously for 30 minutes. **PASS:** no crash, uncontrolled movement, lost dashboard, or thermal shutdown.
- [ ] Disconnect phone-to-Pi communication during a mission. **PASS:** the base stops within 500 ms and remains stopped.

## Validation sign-off

| Item | Result | Date | Tester | Notes/evidence |
| --- | --- | --- | --- | --- |
| Laptop test suite | PASS / FAIL | | | |
| Model generated | PASS / FAIL | | | SHA-256: |
| APK installed | PASS / FAIL | | | |
| Live dashboard and camera | PASS / FAIL | | | |
| Learn and distractor test | PASS / FAIL | | | |
| Occlusion and reacquisition | PASS / FAIL | | | |
| Offline/headless operation | PASS / FAIL | | | |
| 30-minute soak | PASS / FAIL | | | Report path: |
| Pi network connection | PASS / BLOCKED | | | |
| Raised-wheel motor safety | PASS / BLOCKED | | | |
| Three named learned targets | PASS / BLOCKED | | | |
| Moving-target prediction | PASS / BLOCKED | | | |
| Natural-language mission parsing | PASS / BLOCKED | | | |
| Autonomous search and obstacle safety | PASS / BLOCKED | | | |
| Target found and return home | PASS / BLOCKED | | | |
| Ten-mission ambitious reliability run | PASS / BLOCKED | | | |

Do not label HOUND “competition ready” until every row is marked PASS. A stationary-only or manually driven demonstration is a failed ambitious-version sign-off.
