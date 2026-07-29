# HOUND Final-Day Setup Guide

Use this guide only after **every ambitious-version section** of `HARDWARE_VALIDATION_TODO.md` has passed on the same phone, Pi, robot base, laptop, cables, and network. The stationary mode is a diagnostic fallback, not the intended competition entry.

## Pack the night before

- [ ] OnePlus phone charged to 100%.
- [ ] Laptop charged to 100%.
- [ ] Phone stand or tripod.
- [ ] Laptop charger and phone charger.
- [ ] Two known-good USB data cables.
- [ ] Private Wi-Fi router/hotspot and its charger.
- [ ] Target object, at least five distractors, and an occlusion box/cloth.
- [ ] Two additional learned objects for the multiple-target demonstration.
- [ ] Fully validated Pi Zero robot base, motor driver, battery, sensors, and physical emergency power disconnect.
- [ ] Spare robot battery and approved battery charger.
- [ ] Clearly visible home-position marker and safe arena-boundary markers.
- [ ] USB drive containing the repository, APK, and a copy of both guides.
- [ ] Power strip and extension cable.
- [ ] Printed demonstration script and validation sign-off.

Do not update Android, Android Studio, Python packages, Gradle, the AI model, or repository code after the final successful rehearsal.

## Files to prepare the night before

- [ ] Confirm the model exists:

  ```powershell
  Get-Item android\app\src\main\assets\hound_embedding_v1.tflite
  ```

- [ ] Build a fresh APK:

  ```powershell
  $env:JAVA_HOME='C:\Users\Darsh Gupta\jdk-17'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  android\gradlew.bat -p android clean assembleDebug
  ```

- [ ] Copy `android\app\build\outputs\apk\debug\app-debug.apk` to the USB drive.
- [ ] Run the full test once:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\check.ps1
  ```

- [ ] Confirm the final result shows `BUILD SUCCESSFUL`, `30 passed`, and at least 95% coverage.
- [ ] Put the laptop and phone into airplane mode, turn Wi-Fi back on, and perform one complete rehearsal. This proves the demo does not need Internet.

## Arrival: 45 minutes before the demonstration

### 1. Prepare the table

- [ ] Put the phone stand where the camera sees the entire search area.
- [ ] Remove faces, reflective clutter, and moving backgrounds from the camera view.
- [ ] Put the laptop where the operator can see it without blocking the camera.
- [ ] Keep chargers and cables away from the objects.
- [ ] Mark the tested home position and arena boundary exactly as used during validation.
- [ ] Put the robot on a raised stand before connecting motor power.
- [ ] If movement or ambitious intelligence has not completed hardware validation, do not present the project as complete. Use the recorded-rehearsal fallback and state which gates remain blocked.

### 2. Start the private network

- [ ] Power the router/hotspot.
- [ ] Connect the laptop and OnePlus to the same Wi-Fi.
- [ ] The Internet may remain disconnected.
- [ ] Disable laptop VPNs and guest/client-isolation mode on the router.

### 3. Connect and verify the phone

- [ ] Connect the OnePlus to the laptop with the primary USB data cable.
- [ ] Open PowerShell in the repository folder.
- [ ] Run:

  ```powershell
  adb devices
  ```

  **PASS:** exactly one device is listed and ends in `device`.

- [ ] If HOUND is not installed or has changed since rehearsal, install the prepared APK:

  ```powershell
  adb install -r android\app\build\outputs\apk\debug\app-debug.apk
  ```

- [ ] Grant camera permission and start HOUND:

  ```powershell
  adb shell pm grant org.hound.app android.permission.CAMERA
  adb shell am force-stop org.hound.app
  adb shell am start -n org.hound.app/.MainActivity
  ```

- [ ] Connect the dashboard through USB:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\usb-dashboard.ps1
  ```

- [ ] Open `http://localhost:8080` in Chrome and use full-screen mode.

  **STOP:** do not begin the demonstration unless the dashboard loads and the camera preview visibly responds when you wave a hand in front of the lens.

### 4. Connect and verify the Pi robot

- [ ] Keep the wheels raised.
- [ ] Power the Pi first, leaving motor power disconnected.
- [ ] Start the validated HOUND Pi service using the exact command recorded during hardware sign-off.
- [ ] Verify port 8765 from the laptop:

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\usb-pi-tunnel.ps1 -PiHost 192.168.4.1 -Port 8765
  ```

- [ ] Confirm the dashboard reports the Pi as connected and motors as disarmed.
- [ ] Connect motor power. Confirm no wheel moves.
- [ ] Perform the validated 200 ms raised-wheel direction and STOP test.
- [ ] Place the robot at its home marker, lower it to the floor, and clear the arena.

## Ten-minute preflight rehearsal

- [ ] Press **RESET** and confirm `IDLE`.
- [ ] Hold the planned practice object centered 30–60 cm from the phone.
- [ ] Press **LEARN** and hold still for three seconds.
- [ ] Place it among the distractors and press **START**.
- [ ] Confirm only the learned object reaches `TRACKED` with confidence at least `0.78`.
- [ ] Cover it for one second and confirm `OCCLUDED`.
- [ ] Uncover it and confirm `TRACKED` returns within one second.
- [ ] Press **STOP**, then **RESET**.
- [ ] Turn the phone screen off and repeat Start/Stop from the laptop browser.
- [ ] Confirm all three saved targets appear by name.
- [ ] Enter `Find the calculator, ignore the bottle, and return after locating it.` Confirm the parsed plan is correct before arming.
- [ ] Perform one complete autonomous search, moving-target prediction, object identification, and return-home rehearsal.
- [ ] Confirm emergency STOP halts the base within 500 ms.
- [ ] Close unrelated laptop apps, mute notifications, connect chargers, and prevent laptop sleep.

If any preflight step fails twice, use the fallback demonstration described below. Do not spend the event repeatedly reinstalling or editing code.

## The live demonstration script

### 1. Explain

Say: “This phone is doing all visual inference locally. The laptop only displays its decisions. There is no Internet connection and no retraining. The robot remembers multiple objects, predicts a moving target through occlusion, and turns a simple mission sentence into an autonomous search-and-return plan.”

### 2. Teach multiple targets

- Ask the judge to choose three suitable objects.
- Teach and name them `calculator`, `bottle`, and `toy`, one at a time.
- Hold each object centered with a plain background and keep it still for three seconds.
- Show that all three names are stored before continuing.

### 3. Give the mission

- Enter: `Find the calculator, ignore the bottle, and return after locating it.`
- Point out the parsed target, ignored object, and return action.
- Ask someone to place the objects and distractors while the robot waits at home.
- Arm and start the autonomous mission without manually steering.

### 4. Demonstrate prediction and identity

- Move the calculator steadily behind the occlusion box for one to two seconds.
- Point out `OCCLUDED`, its predicted exit direction, and the remembered identity.
- Move the ignored bottle visibly through the scene and show that HOUND does not switch targets.
- Reveal the calculator and point out reacquisition.

### 5. Complete the autonomous mission

- Let HOUND approach and confirm the calculator.
- Point out the searched/unsearched map and decision explanation.
- Let the robot return to its marked home position without steering.
- Press **STOP** after it reaches home.

### 6. Finish safely

- Press **STOP**.
- Press **RESET** before teaching another object.
- Disarm movement before anyone enters the arena.

## Fast recovery guide

### Dashboard does not open

Run:

```powershell
adb devices
powershell -ExecutionPolicy Bypass -File .\scripts\usb-dashboard.ps1
adb shell am force-stop org.hound.app
adb shell am start -n org.hound.app/.MainActivity
```

Refresh `http://localhost:8080` once. If it still fails, move to the fallback demonstration.

### Phone is unauthorized or absent

- Unlock the phone and accept the USB-debugging prompt.
- Try the spare USB data cable.
- Try another laptop USB port.
- Run `adb kill-server`, then `adb start-server`, then `adb devices`.

### Camera preview is frozen

- Press **STOP**, then **RESET**.
- If still frozen, restart the app using the two ADB commands above.
- Allow the phone to cool if it is hot.

### Wrong object is selected

- Press **STOP** and **RESET**.
- Move to a plain background with even lighting.
- Teach again with the target larger and centered in the frame.
- Use fewer shiny or nearly identical distractors for the next attempt.

### Network fails

Press the physical emergency stop or disconnect motor power first. Keep the USB cable attached and use `http://localhost:8080`. USB dashboard forwarding does not require venue Wi-Fi.

## Fallback demonstration

Prepare a short screen recording during the final successful rehearsal. The recording should show:

1. three named learned targets;
2. the parsed find/ignore/return mission;
3. autonomous search and distractor rejection;
4. moving-target direction prediction;
5. `OCCLUDED` and reacquisition without identity switching;
6. correct-object approach and autonomous return home;
7. emergency STOP;
8. the phone in airplane mode with Wi-Fi used only for the local network.

Clearly label it “recorded hardware rehearsal.” Do not present a recording as a live run. If the live camera/dashboard fails, show the recording and then explain the architecture using the stationary phone, Pi, and dashboard roles.

## End-of-day shutdown

- [ ] Press **STOP**.
- [ ] Disconnect motor power first if a validated moving base was used.
- [ ] Stop HOUND:

  ```powershell
  adb shell am force-stop org.hound.app
  adb reverse --remove tcp:8080
  ```

- [ ] Disconnect the phone and chargers.
- [ ] Save logs, photos of the setup, and any failed-run notes before changing code.
